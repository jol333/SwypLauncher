package com.joyal.swyplauncher.domain.repository

import com.joyal.swyplauncher.domain.model.AppChangeEvent
import com.joyal.swyplauncher.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    suspend fun getInstalledApps(): List<AppInfo>
    fun getCachedInstalledApps(): List<AppInfo>

    /**
     * Like [getCachedInstalledApps], but when nothing is in memory yet (fresh process) it first
     * loads the list persisted by the previous scan. May be out of date: use it to show something
     * immediately, then follow up with [getInstalledApps].
     */
    suspend fun restoreCachedApps(): List<AppInfo>
    fun observeAppChanges(): Flow<AppChangeEvent>
    suspend fun launchApp(packageName: String, activityName: String? = null): Result<Unit>
    fun invalidateCache()
}