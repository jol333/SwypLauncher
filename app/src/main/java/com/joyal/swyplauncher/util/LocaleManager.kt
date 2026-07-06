package com.joyal.swyplauncher.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.joyal.swyplauncher.domain.model.AppLanguage

/**
 * Manages locale switching for the app using AppCompatDelegate per-app language API.
 * This approach works on Android 13+ natively and provides backward compatibility.
 */
object LocaleManager {

    /**
     * Apply the selected language to the app.
     *
     * @param language The selected AppLanguage
     */
    fun applyLanguage(language: AppLanguage) {
        val localeList = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList() // Use system default
            else -> LocaleListCompat.forLanguageTags(language.code)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
