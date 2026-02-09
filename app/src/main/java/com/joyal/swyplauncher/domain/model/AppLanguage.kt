package com.joyal.swyplauncher.domain.model

/**
 * Supported app languages for Swyp Launcher.
 * 
 * @param code The locale/language code used for storage and locale resolution
 * @param displayName English display name for the language
 * @param nativeName Native display name (how the language appears in its own script)
 */
enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    SYSTEM("system", "System Default", "System Default"),
    ENGLISH("en", "English", "English"),
    MALAYALAM("ml", "Malayalam", "മലയാളം"),
    SIMPLIFIED_CHINESE("zh", "Chinese (Simplified)", "简体中文"),
    SPANISH("es", "Spanish", "Español"),
    PORTUGUESE_BRAZIL("pt", "Portuguese (Brazil)", "Português (Brasil)"),
    GERMAN("de", "German", "Deutsch"),
    HINDI("hi", "Hindi", "हिन्दी");

    companion object {
        /**
         * Find an AppLanguage by its code, returns SYSTEM if not found.
         */
        fun fromCode(code: String?): AppLanguage {
            return entries.find { it.code == code } ?: SYSTEM
        }

        /**
         * Get all user-selectable languages (excluding SYSTEM for certain uses).
         */
        val supportedLanguages: List<AppLanguage>
            get() = entries.filter { it != SYSTEM }
    }
}
