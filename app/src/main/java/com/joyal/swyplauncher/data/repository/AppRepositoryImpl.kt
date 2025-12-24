package com.joyal.swyplauncher.data.repository

import com.joyal.swyplauncher.data.source.AppDataSource
import com.joyal.swyplauncher.domain.model.AppChangeEvent
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl @Inject constructor(
    private val appDataSource: AppDataSource
) : AppRepository {
    override suspend fun getInstalledApps(): List<AppInfo> = appDataSource.getInstalledApps()
    override fun observeAppChanges(): Flow<AppChangeEvent> = appDataSource.observeAppChanges()
    override suspend fun launchApp(packageName: String, activityName: String?): Result<Unit> = 
        appDataSource.launchApp(packageName, activityName)
}
