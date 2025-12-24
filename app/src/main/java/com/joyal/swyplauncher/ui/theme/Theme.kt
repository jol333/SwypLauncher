package com.joyal.swyplauncher.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BentoColors.AccentGreen,
    secondary = BentoColors.AccentGreenLight,
    tertiary = Pink80,
    surface = BentoColors.BackgroundDark,
    background = BentoColors.BackgroundDark,
    primaryContainer = BentoColors.AccentGreenDark,
    onPrimaryContainer = Color.White,
    tertiaryContainer = Color(0xFF3E3E42), // Neutral gray instead of pink
    onTertiaryContainer = Color.White
)

@Composable
fun SwypLauncherTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}