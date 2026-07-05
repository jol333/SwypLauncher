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

    // When true (default), the full app list is shown as soon as the assistant opens.
    // When false, only suggested apps are shown until the user reveals the rest.
    fun isLoadAllAppsOnOpenEnabled(): Boolean
    fun setLoadAllAppsOnOpen(enabled: Boolean)

    // App shortcut search in assistant results (default off). Requires the assistant role;
    // automatically switched off when the role is revoked.
    fun isShortcutSearchEnabled(): Boolean
    fun setShortcutSearchEnabled(enabled: Boolean)

    // Individually hidden app-shortcut search results, keyed by "packageName/shortcutId".
    fun getHiddenShortcuts(): Set<String>
    fun addHiddenShortcut(identifier: String)
    fun removeHiddenShortcut(identifier: String)

    // Search aliases (magic word -> set of "packageName/shortcutId") for app shortcuts,
    // set only from the assistant long-press menu.
    fun getShortcutSearchAliases(): Map<String, Set<String>>
    fun setShortcutSearchAliases(aliases: Map<String, Set<String>>)

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

    fun getCustomGestures(): List<com.joyal.swyplauncher.domain.model.CustomGesture>
    fun setCustomGestures(gestures: List<com.joyal.swyplauncher.domain.model.CustomGesture>)

    fun getAppLanguage(): com.joyal.swyplauncher.domain.model.AppLanguage
    fun setAppLanguage(language: com.joyal.swyplauncher.domain.model.AppLanguage)

    // Conversion categories: which categories are enabled in assistant search results.
    // Returns null on first launch (meaning all enabled); otherwise the persisted set.
    fun getEnabledConversionCategories(): Set<String>?
    fun setEnabledConversionCategories(categories: Set<String>)

    // Currency rates cache (base currency + rates JSON + fetched timestamp)
    fun getCurrencyRatesJson(): String?
    fun getCurrencyRatesBase(): String?
    fun getCurrencyRatesTimestamp(): Long
    fun setCurrencyRates(base: String, json: String, timestamp: Long)
}

enum class AppSortOrder {
    NAME, USAGE, CATEGORY
}
