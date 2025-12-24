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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appUsageDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_usage")

@Singleton
class AppUsageRepository @Inject constructor(
    @ApplicationContext private val context: Context
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

    private suspend fun invalidateCache() {
        cacheMutex.withLock {
            cachedPreferences = null
            lastCacheTime = 0
        }
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
