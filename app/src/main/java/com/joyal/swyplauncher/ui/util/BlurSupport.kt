package com.joyal.swyplauncher.ui.util

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.function.Consumer

/**
 * Observes whether the system will honour cross-window blur right now
 *
 * The returned state updates live: the underlying listener fires when the user toggles
 * battery saver or the accessibility setting during the session, so any UI gated on this
 * value will appear / disappear automatically.
 */
@Composable
fun rememberSystemBlurSupported(): Boolean {
    val context = LocalContext.current
    val windowManager = remember(context) {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    var supported by remember {
        mutableStateOf(windowManager.isCrossWindowBlurEnabled)
    }

    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled -> supported = enabled }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose {
            windowManager.removeCrossWindowBlurEnabledListener(listener)
        }
    }

    return supported
}
