package com.joyal.swyplauncher.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import com.joyal.swyplauncher.domain.model.AppChangeEvent
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.util.AppIconDiskCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val iconPackRepository: com.joyal.swyplauncher.domain.repository.IconPackRepository
) : com.joyal.swyplauncher.domain.repository.AppRepository {
    private val packageManager: PackageManager = context.packageManager

    // Installed apps are cached in memory and treated as fresh for 5 minutes to reduce expensive
    // PackageManager queries. A package change only marks the list stale (lastCacheTime = 0)
    // rather than dropping it, so the UI keeps showing it while the refresh runs.
    @Volatile
    private var cachedApps: List<AppInfo>? = null
    @Volatile
    private var lastCacheTime: Long = 0
    private val cacheMutex = Mutex()
    private val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes

    // Guards writes to the two fields above; bumped generation = "a scan that started earlier
    // may predate a package change, don't stamp its result as fresh".
    private val cacheLock = Any()
    private val cacheGeneration = AtomicInteger()

    // The last scan is also persisted so a new process can show the list straight away while
    // its own scan runs (see restoreCachedApps).
    private val snapshotFile by lazy { File(context.cacheDir, "app_list_snapshot.bin") }
    private val snapshotMutex = Mutex()
    private var snapshotRestoreAttempted = false
    @Volatile
    private var snapshotUsable = true
    private var iconCachePruned = false

    // Process-lifetime scope for the debounced refresh after package changes.
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    override fun invalidateCache() {
        synchronized(cacheLock) {
            cacheGeneration.incrementAndGet()
            cachedApps = null
            lastCacheTime = 0
        }
        // The caller wants nothing from before this point shown again (e.g. language change).
        snapshotUsable = false
    }

    override fun getCachedInstalledApps(): List<AppInfo> = cachedApps ?: emptyList()

    override suspend fun restoreCachedApps(): List<AppInfo> {
        cachedApps?.let { return it }
        snapshotMutex.withLock {
            if (!snapshotRestoreAttempted) {
                snapshotRestoreAttempted = true
                val generation = cacheGeneration.get()
                val snapshot = withContext(Dispatchers.IO) { readSnapshot() }
                if (snapshot != null) {
                    synchronized(cacheLock) {
                        // Stale by definition (lastCacheTime stays 0): only shown until the
                        // scan finishes. Never replaces a scan result or resurrects an
                        // invalidated list.
                        if (cachedApps == null && snapshotUsable && cacheGeneration.get() == generation) {
                            cachedApps = snapshot
                        }
                    }
                }
            }
        }
        return cachedApps ?: emptyList()
    }

    override suspend fun getInstalledApps(): List<AppInfo> = cacheMutex.withLock {
        val now = System.currentTimeMillis()

        // Return cached apps if still valid
        cachedApps?.let { cached ->
            if ((now - lastCacheTime) < CACHE_VALIDITY_MS) return@withLock cached
        }

        val generation = cacheGeneration.get()
        // Binder calls and APK resource loads: always off the caller's thread
        val scanned = withContext(Dispatchers.IO) { queryInstalledApps() }

        val (apps, changed) = synchronized(cacheLock) {
            // An unchanged scan keeps the previous list instance so downstream state stays equal
            val previous = cachedApps
            val result = if (previous == scanned) previous else scanned
            if (cacheGeneration.get() == generation) {
                cachedApps = result
                lastCacheTime = System.currentTimeMillis()
                result to (previous != scanned)
            } else {
                // A package changed (or the cache was invalidated) mid-scan: hand the result
                // back but leave the cache as it is so the next call scans again.
                result to false
            }
        }

        if (changed || !iconCachePruned) {
            iconCachePruned = true
            withContext(Dispatchers.IO) {
                if (changed) writeSnapshot(apps)
                AppIconDiskCache.prune(context, apps, iconPackRepository::iconStyleToken)
            }
        }

        return@withLock apps
    }

    private suspend fun queryInstalledApps(): List<AppInfo> = coroutineScope {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Request higher density icons for better quality on high-DPI screens
        val iconDensity = 480 // xxxhdpi density for high quality

        val resolved = packageManager.queryIntentActivities(intent, 0)

        // Resolving a label opens that app's resources, which dominates the scan, so split the
        // work over a few IO threads. Chunks are re-joined in order: same result as one pass.
        val chunkSize = ((resolved.size + LOAD_PARALLELISM - 1) / LOAD_PARALLELISM).coerceAtLeast(1)
        resolved.chunked(chunkSize)
            .map { chunk -> async { chunk.mapNotNull { toAppInfo(it, iconDensity) } } }
            .awaitAll()
            .flatten()
            // Some devices report the same launcher activity twice; the identifier is the app
            // grids' item key, so a repeat would crash the grid (duplicate lazy-layout key).
            .distinctBy { it.getIdentifier() }
            .sortedBy { it.sortKey }
    }

    private fun toAppInfo(resolveInfo: ResolveInfo, iconDensity: Int): AppInfo? {
        return try {
            val packageName = resolveInfo.activityInfo.packageName
            val activityName = resolveInfo.activityInfo.name
            val label = resolveInfo.loadLabel(packageManager).toString()

            // Get install / last-update time
            val packageInfo = try {
                packageManager.getPackageInfo(packageName, 0)
            } catch (e: Exception) {
                null
            }

            val firstLetter = label.firstOrNull()?.uppercaseChar() ?: '#'

            // Get app category (the resolved activity already carries its ApplicationInfo)
            val category = when (resolveInfo.activityInfo.applicationInfo?.category) {
                android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Games"
                android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
                android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Image"
                android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "News"
                android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Maps"
                android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                else -> "Other"
            }

            AppInfo(
                packageName = packageName,
                label = label,
                firstLetter = firstLetter,
                iconDensity = iconDensity,
                installTime = packageInfo?.firstInstallTime ?: 0L,
                activityName = activityName,
                category = category,
                lastUpdateTime = packageInfo?.lastUpdateTime ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }

    // Labels are resolved in the app's current locale, so a snapshot is only valid for that locale.
    private fun currentLocaleTags(): String = context.resources.configuration.locales.toLanguageTags()

    private fun writeSnapshot(apps: List<AppInfo>) {
        val file = AtomicFile(snapshotFile)
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            val out = DataOutputStream(BufferedOutputStream(stream))
            out.writeInt(SNAPSHOT_VERSION)
            out.writeUTF(currentLocaleTags())
            out.writeInt(apps.size)
            apps.forEach { app ->
                out.writeUTF(app.packageName)
                out.writeUTF(app.label)
                out.writeChar(app.firstLetter.code)
                out.writeInt(app.iconDensity)
                out.writeLong(app.installTime)
                out.writeUTF(app.activityName ?: "")
                out.writeUTF(app.category)
                out.writeLong(app.lastUpdateTime)
            }
            out.flush()
            file.finishWrite(stream)
        } catch (e: Exception) {
            // Best-effort: without a snapshot the next cold start just waits for its scan.
            stream?.let { file.failWrite(it) }
        }
    }

    private fun readSnapshot(): List<AppInfo>? {
        return try {
            if (!snapshotFile.exists()) return null
            DataInputStream(BufferedInputStream(AtomicFile(snapshotFile).openRead())).use { input ->
                if (input.readInt() != SNAPSHOT_VERSION) return null
                if (input.readUTF() != currentLocaleTags()) return null
                val count = input.readInt()
                if (count !in 0..MAX_SNAPSHOT_APPS) return null
                List(count) {
                    val packageName = input.readUTF()
                    val label = input.readUTF()
                    val firstLetter = input.readChar()
                    val iconDensity = input.readInt()
                    val installTime = input.readLong()
                    val activityName = input.readUTF().ifEmpty { null }
                    val category = input.readUTF()
                    val lastUpdateTime = input.readLong()
                    AppInfo(
                        packageName = packageName,
                        label = label,
                        firstLetter = firstLetter,
                        iconDensity = iconDensity,
                        installTime = installTime,
                        activityName = activityName,
                        category = category,
                        lastUpdateTime = lastUpdateTime
                    )
                }.distinctBy { it.getIdentifier() } // Written by a build that didn't dedupe the scan
            }
        } catch (e: Exception) {
            // Truncated/corrupt/outdated file: behave as if there were no snapshot.
            null
        }
    }

    /**
     * A package was added, removed or changed. Keeps the current list for display but marks it
     * stale, and refreshes it shortly after so the next assistant open already has the new list
     * (package broadcasts arrive in bursts, hence the debounce). Main thread only.
     */
    private fun onPackagesChanged(removedPackage: String? = null) {
        synchronized(cacheLock) {
            cacheGeneration.incrementAndGet()
            lastCacheTime = 0
            // An uninstalled app must not linger in the stale list
            if (removedPackage != null) {
                cachedApps = cachedApps?.filterNot { it.packageName == removedPackage }
            }
        }
        refreshJob?.cancel()
        refreshJob = refreshScope.launch {
            delay(REFRESH_DEBOUNCE_MS)
            try {
                getInstalledApps()
            } catch (e: Exception) {
                // Best-effort; the next assistant open scans again.
            }
        }
    }

    private val appChangesFlow = kotlinx.coroutines.flow.MutableSharedFlow<AppChangeEvent>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_ADDED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName != null) {
                            onPackagesChanged() // Refresh the list when apps change
                            appChangesFlow.tryEmit(AppChangeEvent.AppInstalled(packageName))
                        }
                    }
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName != null) {
                            // EXTRA_REPLACING: the removal half of an update, the app stays
                            val uninstalled = !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                            onPackagesChanged(removedPackage = packageName.takeIf { uninstalled })
                            appChangesFlow.tryEmit(AppChangeEvent.AppUninstalled(packageName))
                        }
                    }
                    Intent.ACTION_PACKAGE_REPLACED,
                    Intent.ACTION_PACKAGE_CHANGED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName != null) {
                            onPackagesChanged() // Refresh the list when apps update/change
                            appChangesFlow.tryEmit(AppChangeEvent.AppInstalled(packageName))
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        // Registered for the whole process lifetime (this is an app-scoped @Singleton) so the
        // installed-apps cache stays fresh across package changes. NOT_EXPORTED: these are
        // protected system broadcasts, so no other app can (or needs to) reach this receiver.
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun observeAppChanges(): Flow<AppChangeEvent> = appChangesFlow

    override suspend fun launchApp(packageName: String, activityName: String?): Result<Unit> {
        return try {
            val launchIntent = if (activityName != null) {
                // Launch specific activity
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                // Launch default activity
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Launch intent not found for package: $packageName"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val LOAD_PARALLELISM = 4
        const val REFRESH_DEBOUNCE_MS = 2_000L
        const val SNAPSHOT_VERSION = 1
        const val MAX_SNAPSHOT_APPS = 10_000 // sanity bound when reading the snapshot
    }
}
