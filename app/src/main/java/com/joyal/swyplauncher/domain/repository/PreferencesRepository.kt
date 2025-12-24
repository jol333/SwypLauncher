package com.joyal.swyplauncher.domain.repository

import com.joyal.swyplauncher.domain.model.LauncherMode

interface PreferencesRepository {
    fun hasShownHandwritingInitToast(): Boolean
    fun setHandwritingInitToastShown()
    
    fun hasDownloadedHandwritingModel(): Boolean
    fun setHandwritingModelDownloaded()
    
    suspend fun isDefaultAssistantSet(): Boolean
    suspend fun setDefaultAssistantConfigured(isConfigured: Boolean)
    
    fun getSelectedMode(): LauncherMode
    fun setSelectedMode(mode: LauncherMode)
    
    fun getHiddenApps(): Set<String>
    fun addHiddenApp(identifier: String)
    fun removeHiddenApp(identifier: String)
    
    fun getGridSize(): Int
    fun setGridSize(size: Int)
    
    fun getCornerRadius(): Float
    fun setCornerRadius(radius: Float)
    
    fun isAutoOpenSingleResultEnabled(): Boolean
    fun setAutoOpenSingleResult(enabled: Boolean)
    
    fun getEnabledModes(): List<LauncherMode>
    fun setEnabledModes(modes: List<LauncherMode>)
    
    fun getAppSortOrder(): AppSortOrder
    fun setAppSortOrder(order: AppSortOrder)
    
    fun hasPromptedForUsageStatsPermission(): Boolean
    fun setUsageStatsPermissionPrompted()
    
    fun isBackgroundBlurEnabled(): Boolean
    fun setBackgroundBlurEnabled(enabled: Boolean)
    
    fun getBlurLevel(): Int
    fun setBlurLevel(level: Int)
    
    fun getAppShortcuts(): Map<String, Set<String>>
    fun setAppShortcuts(shortcuts: Map<String, Set<String>>)
}

enum class AppSortOrder {
    NAME, USAGE, CATEGORY
}
