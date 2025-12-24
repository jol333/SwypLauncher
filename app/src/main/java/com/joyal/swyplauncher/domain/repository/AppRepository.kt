package com.joyal.swyplauncher.domain.repository

import com.joyal.swyplauncher.domain.model.AppChangeEvent
import com.joyal.swyplauncher.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    suspend fun getInstalledApps(): List<AppInfo>
    fun observeAppChanges(): Flow<AppChangeEvent>
    suspend fun launchApp(packageName: String, activityName: String? = null): Result<Unit>
}
