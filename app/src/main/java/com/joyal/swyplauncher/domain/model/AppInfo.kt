package com.joyal.swyplauncher.domain.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val firstLetter: Char,
    val iconDensity: Int = 480, // Default to xxxhdpi for high quality
    val installTime: Long = 0L, // Timestamp when app was installed
    val activityName: String? = null, // Specific activity name for apps with multiple launcher activities
    val category: String = "Other" // App category from ApplicationInfo
) {
    /**
     * Returns a unique identifier for this app/activity combination.
     * Format: "packageName/activityName" or just "packageName" if no activity specified.
     */
    fun getIdentifier(): String = if (activityName != null) "$packageName/$activityName" else packageName
}
