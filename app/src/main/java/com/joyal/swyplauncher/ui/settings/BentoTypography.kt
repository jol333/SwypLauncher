package com.joyal.swyplauncher.ui.settings

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography system for Bento Settings UI.
 * Standardized font styles to ensure visual consistency.
 */
object BentoTypography {
    /**
     * Large display text (48sp, Bold) - For large numbers, sort categories
     */
    val displayLarge = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 48.sp
    )
    
    /**
     * Main card titles (20sp, Bold) - "Try Swyp Launcher", "Buy me a coffee"
     */
    val titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
    
    /**
     * Card subtitles/toggle labels (18sp, Medium) - "Auto-open app", "Blur background"
     */
    val titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium
    )
    
    /**
     * Section headers uppercase (14sp, Medium) - "APPEARANCE", "VISUAL EFFECTS", "LAUNCH MODES"
     */
    val labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        lineHeight = 16.sp
    )
    
    /**
     * Body text, descriptions, button labels (14sp, Normal) - General body copy
     */
    val bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )
    
    /**
     * Button text (16sp, SemiBold) - All button labels in settings UI
     */
    val labelButton = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    /**
     * Small labels for app previews (12sp, Normal)
     */
    val labelSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    )
}
