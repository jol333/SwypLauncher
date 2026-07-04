package com.joyal.swyplauncher.domain.model

import android.graphics.Bitmap

/**
 * A launchable app shortcut ("Compose" in Gmail, "New text note" in Keep, …) surfaced
 * in assistant search results. Icons are loaded separately (see [ShortcutIcon]) so the
 * search index stays lightweight.
 */
data class ShortcutSearchItem(
    val id: String,
    val packageName: String,
    val label: String,
    val appLabel: String
) {
    /** Stable key used for hidden-shortcut and search-alias storage. */
    fun identifier(): String = "$packageName/$id"
}

/**
 * Render-ready shortcut icon. [isDark] marks icons that are mostly black/dark gray and
 * would vanish on the assistant's black sheet — the UI puts a white backing behind them.
 */
data class ShortcutIcon(
    val bitmap: Bitmap,
    val isDark: Boolean
)
