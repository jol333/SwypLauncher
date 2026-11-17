package com.joyal.swyplauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.joyal.swyplauncher.ui.theme.BentoColors
import com.joyal.swyplauncher.util.dominantColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared look of the two icon pack screens (the pack chooser in settings and the per-app icon
 * picker in the assistant): both light the screen with colors taken from the icons on show.
 */

/** Ambient colors when there is nothing colorful to take them from: the app's own greens. */
val DefaultAmbientColors = listOf(BentoColors.AccentGreen, BentoColors.AccentGreenTeal)

/** Shape of an app icon of [size], following the user's corner radius setting like the app grid. */
fun appIconShape(size: Dp, cornerRadiusPercent: Float) =
    RoundedCornerShape(size / 2 * cornerRadiusPercent.coerceIn(0f, 1f))

/**
 * The characteristic colors of whatever [model] loads to (any Coil model; null for none), for
 * [AmbientBackdrop]. Falls back to [DefaultAmbientColors] while loading, if the image can't be
 * loaded, or if it is colorless.
 */
@Composable
fun rememberAmbientColors(model: Any?): List<Color> {
    val context = LocalContext.current
    val colors by produceState(initialValue = DefaultAmbientColors, model) {
        value = if (model == null) {
            DefaultAmbientColors
        } else {
            withContext(Dispatchers.Default) {
                val request = ImageRequest.Builder(context).data(model).build()
                val result = SingletonImageLoader.get(context).execute(request)
                runCatching { (result as? SuccessResult)?.image?.toBitmap() }.getOrNull()
                    ?.let { bitmap -> dominantColors(bitmap).map { Color(it) } }
                    ?.takeIf { it.isNotEmpty() }
                    ?: DefaultAmbientColors
            }
        }
    }
    return colors
}

/**
 * A dark backdrop lit by soft pools of [colors], which drift to the new colors whenever they
 * change. [base] is what the light falls on: opaque for a screen of its own, translucent when
 * something blurred should show through.
 */
@Composable
fun AmbientBackdrop(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    base: Color = BentoColors.BackgroundDark
) {
    val primary by animateColorAsState(
        targetValue = colors.firstOrNull() ?: DefaultAmbientColors[0],
        animationSpec = tween(700),
        label = "ambientPrimary"
    )
    val secondary by animateColorAsState(
        targetValue = colors.getOrNull(1) ?: colors.firstOrNull() ?: DefaultAmbientColors[1],
        animationSpec = tween(1000),
        label = "ambientSecondary"
    )
    // Colors are read in the draw phase: the drift repaints this box without recomposing anything.
    Box(
        modifier = modifier.drawBehind {
            drawRect(base)
            pool(primary, alpha = 0.42f, x = 0.10f, y = 0.06f, radius = 1.15f)
            pool(secondary, alpha = 0.28f, x = 1.00f, y = 0.50f, radius = 1.00f)
            pool(primary, alpha = 0.18f, x = 0.20f, y = 1.02f, radius = 0.95f)
        }
    )
}

// One pool of light; position is a fraction of the box, radius a fraction of its shorter edge.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.pool(
    color: Color,
    alpha: Float,
    x: Float,
    y: Float,
    radius: Float
) {
    val center = Offset(size.width * x, size.height * y)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = size.minDimension * radius
        )
    )
}
