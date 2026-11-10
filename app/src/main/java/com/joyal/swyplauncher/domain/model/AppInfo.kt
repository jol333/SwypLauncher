package com.joyal.swyplauncher.domain.model

data class AppInfo(
    val packageName: String,
    val label: String,
    val firstLetter: Char,
    val iconDensity: Int = 480, // Default to xxxhdpi for high quality
    val installTime: Long = 0L, // Timestamp when app was installed
    val activityName: String? = null, // Specific activity name for apps with multiple launcher activities
    val category: String = "Other", // App category from ApplicationInfo
    val lastUpdateTime: Long = 0L // Timestamp of the last install/update; versions the on-disk icon cache
) {
    // Built once per instance: the identifier is read on every filter pass and grid-key lookup,
    // and the lowercase label on every sort comparison.
    private val identifier: String =
        if (activityName != null) "$packageName/$activityName" else packageName

    /** Lowercase label used as the alphabetical sort key. */
    val sortKey: String = label.lowercase()

    /**
     * Returns a unique identifier for this app/activity combination.
     * Format: "packageName/activityName" or just "packageName" if no activity specified.
     */
    fun getIdentifier(): String = identifier
}
