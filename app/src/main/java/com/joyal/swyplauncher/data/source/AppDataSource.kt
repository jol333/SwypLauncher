package com.joyal.swyplauncher.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.joyal.swyplauncher.domain.model.AppChangeEvent
import com.joyal.swyplauncher.domain.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : com.joyal.swyplauncher.domain.repository.AppRepository {
    private val packageManager: PackageManager = context.packageManager

    // Cache installed apps for 5 seconds to reduce expensive PackageManager queries
    private var cachedApps: List<AppInfo>? = null
    private var lastCacheTime: Long = 0
    private val cacheMutex = Mutex()
    private val CACHE_VALIDITY_MS = 5000L // 5 seconds

    fun invalidateCache() {
        cachedApps = null
        lastCacheTime = 0
    }

    override suspend fun getInstalledApps(): List<AppInfo> = cacheMutex.withLock {
        val now = System.currentTimeMillis()

        // Return cached apps if still valid
        if (cachedApps != null && (now - lastCacheTime) < CACHE_VALIDITY_MS) {
            return@withLock cachedApps!!
        }

        // Load apps from PackageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Request higher density icons for better quality on high-DPI screens
        val iconDensity = 480 // xxxhdpi density for high quality

        val apps = packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                try {
                    val packageName = resolveInfo.activityInfo.packageName
                    val activityName = resolveInfo.activityInfo.name
                    val label = resolveInfo.loadLabel(packageManager).toString()

                    // Load activity-specific icon (important for apps with multiple launcher activities)
                    // val icon = resolveInfo.loadIcon(packageManager) // Removed: Loading asynchronously with Coil

                    // Get install time
                    val installTime = try {
                        val packageInfo = packageManager.getPackageInfo(packageName, 0)
                        packageInfo.firstInstallTime
                    } catch (e: Exception) {
                        0L
                    }

                    val firstLetter = label.firstOrNull()?.uppercaseChar() ?: '#'

                    // Get app category
                    val category = try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        when (appInfo.category) {
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
                    } catch (e: Exception) {
                        "Other"
                    }

                    AppInfo(
                        packageName = packageName,
                        label = label,
                        // icon = icon, // Removed
                        firstLetter = firstLetter,
                        iconDensity = iconDensity,
                        installTime = installTime,
                        activityName = activityName,
                        category = category
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }

        // Update cache
        cachedApps = apps
        lastCacheTime = now

        return@withLock apps
    }

    override fun observeAppChanges(): Flow<AppChangeEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_ADDED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName != null) {
                            invalidateCache() // Invalidate cache when apps change
                            trySend(AppChangeEvent.AppInstalled(packageName))
                        }
                    }
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName != null) {
                            invalidateCache() // Invalidate cache when apps change
                            trySend(AppChangeEvent.AppUninstalled(packageName))
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        context.registerReceiver(receiver, filter)

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Receiver already unregistered
            }
        }
    }

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
}
