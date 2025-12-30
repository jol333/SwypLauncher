package com.joyal.swyplauncher.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.InkPoint
import com.joyal.swyplauncher.domain.model.InkStroke
import com.joyal.swyplauncher.domain.model.RecognitionResult
import com.joyal.swyplauncher.ui.components.AppIconItem
import com.joyal.swyplauncher.ui.model.AppListItem
import com.joyal.swyplauncher.ui.util.combineAppListsWithHeaders
import com.joyal.swyplauncher.ui.viewmodel.HandwritingViewModel
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Particle sparkle system
data class Particle(
    var position: Offset,
    var velocity: Offset,
    var life: Float,
    var maxLife: Float,
    var color: Color,
    var size: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandwritingModeScreen(
    onDismiss: () -> Unit,
    launcherViewModel: LauncherViewModel,
    handwritingViewModel: HandwritingViewModel = hiltViewModel(),
    onAddShortcut: ((String) -> Unit)? = null
) {
    val handwritingState by handwritingViewModel.uiState.collectAsState()
    val launcherState by launcherViewModel.uiState.collectAsState()
    val gridSize by launcherViewModel.gridSize.collectAsState()
    val cornerRadius by launcherViewModel.cornerRadius.collectAsState()
    val autoOpenSingleResult by launcherViewModel.autoOpenSingleResult.collectAsState()
    val sortOrder by launcherViewModel.appSortOrder.collectAsState()
    val context = LocalContext.current

    // Memoize combined app list to avoid recalculation on every recomposition
    val combinedAppList = remember(
        launcherState.handwritingSmartApps,
        launcherState.handwritingFilteredApps,
        sortOrder,
        handwritingState.recognizedText,
        gridSize
    ) {
        combineAppListsWithHeaders(
            launcherState.handwritingSmartApps,
            launcherState.handwritingFilteredApps,
            sortOrder,
            isSearching = handwritingState.recognizedText.isNotEmpty(),
            gridSize = gridSize
        )
    }

    // Track menu state outside of LazyGrid items to prevent state loss
    var selectedAppIndex by remember { mutableIntStateOf(-1) }

    val currentPath = remember { mutableStateListOf<Offset>() }

    // Remember scroll state for the app grid
    val gridScrollState = rememberLazyGridState()

    // Use regular mutable list instead of mutableStateListOf to avoid triggering recomposition
    val particles = remember { mutableListOf<Particle>() }
    var particleUpdateTrigger by remember { mutableIntStateOf(0) }

    // Animation: slowly rotate ink hue for a magical shimmer
    val hue by rememberInfiniteTransition(label = "inkHue").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue"
    )

    val density = LocalDensity.current
    val baseStrokeWidth = with(density) { 8.dp.toPx() } // base stroke width

    // Show toast only once when initialization completes for the first time
    LaunchedEffect(handwritingState.isInitialized, handwritingState.hasShownInitToast) {
        if (handwritingState.isInitialized && !handwritingState.hasShownInitToast) {
            Toast.makeText(context, "Ready to recognize handwriting", Toast.LENGTH_SHORT).show()
            handwritingViewModel.onInitializationToastShown()
        }
    }

    // Track if we were searching, to detect clear events and reset scroll
    var wasSearching by remember { mutableStateOf(false) }

    // Filter apps based on recognized text - only match apps starting with the text
    LaunchedEffect(handwritingState.recognizedText) {
        if (handwritingState.recognizedText.isNotEmpty()) {
            launcherViewModel.filterAppsByPrefixHandwriting(handwritingState.recognizedText)
        } else {
            launcherViewModel.resetFilterHandwriting()
        }
    }

    // Re-apply filter when loading completes if there's an active search term
    // This handles the case where user searches while apps are still loading
    var wasLoading by remember { mutableStateOf(launcherState.isLoading) }
    LaunchedEffect(launcherState.isLoading, handwritingState.recognizedText) {
        if (wasLoading && !launcherState.isLoading && handwritingState.recognizedText.isNotEmpty()) {
            launcherViewModel.filterAppsByPrefixHandwriting(handwritingState.recognizedText)
        }
        wasLoading = launcherState.isLoading
    }

    // When recognized text is cleared (transition from non-empty -> empty), reset scroll to top
    LaunchedEffect(handwritingState.recognizedText) {
        if (wasSearching && handwritingState.recognizedText.isEmpty()) {
            // Give time for the list to recompose with full app list before scrolling
            kotlinx.coroutines.delay(50)
            gridScrollState.scrollToItem(0)
        }
        wasSearching = handwritingState.recognizedText.isNotEmpty()
    }

    // Auto-open single result if enabled
    LaunchedEffect(
        launcherState.handwritingFilteredApps,
        autoOpenSingleResult,
        handwritingState.recognizedText
    ) {
        if (autoOpenSingleResult &&
            handwritingState.recognizedText.isNotEmpty() &&
            launcherState.handwritingFilteredApps.size == 1
        ) {
            kotlinx.coroutines.delay(300) // Small delay to avoid accidental opens
            val app = launcherState.handwritingFilteredApps[0]
            launcherViewModel.launchApp(app.packageName, app.activityName)
            onDismiss()
        }
    }

    // Update particles at 30 FPS instead of screen refresh rate to reduce CPU usage
    LaunchedEffect(Unit) {
        var lastTime = 0L
        val targetFrameTime = 1_000_000_000L / 30 // 30 FPS = ~33ms per frame
        while (true) {
            withFrameNanos { now ->
                val elapsed = now - lastTime
                // Only update if enough time has passed (throttle to 30 FPS)
                if (lastTime == 0L || elapsed >= targetFrameTime) {
                    val dt = if (lastTime == 0L) 0f else elapsed / 1_000_000_000f
                    lastTime = now

                    if (dt > 0f && particles.isNotEmpty()) {
                        // Simple physics + fade - use iterator for safe removal
                        val iterator = particles.iterator()
                        while (iterator.hasNext()) {
                            val p = iterator.next()
                            p.life += dt
                            if (p.life >= p.maxLife) {
                                iterator.remove()
                            } else {
                                // Update velocity and position
                                p.velocity = Offset(p.velocity.x * 0.985f, p.velocity.y + 900f * dt)
                                p.position += p.velocity * dt
                            }
                        }
                        // Trigger recomposition only when particles exist
                        particleUpdateTrigger++
                    }
                }
            }
        }
    }

    // Handle back button: clear strokes if any exist, otherwise close bottom sheet
    BackHandler(enabled = handwritingState.strokes.isNotEmpty()) {
        handwritingViewModel.clearStrokes()
        launcherViewModel.resetFilterHandwriting()
        particles.clear()
        currentPath.clear()
    }

    // Orientation detection for responsive layout
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val canvasHeight = if (isLandscape) 160.dp else 260.dp

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Drawing canvas area - responsive height based on orientation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { } // Enable hardware acceleration for Canvas
                    .pointerInput(handwritingState.isInitialized) {
                        if (handwritingState.isInitialized) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath.clear()
                                    currentPath.add(offset)
                                    // Burst of particles on start
                                    emitSparkles(
                                        particles = particles,
                                        at = offset,
                                        baseStrokeWidth = baseStrokeWidth,
                                        hue = hue
                                    )
                                },
                                onDrag = { change, _ ->
                                    currentPath.add(change.position)
                                    // Emit sparkles along the stroke
                                    emitSparkles(
                                        particles = particles,
                                        at = change.position,
                                        baseStrokeWidth = baseStrokeWidth,
                                        hue = hue
                                    )
                                },
                                onDragEnd = {
                                    if (currentPath.isNotEmpty()) {
                                        val now = System.currentTimeMillis()
                                        val points = currentPath.mapIndexed { index, offset ->
                                            InkPoint(
                                                x = offset.x,
                                                y = offset.y,
                                                timestamp = now + index
                                            )
                                        }
                                        handwritingViewModel.addStroke(InkStroke(points))
                                        currentPath.clear()
                                    }
                                }
                            )
                        }
                    }
            ) {
                // Read the trigger to ensure recomposition while particles are animating
                @Suppress("UNUSED_VARIABLE")
                val unusedTrigger = particleUpdateTrigger

                // Ink shimmer brush
                val inkColors = listOf(
                    Color.hsv((hue) % 360f, 0.9f, 1f),
                    Color.hsv((hue + 60f) % 360f, 0.9f, 1f),
                    Color.hsv((hue + 120f) % 360f, 0.9f, 1f)
                )
                val shimmerBrush = Brush.linearGradient(
                    colors = inkColors,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )

                // Helper to draw stroke with shimmer and highlight
                fun drawStroke(path: Path, highlightAlpha: Float) {
                    drawPath(
                        path = path,
                        brush = shimmerBrush,
                        style = Stroke(width = baseStrokeWidth, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = highlightAlpha),
                        style = Stroke(width = baseStrokeWidth * 0.5f, cap = StrokeCap.Round)
                    )
                }

                // Draw completed strokes
                handwritingState.strokes.forEach { stroke ->
                    if (stroke.points.size > 1) {
                        drawStroke(
                            createSmoothPath(stroke.points.map { Offset(it.x, it.y) }),
                            0.18f
                        )
                    }
                }

                // Draw current path being drawn
                if (currentPath.size > 1) {
                    drawStroke(createSmoothPath(currentPath), 0.22f)
                }

                // Sparkle particles — twinkle and fade
                particles.forEach { p ->
                    val t = (p.life / p.maxLife).coerceIn(0f, 1f)
                    val alpha =
                        (1f - t) * (0.45f + 0.55f * sin((t * 6f) * PI.toFloat()).coerceIn(0f, 1f))
                    // main sparkle
                    drawCircle(p.color.copy(alpha), p.size, p.position)
                    // soft glow halo
                    drawCircle(
                        Brush.radialGradient(
                            listOf(p.color.copy(alpha * 0.4f), Color.Transparent),
                            p.position,
                            p.size * 3f
                        ),
                        p.size * 3f,
                        p.position
                    )
                }


            }

            // Initialization and error states - only show if not initialized yet
            when (val result = handwritingState.recognitionResult) {
                is RecognitionResult.Initializing -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = "Downloading handwriting recognition model...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is RecognitionResult.Error -> {
                    if (result.isInitializationError) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            androidx.compose.material3.Button(
                                onClick = { handwritingViewModel.retryInitialization() }
                            ) {
                                Text("Retry")
                            }
                        }
                    } else {
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                        )
                    }
                }

                else -> {
                    // "Scribble here" hint - show only when initialized, no strokes, and not currently drawing
                    if (handwritingState.isInitialized && handwritingState.strokes.isEmpty() && currentPath.isEmpty()) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(
                                R.drawable.ic_scribble_here
                            ),
                            contentDescription = "Scribble here",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = 24.dp)
                                .size(width = 158.dp, height = 21.dp)
                                .alpha(0.2f),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // "Recognized:" label and "Undo/Clear" actions - always reserve space to prevent jumping
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .alpha(if (handwritingState.strokes.isNotEmpty()) 1f else 0f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recognized: ${handwritingState.recognizedText}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Undo button with tooltip
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above
                    ),
                    tooltip = {
                        PlainTooltip {
                            Text("Undo")
                        }
                    },
                    state = rememberTooltipState()
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (handwritingState.strokes.size == 1) {
                                // Last stroke - clear everything
                                handwritingViewModel.clearStrokes()
                                launcherViewModel.resetFilterHandwriting()
                                currentPath.clear()
                                particles.clear()
                            } else {
                                // Remove last stroke
                                handwritingViewModel.removeLastStroke()
                            }
                        },
                        enabled = handwritingState.strokes.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(
                                R.drawable.ic_undo
                            ),
                            contentDescription = "Undo",
                            tint = if (handwritingState.strokes.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                // Clear button
                TextButton(
                    onClick = {
                        handwritingViewModel.clearStrokes()
                        launcherViewModel.resetFilterHandwriting()
                        // Also clear visual effects
                        currentPath.clear()
                        particles.clear()
                    },
                    enabled = handwritingState.strokes.isNotEmpty()
                ) {
                    Text(
                        text = "CLEAR",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // App list - show smart 4 apps initially, all on scroll
        if (launcherState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (launcherState.handwritingCalculatorResult != null) {
            // Show calculator result
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = com.joyal.swyplauncher.util.CalculatorUtil.normalizeForDisplay(
                            handwritingState.recognizedText
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "= ${launcherState.handwritingCalculatorResult}",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (launcherState.handwritingSmartApps.isNotEmpty() || launcherState.handwritingFilteredApps.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
            ) {
                val showFade =
                    gridScrollState.firstVisibleItemIndex > 0 || gridScrollState.firstVisibleItemScrollOffset > 0

                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridSize),
                    state = gridScrollState,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = combinedAppList.size,
                        key = { index ->
                            when (val item = combinedAppList[index]) {
                                is AppListItem.App -> item.appInfo.getIdentifier()
                                is AppListItem.CategoryHeader -> "header_${item.category}"
                                is AppListItem.Divider -> "divider"
                            }
                        },
                        span = { index ->
                            when (combinedAppList[index]) {
                                is AppListItem.CategoryHeader -> GridItemSpan(gridSize)
                                is AppListItem.Divider -> GridItemSpan(gridSize)
                                is AppListItem.App -> GridItemSpan(1)
                            }
                        }
                    ) { index ->
                        when (val item = combinedAppList[index]) {
                            is AppListItem.CategoryHeader -> {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        top = 16.dp,
                                        bottom = 8.dp,
                                        start = 4.dp
                                    )
                                )
                            }

                            is AppListItem.Divider -> {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            }

                            is AppListItem.App -> {
                                AppIconItem(
                                    app = item.appInfo,
                                    onClick = {
                                        launcherViewModel.launchApp(
                                            item.appInfo.packageName,
                                            item.appInfo.activityName
                                        )
                                        onDismiss()
                                    },
                                    showBadge = item.appInfo.getIdentifier() == launcherState.newlyInstalledAppPackage,
                                    onLongClick = { selectedAppIndex = index },
                                    showContextMenu = selectedAppIndex == index,
                                    onDismissMenu = { selectedAppIndex = -1 },
                                    onHide = {
                                        launcherViewModel.hideApp(
                                            item.appInfo.getIdentifier(),
                                            LauncherViewModel.LauncherMode.HANDWRITING,
                                            handwritingState.recognizedText
                                        )
                                        selectedAppIndex = -1
                                    },
                                    onAddShortcut = onAddShortcut?.let { callback ->
                                        { callback(item.appInfo.getIdentifier()) }
                                    },
                                    cornerRadiusPercent = cornerRadius
                                )
                            }
                        }
                    }
                }

                // Top fade edge - only show when scrolled
                if (showFade) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        } else if (handwritingState.recognizedText.isNotEmpty()) {
            val isHiddenApp = launcherState.hiddenApps.any {
                it.label.equals(handwritingState.recognizedText, ignoreCase = true)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No apps found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isHiddenApp) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    val intent =
                                        android.content.Intent(android.content.Intent.ACTION_VIEW)
                                            .apply {
                                                data = android.net.Uri.parse(
                                                    "https://www.google.com/search?q=${
                                                        android.net.Uri.encode(handwritingState.recognizedText)
                                                    }"
                                                )
                                            }
                                    context.startActivity(intent)
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.google_search),
                                    contentDescription = "Search on Google",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Google")
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    val intent =
                                        android.content.Intent(android.content.Intent.ACTION_VIEW)
                                            .apply {
                                                data = android.net.Uri.parse(
                                                    "https://play.google.com/store/search?q=${
                                                        android.net.Uri.encode(handwritingState.recognizedText)
                                                    }&c=apps"
                                                )
                                            }
                                    context.startActivity(intent)
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.play_store),
                                    contentDescription = "Search on Play Store",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play Store")
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Helpers: Smoothing, Glow, Sparkles ---------- */

// Helper function to create smooth Bézier curves
private fun createSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    if (points.size == 1) {
        path.moveTo(points[0].x, points[0].y)
        return path
    }

    path.moveTo(points[0].x, points[0].y)

    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    // Use quadratic Bézier curves for smooth interpolation
    for (i in 1 until points.size) {
        val prevPoint = points[i - 1]
        val currentPoint = points[i]

        // Calculate control point as midpoint
        val controlX = (prevPoint.x + currentPoint.x) / 2
        val controlY = (prevPoint.y + currentPoint.y) / 2

        if (i == 1) {
            path.lineTo(controlX, controlY)
        } else {
            path.quadraticTo(prevPoint.x, prevPoint.y, controlX, controlY)
        }

        if (i == points.size - 1) {
            path.lineTo(currentPoint.x, currentPoint.y)
        }
    }

    return path
}

// Emit sparkles along the path for a magical feel
private fun emitSparkles(
    particles: MutableList<Particle>,
    at: Offset,
    baseStrokeWidth: Float,
    hue: Float
) {
    repeat(Random.nextInt(3, 6)) {
        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val speed = Random.nextFloat() * 220f + 80f
        particles.add(
            Particle(
                position = at,
                velocity = Offset(cos(angle) * speed, sin(angle) * speed - 60f),
                life = 0f,
                maxLife = Random.nextFloat() * 0.5f + 0.35f,
                color = Color.hsv(
                    (hue + (Random.nextFloat() - 0.5f) * 40f + 360f) % 360f,
                    0.9f,
                    1f
                ),
                size = (Random.nextFloat() * 0.4f + 0.25f) * baseStrokeWidth * 0.28f
            )
        )
    }
}