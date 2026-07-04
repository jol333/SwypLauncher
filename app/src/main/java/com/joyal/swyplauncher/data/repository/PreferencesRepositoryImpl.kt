package com.joyal.swyplauncher.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PreferencesRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "swyplauncher_prefs",
        Context.MODE_PRIVATE
    )

    override fun hasShownHandwritingInitToast(): Boolean = 
        prefs.getBoolean(KEY_HANDWRITING_INIT_TOAST_SHOWN, false)

    override fun setHandwritingInitToastShown() {
        prefs.edit().putBoolean(KEY_HANDWRITING_INIT_TOAST_SHOWN, true).apply()
    }

    override fun hasDownloadedHandwritingModel(): Boolean = 
        prefs.getBoolean(KEY_HANDWRITING_MODEL_DOWNLOADED, false)

    override fun setHandwritingModelDownloaded() {
        prefs.edit().putBoolean(KEY_HANDWRITING_MODEL_DOWNLOADED, true).apply()
    }

    override suspend fun isDefaultAssistantSet(): Boolean = 
        prefs.getBoolean(KEY_DEFAULT_ASSISTANT_SET, false)

    override suspend fun setDefaultAssistantConfigured(isConfigured: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_ASSISTANT_SET, isConfigured).apply()
    }

    override fun getSelectedMode(): LauncherMode {
        return try {
            LauncherMode.valueOf(prefs.getString(KEY_SELECTED_MODE, null) ?: LauncherMode.HANDWRITING.name)
        } catch (e: IllegalArgumentException) {
            LauncherMode.HANDWRITING
        }
    }

    override fun setSelectedMode(mode: LauncherMode) {
        prefs.edit().putString(KEY_SELECTED_MODE, mode.name).apply()
    }

    override fun getHiddenApps(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
    }

    override fun addHiddenApp(identifier: String) {
        val current = getHiddenApps().toMutableSet()
        current.add(identifier)
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, current).apply()
    }

    override fun removeHiddenApp(identifier: String) {
        val current = getHiddenApps().toMutableSet()
        current.remove(identifier)
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, current).apply()
    }

    override fun getGridSize(): Int = prefs.getInt(KEY_GRID_SIZE, 4)

    override fun setGridSize(size: Int) {
        prefs.edit().putInt(KEY_GRID_SIZE, size).apply()
    }

    override fun getCornerRadius(): Float = prefs.getFloat(KEY_CORNER_RADIUS, 0.85f)

    override fun setCornerRadius(radius: Float) {
        prefs.edit().putFloat(KEY_CORNER_RADIUS, radius).apply()
    }

    override fun isAutoOpenSingleResultEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_OPEN_SINGLE_RESULT, false)

    override fun setAutoOpenSingleResult(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_OPEN_SINGLE_RESULT, enabled).apply()
    }

    override fun isLoadAllAppsOnOpenEnabled(): Boolean =
        prefs.getBoolean(KEY_LOAD_ALL_APPS_ON_OPEN, true)

    override fun setLoadAllAppsOnOpen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOAD_ALL_APPS_ON_OPEN, enabled).apply()
    }

    override fun isShortcutSearchEnabled(): Boolean =
        prefs.getBoolean(KEY_SHORTCUT_SEARCH_ENABLED, false)

    override fun setShortcutSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHORTCUT_SEARCH_ENABLED, enabled).apply()
    }

    override fun getHiddenShortcuts(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_SHORTCUTS, emptySet()) ?: emptySet()

    override fun addHiddenShortcut(identifier: String) {
        val current = getHiddenShortcuts().toMutableSet()
        current.add(identifier)
        prefs.edit().putStringSet(KEY_HIDDEN_SHORTCUTS, current).apply()
    }

    // Shortcut ids can contain arbitrary characters, so use JSON rather than the delimiter
    // scheme used for app aliases (which would be ambiguous here).
    override fun getShortcutSearchAliases(): Map<String, Set<String>> {
        val raw = prefs.getString(KEY_SHORTCUT_SEARCH_ALIASES, null) ?: return emptyMap()
        return try {
            val json = org.json.JSONObject(raw)
            buildMap {
                json.keys().forEach { word ->
                    val arr = json.getJSONArray(word)
                    val ids = buildSet {
                        for (i in 0 until arr.length()) add(arr.getString(i))
                    }
                    if (ids.isNotEmpty()) put(word, ids)
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override fun setShortcutSearchAliases(aliases: Map<String, Set<String>>) {
        val json = org.json.JSONObject()
        aliases.forEach { (word, ids) ->
            json.put(word, org.json.JSONArray(ids.toList()))
        }
        prefs.edit().putString(KEY_SHORTCUT_SEARCH_ALIASES, json.toString()).apply()
    }

    override fun getEnabledModes(): List<LauncherMode> {
        val saved = prefs.getString(KEY_ENABLED_MODES, null)
        return if (saved != null) {
            saved.split(",").mapNotNull { 
                try { LauncherMode.valueOf(it) } catch (e: Exception) { null }
            }
        } else {
            LauncherMode.entries
        }
    }

    override fun setEnabledModes(modes: List<LauncherMode>) {
        prefs.edit().putString(KEY_ENABLED_MODES, modes.joinToString(",") { it.name }).apply()
    }

    override fun getAppSortOrder(): com.joyal.swyplauncher.domain.repository.AppSortOrder {
        return try {
            com.joyal.swyplauncher.domain.repository.AppSortOrder.valueOf(
                prefs.getString(KEY_APP_SORT_ORDER, null) ?: com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME.name
            )
        } catch (e: IllegalArgumentException) {
            com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME
        }
    }

    override fun setAppSortOrder(order: com.joyal.swyplauncher.domain.repository.AppSortOrder) {
        prefs.edit().putString(KEY_APP_SORT_ORDER, order.name).apply()
    }

    override fun hasPromptedForUsageStatsPermission(): Boolean = 
        prefs.getBoolean(KEY_USAGE_STATS_PERMISSION_PROMPTED, false)

    override fun setUsageStatsPermissionPrompted() {
        prefs.edit().putBoolean(KEY_USAGE_STATS_PERMISSION_PROMPTED, true).apply()
    }

    override fun isBackgroundBlurEnabled(): Boolean = 
        prefs.getBoolean(KEY_BACKGROUND_BLUR_ENABLED, false)

    override fun setBackgroundBlurEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_BLUR_ENABLED, enabled).apply()
    }

    override fun getBlurLevel(): Int = prefs.getInt(KEY_BLUR_LEVEL, 80)

    override fun setBlurLevel(level: Int) {
        prefs.edit().putInt(KEY_BLUR_LEVEL, level).apply()
    }

    override fun getAppShortcuts(): Map<String, Set<String>> {
        val json = prefs.getString(KEY_APP_SHORTCUTS, null) ?: return emptyMap()
        return try {
            json.split("|").filter { it.isNotBlank() }.associate { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    parts[0] to parts[1].split(",").toSet()
                } else {
                    "" to emptySet()
                }
            }.filterKeys { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override fun setAppShortcuts(shortcuts: Map<String, Set<String>>) {
        val json = shortcuts.entries.joinToString("|") { (shortcut, apps) ->
            "$shortcut:${apps.joinToString(",")}"
        }
        prefs.edit().putString(KEY_APP_SHORTCUTS, json).apply()
    }

    override fun getCustomGestures(): List<com.joyal.swyplauncher.domain.model.CustomGesture> {
        val json = prefs.getString(KEY_CUSTOM_GESTURES, null)
        return com.joyal.swyplauncher.util.GestureRecognizer.deserialize(json)
    }

    override fun setCustomGestures(
        gestures: List<com.joyal.swyplauncher.domain.model.CustomGesture>
    ) {
        val json = com.joyal.swyplauncher.util.GestureRecognizer.serialize(gestures)
        prefs.edit().putString(KEY_CUSTOM_GESTURES, json).apply()
    }

    override fun getAppLanguage(): com.joyal.swyplauncher.domain.model.AppLanguage {
        val code = prefs.getString(KEY_APP_LANGUAGE, null)
        return com.joyal.swyplauncher.domain.model.AppLanguage.fromCode(code)
    }

    override fun setAppLanguage(language: com.joyal.swyplauncher.domain.model.AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.code).apply()
    }

    override fun getCurrencyRatesJson(): String? = prefs.getString(KEY_CURRENCY_RATES_JSON, null)
    override fun getCurrencyRatesBase(): String? = prefs.getString(KEY_CURRENCY_RATES_BASE, null)
    override fun getCurrencyRatesTimestamp(): Long = prefs.getLong(KEY_CURRENCY_RATES_TIMESTAMP, 0L)

    override fun setCurrencyRates(base: String, json: String, timestamp: Long) {
        prefs.edit()
            .putString(KEY_CURRENCY_RATES_BASE, base)
            .putString(KEY_CURRENCY_RATES_JSON, json)
            .putLong(KEY_CURRENCY_RATES_TIMESTAMP, timestamp)
            .apply()
    }

    override fun getEnabledConversionCategories(): Set<String>? {
        if (!prefs.contains(KEY_ENABLED_CONVERSION_CATEGORIES)) {
            return null // First launch — caller treats null as "all enabled"
        }
        val stored = prefs.getStringSet(KEY_ENABLED_CONVERSION_CATEGORIES, emptySet()) ?: emptySet()
        // One-time migration: time-zone conversion shipped after this preference could
        // already be saved, so a pre-existing set can never contain it. Enable it once
        // for upgraders (mirrors "new categories default on") without overriding any
        // category they later choose to disable.
        if (!prefs.contains(KEY_TIMEZONE_CATEGORY_MIGRATED)) {
            val migrated = stored + "TIMEZONE"
            prefs.edit()
                .putStringSet(KEY_ENABLED_CONVERSION_CATEGORIES, migrated)
                .putBoolean(KEY_TIMEZONE_CATEGORY_MIGRATED, true)
                .apply()
            return migrated
        }
        return stored
    }

    override fun setEnabledConversionCategories(categories: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED_CONVERSION_CATEGORIES, categories).apply()
    }

    companion object {
        private const val KEY_HANDWRITING_INIT_TOAST_SHOWN = "handwriting_init_toast_shown"
        private const val KEY_HANDWRITING_MODEL_DOWNLOADED = "handwriting_model_downloaded"
        private const val KEY_DEFAULT_ASSISTANT_SET = "default_assistant_set"
        private const val KEY_SELECTED_MODE = "selected_mode"
        private const val KEY_HIDDEN_APPS = "hidden_apps"
        private const val KEY_GRID_SIZE = "grid_size"
        private const val KEY_CORNER_RADIUS = "corner_radius"
        private const val KEY_AUTO_OPEN_SINGLE_RESULT = "auto_open_single_result"
        private const val KEY_LOAD_ALL_APPS_ON_OPEN = "load_all_apps_on_open"
        private const val KEY_SHORTCUT_SEARCH_ENABLED = "shortcut_search_enabled"
        private const val KEY_HIDDEN_SHORTCUTS = "hidden_shortcuts"
        private const val KEY_SHORTCUT_SEARCH_ALIASES = "shortcut_search_aliases"
        private const val KEY_ENABLED_MODES = "enabled_modes"
        private const val KEY_APP_SORT_ORDER = "app_sort_order"
        private const val KEY_USAGE_STATS_PERMISSION_PROMPTED = "usage_stats_permission_prompted"
        private const val KEY_BACKGROUND_BLUR_ENABLED = "background_blur_enabled"
        private const val KEY_BLUR_LEVEL = "blur_level"
        private const val KEY_APP_SHORTCUTS = "app_shortcuts"
        private const val KEY_CUSTOM_GESTURES = "custom_gestures"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_CURRENCY_RATES_JSON = "currency_rates_json"
        private const val KEY_CURRENCY_RATES_BASE = "currency_rates_base"
        private const val KEY_CURRENCY_RATES_TIMESTAMP = "currency_rates_timestamp"
        private const val KEY_ENABLED_CONVERSION_CATEGORIES = "enabled_conversion_categories"
        private const val KEY_TIMEZONE_CATEGORY_MIGRATED = "timezone_category_migrated"
    }
}
