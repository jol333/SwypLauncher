package com.joyal.swyplauncher.ui.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.IconPackIcon
import com.joyal.swyplauncher.domain.model.IconPackInfo
import com.joyal.swyplauncher.domain.repository.IconPackRepository
import com.joyal.swyplauncher.util.isBitmapDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// The showcase grid: icon edge, gap, tilt (clockwise), and the tile of distinct icons it repeats
private val ShowcaseIconSize = 44.dp
private val ShowcaseGap = 16.dp
private const val SHOWCASE_ANGLE = 30f
private const val SHOWCASE_ROWS = 3
private const val SHOWCASE_COLUMNS = 8

// One grid row per this long: a slow drift
private const val SHOWCASE_MILLIS_PER_ROW = 4_000

// Clear on the text's side, solid on the far side. Eased (smoothstep) rather than linear: a
// straight ramp starts abruptly enough to read as a hard edge across bright icons.
private val ShowcaseFade = Array(9) { i ->
    val t = i / 8f
    (0.45f + 0.45f * t) to Color.Black.copy(alpha = t * t * (3 - 2 * t))
}

/**
 * The Icon pack tile's backdrop: [icons] in a tilted grid that drifts slowly along its columns,
 * fading out towards the tile's text. Nothing while [icons] is empty.
 *
 * The grid is drawn once; the drift only moves the layer it is drawn in, so it costs no
 * recomposition or redraw per frame.
 */
@Composable
fun IconPackShowcase(icons: List<ImageBitmap>, modifier: Modifier = Modifier) {
    if (icons.isEmpty()) return

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(700)) }

    val period = SHOWCASE_ROWS * SHOWCASE_MILLIS_PER_ROW
    val phase by rememberInfiniteTransition(label = "showcaseDrift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
        label = "showcasePhase"
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val radians = Math.toRadians(SHOWCASE_ANGLE.toDouble())
    // The grid's column direction, on screen
    val columnX = -sin(radians).toFloat()
    val columnY = cos(radians).toFloat()

    Box(
        modifier = modifier
            // Offscreen, so the fade below can cut into the icons and nothing else
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = appear.value
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = if (rtl) {
                        Brush.horizontalGradient(*ShowcaseFade, startX = size.width, endX = 0f)
                    } else {
                        Brush.horizontalGradient(*ShowcaseFade)
                    },
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // Up the tilted columns; after a whole tile the grid looks as it started
                    val travel = phase * SHOWCASE_ROWS * (ShowcaseIconSize + ShowcaseGap).toPx()
                    translationX = -travel * columnX
                    translationY = -travel * columnY
                }
        ) {
            drawShowcaseGrid(icons)
        }
    }
}

private fun DrawScope.drawShowcaseGrid(icons: List<ImageBitmap>) {
    val iconPx = ShowcaseIconSize.roundToPx()
    val stride = (ShowcaseIconSize + ShowcaseGap).toPx()
    // Enough cells to cover the canvas at any tilt, plus the tile the drift moves through
    val reach = hypot(size.width, size.height) / 2 + stride
    val travel = SHOWCASE_ROWS * stride
    val firstColumn = floor(-reach / stride).toInt()
    val lastColumn = ceil(reach / stride).toInt()
    val firstRow = floor(-reach / stride).toInt()
    val lastRow = ceil((reach + travel) / stride).toInt()
    val iconSize = IntSize(iconPx, iconPx)

    rotate(SHOWCASE_ANGLE, pivot = center) {
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                // Rows repeat every SHOWCASE_ROWS, which is what makes the drift loop seamlessly
                val cell = Math.floorMod(row, SHOWCASE_ROWS) * SHOWCASE_COLUMNS +
                    Math.floorMod(column, SHOWCASE_COLUMNS)
                val topLeft = center + Offset(column * stride, row * stride) - Offset(iconPx / 2f, iconPx / 2f)
                drawImage(
                    image = icons[cell % icons.size],
                    dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = iconSize
                )
            }
        }
    }
}

/**
 * Picks and renders the icons for [IconPackShowcase]: what the installed packs draw for the
 * user's own apps, interleaved so that neighbours come from different packs. Icons that would
 * vanish on the dark tile are only used when there aren't enough others.
 *
 * Kept for the life of the process, so the settings screen only pays for it once.
 */
object IconPackShowcaseLoader {

    // The repository keeps four parsed packs; looking at more would push out the applied one
    private const val MAX_PACKS = 4
    private const val TARGET = SHOWCASE_ROWS * SHOWCASE_COLUMNS

    // Out of the way of the settings screen's own first frames
    private const val FIRST_LOAD_DELAY_MS = 400L

    private class Showcase(val packs: Set<String>, val bitmaps: List<ImageBitmap>)

    @Volatile
    private var cached: Showcase? = null

    suspend fun load(
        context: Context,
        repository: IconPackRepository,
        packs: List<IconPackInfo>,
        selectedPackage: String?,
        apps: List<AppInfo>
    ): List<ImageBitmap> {
        // The applied pack always has a place
        val shown = packs.sortedByDescending { it.packageName == selectedPackage }
            .take(MAX_PACKS)
            .map { it.packageName }
        if (shown.isEmpty()) return emptyList()
        cached?.takeIf { it.packs == shown.toSet() }?.let { return it.bitmaps }

        delay(FIRST_LOAD_DELAY_MS)
        val bitmaps = withContext(Dispatchers.IO) { pick(context, repository, shown, apps) }
        cached = Showcase(shown.toSet(), bitmaps)
        return bitmaps
    }

    private suspend fun pick(
        context: Context,
        repository: IconPackRepository,
        packs: List<String>,
        apps: List<AppInfo>
    ): List<ImageBitmap> {
        val imageLoader = SingletonImageLoader.get(context)
        val sizePx = (ShowcaseIconSize.value * context.resources.displayMetrics.density).toInt()
        // The same mix every time for the same packs
        val random = Random(packs.sorted().hashCode())
        val shuffledApps = apps.shuffled(random)
        // A few spare per pack, for the dark ones that get passed over
        val candidatesPerPack = TARGET / packs.size + 4

        // Per pack, its usable icons: bright ones first, then dark
        val perPack = packs.mapIndexed { index, pack ->
            // Each pack starts at a different place in the app list, so the mix shows more apps
            val start = shuffledApps.size * index / packs.size
            val fromApps = (shuffledApps.drop(start) + shuffledApps.take(start)).asSequence()
                .mapNotNull { app -> runCatching { repository.findIcon(pack, app) }.getOrNull()?.icon }
                .distinct()
                .take(candidatesPerPack)
                .toList()
            // A pack with nothing for these apps still has icons of its own to show
            val candidates = fromApps.ifEmpty {
                repository.getAllIcons(pack).shuffled(random).take(candidatesPerPack)
            }
            val rendered = candidates.mapNotNull { icon -> render(context, imageLoader, icon, sizePx) }
            val (dark, bright) = rendered.partition { isBitmapDark(it) }
            ArrayDeque(bright) to ArrayDeque(dark)
        }

        val picked = ArrayList<Bitmap>(TARGET)
        fun takeRoundRobin(queue: (Pair<ArrayDeque<Bitmap>, ArrayDeque<Bitmap>>) -> ArrayDeque<Bitmap>) {
            while (picked.size < TARGET && perPack.any { queue(it).isNotEmpty() }) {
                for (pack in perPack) {
                    if (picked.size == TARGET) break
                    queue(pack).removeFirstOrNull()?.let(picked::add)
                }
            }
        }
        takeRoundRobin { it.first }
        if (picked.size < TARGET / 2) takeRoundRobin { it.second }
        return picked.map { it.asImageBitmap() }
    }

    private suspend fun render(
        context: Context,
        imageLoader: coil3.ImageLoader,
        icon: IconPackIcon,
        sizePx: Int
    ): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(icon)
            .size(sizePx)
            // Its own entry: other screens render the same icon at other sizes
            .memoryCacheKey("pack_icon_showcase_${icon.packPackage}/${icon.drawableName}")
            .build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return null
        return runCatching { result.image.toBitmap() }.getOrNull()
    }
}
