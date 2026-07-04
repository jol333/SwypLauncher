package com.joyal.swyplauncher.data.repository

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import android.os.SystemClock
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.joyal.swyplauncher.domain.model.ShortcutIcon
import com.joyal.swyplauncher.domain.model.ShortcutSearchItem
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.domain.repository.ShortcutSearchRepository
import com.joyal.swyplauncher.util.isBitmapDark
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutSearchRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) : ShortcutSearchRepository {

    private data class IndexEntry(
        val item: ShortcutSearchItem,
        val matchText: String,      // lowercase short label
        val altMatchText: String?   // lowercase long label, when it adds anything
    )

    private val indexMutex = Mutex()

    @Volatile
    private var index: List<IndexEntry>? = null

    @Volatile
    private var indexLoadedAt: Long = 0L

    // Bitmaps are ~144x144 ARGB (80KB); 48 entries ≈ 4MB worst case.
    private val iconCache = LruCache<String, ShortcutIcon>(48)

    override suspend fun prewarm() {
        if (!preferencesRepository.isShortcutSearchEnabled()) return
        // Each assistant session refreshes a stale index up front; searches themselves
        // never block on freshness, only on the very first load.
        ensureIndex(maxAgeMs = PREWARM_MAX_AGE_MS)
    }

    override suspend fun search(query: String, prefixMatch: Boolean): List<ShortcutSearchItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!preferencesRepository.isShortcutSearchEnabled()) return emptyList()
        val entries = ensureIndex(maxAgeMs = Long.MAX_VALUE) ?: return emptyList()
        if (entries.isEmpty()) return emptyList()

        val q = trimmed.lowercase()
        val hiddenApps = preferencesRepository.getHiddenApps()
        val hiddenShortcuts = preferencesRepository.getHiddenShortcuts()
        // Shortcut ids mapped to this exact search word via the long-press "set a shortcut"
        val aliasedIds = preferencesRepository.getShortcutSearchAliases()
            .asSequence()
            .filter { it.key.equals(trimmed, ignoreCase = true) }
            .flatMap { it.value.asSequence() }
            .toSet()

        fun matches(text: String?) =
            text != null && if (prefixMatch) text.startsWith(q) else text.contains(q)

        return entries.asSequence()
            .filter { entry ->
                matches(entry.matchText) || matches(entry.altMatchText) ||
                    entry.item.identifier() in aliasedIds
            }
            .filter { entry -> entry.item.identifier() !in hiddenShortcuts }
            .filter { entry -> !isPackageHidden(entry.item.packageName, hiddenApps) }
            .map { it.item }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override suspend fun getIcon(item: ShortcutSearchItem): ShortcutIcon? {
        val cacheKey = "${item.packageName}/${item.id}"
        iconCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { loadIcon(item) }.getOrNull()
                ?.also { iconCache.put(cacheKey, it) }
        }
    }

    override fun launchShortcut(item: ShortcutSearchItem): Boolean {
        return try {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            launcherApps.startShortcut(
                item.packageName, item.id, null, null, Process.myUserHandle()
            )
            true
        } catch (e: Exception) {
            // Stale shortcut, package removed mid-tap, or role revoked — never crash.
            false
        }
    }

    override fun invalidate() {
        index = null
        iconCache.evictAll()
    }

    /**
     * Returns the shortcut index, loading it (once, under the mutex) when missing or older
     * than [maxAgeMs]. Returns null — and switches the preference off — when the assistant
     * role is no longer held, per the product rule that removing the role disables the feature.
     */
    private suspend fun ensureIndex(maxAgeMs: Long): List<IndexEntry>? {
        cachedIndex(maxAgeMs)?.let { return it }
        return indexMutex.withLock {
            cachedIndex(maxAgeMs)?.let { return it }
            if (!isAssistantRoleHeld()) {
                invalidate()
                preferencesRepository.setShortcutSearchEnabled(false)
                return null
            }
            withContext(Dispatchers.IO) { loadIndex() }.also {
                index = it
                indexLoadedAt = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun cachedIndex(maxAgeMs: Long): List<IndexEntry>? =
        index?.takeIf { SystemClock.elapsedRealtime() - indexLoadedAt <= maxAgeMs }

    private fun isAssistantRoleHeld(): Boolean = try {
        context.getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
    } catch (e: Exception) {
        false
    }

    /** One query across all packages (dynamic + manifest) — a single binder call. */
    private fun loadIndex(): List<IndexEntry> {
        return try {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
                ?: return emptyList()
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
            }
            val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                ?: return emptyList()

            val pm = context.packageManager
            val appLabelCache = HashMap<String, String>()
            shortcuts.asSequence()
                .filter { it.isEnabled && it.`package` != context.packageName }
                .distinctBy { "${it.`package`}/${it.id}" }
                .mapNotNull { info ->
                    val label = (info.shortLabel ?: info.longLabel)?.toString()?.trim()
                    if (label.isNullOrEmpty()) return@mapNotNull null
                    val appLabel = appLabelCache.getOrPut(info.`package`) {
                        runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(info.`package`, 0)).toString()
                        }.getOrDefault(info.`package`)
                    }
                    val matchText = label.lowercase()
                    val altMatchText = info.longLabel?.toString()?.trim()?.lowercase()
                        ?.takeIf { it.isNotEmpty() && it != matchText }
                    IndexEntry(
                        item = ShortcutSearchItem(
                            id = info.id,
                            packageName = info.`package`,
                            label = label,
                            appLabel = appLabel
                        ),
                        matchText = matchText,
                        altMatchText = altMatchText
                    )
                }
                .toList()
        } catch (e: Exception) {
            // SecurityException (access revoked) or any binder hiccup: cache the empty
            // result so we don't hammer a failing call; the TTL retries next session.
            emptyList()
        }
    }

    private fun loadIcon(item: ShortcutSearchItem): ShortcutIcon? {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
            setPackage(item.packageName)
            setShortcutIds(listOf(item.id))
        }
        val info = launcherApps.getShortcuts(query, Process.myUserHandle())
            ?.firstOrNull() ?: return null
        val drawable = launcherApps.getShortcutIconDrawable(
            info, context.resources.displayMetrics.densityDpi
        ) ?: return null
        // Explicit config forces a fresh software bitmap (hardware bitmaps can't be sampled).
        val bitmap = drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        return ShortcutIcon(bitmap = bitmap, isDark = isBitmapDark(bitmap))
    }

    companion object {
        private const val PREWARM_MAX_AGE_MS = 30_000L
        private const val ICON_SIZE_PX = 144
    }
}

/** True when the user hid this app (any of its activities) from the launcher. */
private fun isPackageHidden(packageName: String, hiddenApps: Set<String>): Boolean =
    hiddenApps.any { it == packageName || it.startsWith("$packageName/") }
