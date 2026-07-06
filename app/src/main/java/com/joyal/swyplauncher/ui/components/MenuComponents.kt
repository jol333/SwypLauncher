package com.joyal.swyplauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared "genie" entrance/exit animation for the long-press context menus
 * ([AppContextMenu] and [ShortcutContextMenu]): the menu emerges from its icon on show and
 * collapses back into it on dismiss.
 *
 * The host composable applies [GenieMenuAnimation.graphicsLayerModifier] to the menu container,
 * calls [GenieMenuAnimation.dismiss] to play the exit animation before invoking [onDismiss], and
 * reads [GenieMenuAnimation.isDismissing] for the `Popup` focus flags. Keeping `dismiss` as a
 * plain value (rather than wrapping the whole Popup) lets callers trigger it from anywhere —
 * including a dialog rendered outside the popup.
 */
@Composable
internal fun rememberGenieMenuAnimation(onDismiss: () -> Unit): GenieMenuAnimation {
    val scope = rememberCoroutineScope()
    val slideOffset = remember { Animatable(60f) }
    val alphaAnim = remember { Animatable(0f) }
    val scaleYAnim = remember { Animatable(0.1f) }
    val scaleXAnim = remember { Animatable(0.3f) }
    var isDismissing by remember { mutableStateOf(false) }

    val bouncySpring = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
    val smoothSpring = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)

    // Genie entrance animation - emerging from icon
    LaunchedEffect(Unit) {
        launch { slideOffset.animateTo(0f, bouncySpring) }
        launch { alphaAnim.animateTo(1f, smoothSpring) }
        launch { scaleYAnim.animateTo(1f, bouncySpring) }
        launch { scaleXAnim.animateTo(1f, bouncySpring) }
    }

    // Exit animation, then dismiss shortly after it begins. Guards against double-dismissal.
    val dismiss: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            val exitSpring = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
            scope.launch {
                launch { slideOffset.animateTo(50f, exitSpring) }
                launch { alphaAnim.animateTo(0f, smoothSpring) }
                launch { scaleYAnim.animateTo(0.1f, exitSpring) }
                launch { scaleXAnim.animateTo(0.3f, exitSpring) }
                delay(150)
                onDismiss()
            }
        }
    }

    val graphicsLayerModifier = Modifier.graphicsLayer {
        translationY = slideOffset.value
        alpha = alphaAnim.value
        scaleX = scaleXAnim.value
        scaleY = scaleYAnim.value
        transformOrigin = TransformOrigin(0.5f, 1f)
    }

    return GenieMenuAnimation(graphicsLayerModifier, dismiss, isDismissing)
}

internal data class GenieMenuAnimation(
    val graphicsLayerModifier: Modifier,
    val dismiss: () -> Unit,
    val isDismissing: Boolean,
)

/** A single tappable row (leading icon + label) inside a genie context menu. */
@Composable
internal fun MenuOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}
