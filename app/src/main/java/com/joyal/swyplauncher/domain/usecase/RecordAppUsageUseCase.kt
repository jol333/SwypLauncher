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

    suspend fun getUsageMap(): Map<String, Int> {
        return appUsageRepository.getUsageMap()
    }

    /** Warms the usage data so the first app-list build doesn't wait on it. */
    suspend fun prewarm() = appUsageRepository.prewarm()

    /** Last known usage map without waiting for a refresh; null if none was loaded yet. */
    fun peekUsageMap(): Map<String, Int>? = appUsageRepository.peekUsageMap()
    
    fun hasUsageStatsPermission(): Boolean {
        return appUsageRepository.hasUsageStatsPermission()
    }
    
    fun openUsageAccessSettings() {
        appUsageRepository.openUsageAccessSettings()
    }
}
