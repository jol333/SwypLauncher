package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.data.repository.AppUsageRepository
import com.joyal.swyplauncher.domain.model.AppInfo
import javax.inject.Inject

class GetSmartAppListUseCase @Inject constructor(
    private val appUsageRepository: AppUsageRepository
) {
    /**
     * Returns a smart list of apps based on the limit (apps per row):
     * - 1 recently installed app (within 24 hours, not yet opened) - appears first
     * - Remaining slots filled with recently opened and most used apps
     * - Uses system usage stats when permission granted, falls back to manual tracking
     * - No duplicates
     * - If not enough recent/most used, fill with alphabetically sorted apps
     */
    suspend operator fun invoke(allApps: List<AppInfo>, limit: Int = 4): List<AppInfo> {
        if (allApps.isEmpty()) return emptyList()
        
        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000)
        
        // Find most recently installed app within 24 hours that hasn't been opened
        val recentlyInstalledApp = allApps
            .filter { it.installTime > twentyFourHoursAgo }
            .filter { !appUsageRepository.hasBeenOpened(it.getIdentifier()) }
            .maxByOrNull { it.installTime }
        
        // Get recently used apps (by last opened timestamp from manual tracking)
        val recentIdentifiers = appUsageRepository.getRecentlyUsedApps(limit = 2)
        
        // Get usage map (uses system stats if permission granted, falls back to manual tracking)
        val usageMap = appUsageRepository.getUsageMap()
        
        // Sort apps by usage (system stats or manual tracking)
        val mostUsedApps = allApps
            .filter { usageMap[it.getIdentifier()] ?: usageMap[it.packageName] ?: 0 > 0 }
            .sortedByDescending { usageMap[it.getIdentifier()] ?: usageMap[it.packageName] ?: 0 }
        
        val smartList = mutableListOf<AppInfo>()
        val addedIdentifiers = mutableSetOf<String>()
        
        // Add recently installed app first (if exists)
        if (recentlyInstalledApp != null) {
            smartList.add(recentlyInstalledApp)
            addedIdentifiers.add(recentlyInstalledApp.getIdentifier())
        }
        
        // Calculate how many slots remain after recent install (if any)
        val remainingSlots = limit - smartList.size
        
        // Add recent apps (up to half of remaining slots, minimum 1)
        val recentLimit = maxOf(1, remainingSlots / 2)
        recentIdentifiers.take(recentLimit).forEach { identifier ->
            if (smartList.size >= limit) return@forEach
            val app = allApps.find { it.getIdentifier() == identifier }
            if (app != null && addedIdentifiers.add(identifier)) {
                smartList.add(app)
            }
        }
        
        // Add most used apps to fill remaining slots
        mostUsedApps.forEach { app ->
            if (smartList.size >= limit) return@forEach
            if (addedIdentifiers.add(app.getIdentifier())) {
                smartList.add(app)
            }
        }
        

        
        return smartList.take(limit)
    }
}
