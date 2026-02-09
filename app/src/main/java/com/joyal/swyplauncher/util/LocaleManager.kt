package com.joyal.swyplauncher.util

import android.content.Context
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.joyal.swyplauncher.domain.model.AppLanguage
import java.util.Locale

/**
 * Manages locale switching for the app using AppCompatDelegate per-app language API.
 * This approach works on Android 13+ natively and provides backward compatibility.
 */
object LocaleManager {

    /**
     * Supported language codes in the app.
     */
    private val supportedLanguageCodes = setOf("en", "ml", "zh", "es", "pt", "de", "hi")

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

    /**
     * Get the current effective locale, resolving system default to an actual locale.
     * Falls back to English if the system language is not supported.
     * 
     * @param context The application context
     * @return The effective Locale to use
     */
    fun getEffectiveLocale(context: Context): Locale {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        
        // If app has a specific locale set, use it
        if (!appLocales.isEmpty) {
            return appLocales.get(0) ?: Locale.ENGLISH
        }
        
        // Otherwise, check system locale
        val systemLocale = context.resources.configuration.locales.get(0) ?: Locale.ENGLISH
        
        // Check if system language is supported
        val languageCode = getBaseLanguageCode(systemLocale)
        return if (languageCode in supportedLanguageCodes) {
            // Return a locale with just the base language code
            Locale.Builder().setLanguage(languageCode).build()
        } else {
            Locale.ENGLISH
        }
    }

    /**
     * Get the base language code, handling English variants.
     * All English variants (en-US, en-GB, en-IN, etc.) map to "en".
     * 
     * @param locale The locale to extract the base code from
     * @return The base language code
     */
    private fun getBaseLanguageCode(locale: Locale): String {
        return locale.language // Returns just the 2-letter language code (e.g., "en", "ml")
    }

    /**
     * Check if a locale is any variant of English.
     * 
     * @param locale The locale to check
     * @return True if the locale is any English variant
     */
    fun isEnglishVariant(locale: Locale): Boolean {
        return locale.language == "en"
    }

    /**
     * Get the currently applied AppLanguage setting.
     * 
     * @return The current AppLanguage or SYSTEM if using system default
     */
    fun getCurrentLanguage(): AppLanguage {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        
        if (appLocales.isEmpty) {
            return AppLanguage.SYSTEM
        }
        
        val locale = appLocales.get(0) ?: return AppLanguage.SYSTEM
        return AppLanguage.entries.find { it.code == locale.language } ?: AppLanguage.SYSTEM
    }
}
