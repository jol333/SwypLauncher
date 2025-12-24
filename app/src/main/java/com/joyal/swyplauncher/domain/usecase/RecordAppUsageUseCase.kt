package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.data.repository.AppUsageRepository
import javax.inject.Inject

class RecordAppUsageUseCase @Inject constructor(
    private val appUsageRepository: AppUsageRepository
) {
    suspend operator fun invoke(packageName: String, activityName: String? = null) {
        val identifier = if (activityName != null) "$packageName/$activityName" else packageName
        appUsageRepository.recordAppUsage(identifier)
    }
    
    suspend fun hasBeenOpened(packageName: String, activityName: String? = null): Boolean {
        val identifier = if (activityName != null) "$packageName/$activityName" else packageName
        return appUsageRepository.hasBeenOpened(identifier)
    }
    
    // Convenience method that takes AppInfo
    suspend operator fun invoke(app: com.joyal.swyplauncher.domain.model.AppInfo) {
        appUsageRepository.recordAppUsage(app.getIdentifier())
    }
    
    suspend fun getUsageMap(): Map<String, Int> {
        return appUsageRepository.getUsageMap()
    }
    
    fun hasUsageStatsPermission(): Boolean {
        return appUsageRepository.hasUsageStatsPermission()
    }
    
    fun openUsageAccessSettings() {
        appUsageRepository.openUsageAccessSettings()
    }
}
