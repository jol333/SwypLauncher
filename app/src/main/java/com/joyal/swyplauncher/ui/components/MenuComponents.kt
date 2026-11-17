package com.joyal.swyplauncher.ui.components

import android.graphics.Rect
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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

/** Gap kept between a context menu and the top/bottom edges of the visible screen area. */
private val MenuEdgeMargin = 16.dp

@Composable
internal fun GenieContextMenuPopup(
    genie: GenieMenuAnimation,
    content: @Composable ColumnScope.() -> Unit
) {
    val view = LocalView.current
    val marginPx = with(LocalDensity.current) { MenuEdgeMargin.roundToPx() }
    val positionProvider = remember(view, marginPx) { ContextMenuPositionProvider(view, marginPx) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = genie.dismiss,
        properties = PopupProperties(
            focusable = !genie.isDismissing, // Release focus when dismissing
            dismissOnBackPress = !genie.isDismissing,
            dismissOnClickOutside = !genie.isDismissing
        )
    ) {
        Column(
            modifier = genie.graphicsLayerModifier
                // The popup measures its content against the visible display frame; leave the
                // edge gap on both sides of it so the position provider can honor it.
                .layout { measurable, constraints ->
                    val capped = if (constraints.hasBoundedHeight) {
                        constraints.copy(
                            maxHeight = (constraints.maxHeight - 2 * marginPx)
                                .coerceAtLeast(constraints.minHeight)
                        )
                    } else constraints
                    val placeable = measurable.measure(capped)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .width(IntrinsicSize.Min)
                // Clip to the rounded shape so scrolled items run right up to its edges
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState())
                // Vertical padding inside the scroll: only around the first/last item
                .padding(vertical = 8.dp),
            content = content
        )
    }
}

private class ContextMenuPositionProvider(
    private val view: View,
    private val margin: Int
) : PopupPositionProvider {
    private val frame = Rect()
    private val windowOrigin = IntArray(2)

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // Visible frame (screen coordinates) → this window's coordinates, which both
        // anchorBounds and the popup's position use.
        view.getWindowVisibleDisplayFrame(frame)
        view.rootView.getLocationOnScreen(windowOrigin)
        frame.offset(-windowOrigin[0], -windowOrigin[1])

        val top = frame.top + margin
        val bottom = frame.bottom - margin
        val y = (anchorBounds.top - popupContentSize.height)
            .coerceIn(top, (bottom - popupContentSize.height).coerceAtLeast(top))

        val preferredX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
        else anchorBounds.right - popupContentSize.width
        val x = preferredX
            .coerceIn(frame.left, (frame.right - popupContentSize.width).coerceAtLeast(frame.left))

        return IntOffset(x, y)
    }
}

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
