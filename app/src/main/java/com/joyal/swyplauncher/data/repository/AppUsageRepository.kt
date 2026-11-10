package com.joyal.swyplauncher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joyal.swyplauncher.domain.model.AppUsageInfo
import com.joyal.swyplauncher.util.UsageStatsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appUsageDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_usage")

@Singleton
class AppUsageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.appUsageDataStore

    // Cache preferences to reduce DataStore reads
    private var cachedPreferences: Preferences? = null
    private var lastCacheTime: Long = 0
    private val cacheMutex = Mutex()
    private val CACHE_VALIDITY_MS = 5000L // 5 seconds

    private suspend fun getCachedPreferences(): Preferences? {
        return cacheMutex.withLock {
            val now = System.currentTimeMillis()
            if (cachedPreferences != null && (now - lastCacheTime) < CACHE_VALIDITY_MS) {
                cachedPreferences
            } else {
                // Refresh cache
                val prefs = dataStore.data.firstOrNull()
                if (prefs != null) {
                    cachedPreferences = prefs
                    lastCacheTime = now
                }
                prefs
            }
        }
    }

    // Last computed usage map. Building it is a UsageStatsManager binder query over 30 days of
    // data (or a full pass over the DataStore), far too slow to repeat for every keystroke of a
    // search, so it is reused for a short while. usageMapTime = 0 means "recompute on next use".
    @Volatile
    private var cachedUsageMap: Map<String, Int>? = null
    @Volatile
    private var usageMapTime: Long = 0
    private val usageMapMutex = Mutex()
    private val USAGE_MAP_VALIDITY_MS = 30_000L // 30 seconds

    private suspend fun invalidateCache() {
        cacheMutex.withLock {
            cachedPreferences = null
            lastCacheTime = 0
        }
        // Manual counts changed; the previous map stays available via peekUsageMap()
        usageMapTime = 0
    }

    suspend fun recordAppUsage(identifier: String) {
        // Use a safe key by replacing / with __
        val safeIdentifier = identifier.replace("/", "__")
        val countKey = intPreferencesKey("${safeIdentifier}_count")
        val timestampKey = longPreferencesKey("${safeIdentifier}_timestamp")

        dataStore.edit { preferences ->
            val currentCount = preferences[countKey] ?: 0
            preferences[countKey] = currentCount + 1
            preferences[timestampKey] = System.currentTimeMillis()
        }
        // Invalidate cache after write
        invalidateCache()
    }

    suspend fun getAppUsageInfo(identifier: String): AppUsageInfo {
        val safeIdentifier = identifier.replace("/", "__")
        val preferences = getCachedPreferences()
        return AppUsageInfo(
            packageName = identifier,
            usageCount = preferences?.get(intPreferencesKey("${safeIdentifier}_count")) ?: 0,
            lastUsedTimestamp = preferences?.get(longPreferencesKey("${safeIdentifier}_timestamp")) ?: 0L
        )
    }

    private suspend fun getAllAppUsageInfo(): List<AppUsageInfo> {
        val preferences = getCachedPreferences() ?: return emptyList()
        return preferences.asMap()
            .filter { it.key.name.endsWith("_count") }
            .map { (key, value) ->
                val safeIdentifier = key.name.removeSuffix("_count")
                // Convert back from safe identifier to original identifier
                val identifier = safeIdentifier.replace("__", "/")
                AppUsageInfo(
                    packageName = identifier,
                    usageCount = value as? Int ?: 0,
                    lastUsedTimestamp = preferences[longPreferencesKey("${safeIdentifier}_timestamp")] ?: 0L
                )
            }
    }

    suspend fun getRecentlyUsedApps(limit: Int = 2): List<String> {
        return getAllAppUsageInfo()
            .filter { it.lastUsedTimestamp > 0 }
            .sortedByDescending { it.lastUsedTimestamp }
            .take(limit)
            .map { it.packageName }
    }

    suspend fun getMostUsedApps(limit: Int = 2): List<String> {
        return getAllAppUsageInfo()
            .filter { it.usageCount > 0 }
            .sortedByDescending { it.usageCount }
            .take(limit)
            .map { it.packageName }
    }

    suspend fun hasBeenOpened(identifier: String): Boolean {
        val safeIdentifier = identifier.replace("/", "__")
        val preferences = getCachedPreferences()
        val count = preferences?.get(intPreferencesKey("${safeIdentifier}_count")) ?: 0
        return count > 0
    }

    suspend fun getUsageMap(): Map<String, Int> {
        freshUsageMap()?.let { return it }
        return usageMapMutex.withLock {
            freshUsageMap() ?: withContext(Dispatchers.IO) { computeUsageMap() }.also {
                cachedUsageMap = it
                usageMapTime = System.currentTimeMillis()
            }
        }
    }

    /** Loads the manual-tracking store and the usage map into memory ahead of first use. */
    suspend fun prewarm() {
        getCachedPreferences()
        getUsageMap()
    }

    /**
     * The most recently computed usage map regardless of its age, or null if none was computed
     * in this process yet. For rendering something immediately; follow up with [getUsageMap].
     */
    fun peekUsageMap(): Map<String, Int>? = cachedUsageMap

    private fun freshUsageMap(): Map<String, Int>? =
        cachedUsageMap?.takeIf { System.currentTimeMillis() - usageMapTime < USAGE_MAP_VALIDITY_MS }

    private suspend fun computeUsageMap(): Map<String, Int> {
        // Try to get system usage stats first
        if (UsageStatsHelper.hasUsageStatsPermission(context)) {
            val systemUsageStats = UsageStatsHelper.getAppUsageStats(context)
            // Convert time in foreground to a score (divide by 1000 to get seconds)
            return systemUsageStats.mapValues { (it.value / 1000).toInt() }
        }
        
        // Fall back to manual tracking if permission not granted
        return getAllAppUsageInfo().associate { it.packageName to it.usageCount }
    }
    
    fun hasUsageStatsPermission(): Boolean {
        return UsageStatsHelper.hasUsageStatsPermission(context)
    }
    
    fun openUsageAccessSettings() {
        UsageStatsHelper.openUsageAccessSettings(context)
    }
}
