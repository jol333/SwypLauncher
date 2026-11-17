package com.joyal.swyplauncher.ui.screens

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.IconPackInfo
import com.joyal.swyplauncher.domain.repository.IconPackRepository
import com.joyal.swyplauncher.ui.components.AmbientBackdrop
import com.joyal.swyplauncher.ui.components.DefaultAmbientColors
import com.joyal.swyplauncher.ui.components.appIconShape
import com.joyal.swyplauncher.ui.components.rememberAmbientColors
import com.joyal.swyplauncher.ui.settings.BentoTypography
import com.joyal.swyplauncher.ui.theme.BentoColors
import com.joyal.swyplauncher.ui.util.AppIconFetcher
import com.joyal.swyplauncher.ui.util.AppIconPreview
import com.joyal.swyplauncher.ui.util.logoModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

// The rail's logos, and the space between them (also how far the stage's swipe moves the rail)
private val RailLogoSize = 64.dp
private val RailPageSpacing = 16.dp

// How long the rail must rest on a pack before the stage dresses in it
private const val STAGE_FOLLOW_DELAY_MS = 120L

// Head start the stage's own icons get before the neighbouring packs are rendered
private const val PREFETCH_DELAY_MS = 500L

/**
 * The icon pack chooser opened from settings. The user's own apps sit on a stage dressed in the
 * pack that is centered in the rail below it, so swiping through the rail tries packs on; the
 * screen's light takes its colors from that pack. Nothing changes until Apply.
 *
 * @param allApps every installed app, for the "covers N of your apps" figure
 * @param previewApps the apps shown on the stage, most relevant first
 */
@Composable
fun IconPackScreen(
    iconPackRepository: IconPackRepository,
    allApps: List<AppInfo>,
    previewApps: List<AppInfo>,
    gridSize: Int,
    cornerRadius: Float,
    onBack: () -> Unit
) {
    val packs by produceState(initialValue = iconPackRepository.peekIconPacks()) {
        value = iconPackRepository.getIconPacks()
    }
    when (val loaded = packs) {
        // Only on a cold process, for the moment the pack query takes
        null -> AmbientBackdrop(DefaultAmbientColors, Modifier.fillMaxSize())
        else -> IconPackChooser(
            packs = loaded,
            iconPackRepository = iconPackRepository,
            allApps = allApps,
            previewApps = previewApps,
            gridSize = gridSize,
            cornerRadius = cornerRadius,
            onBack = onBack
        )
    }
}

@Composable
private fun IconPackChooser(
    packs: List<IconPackInfo>,
    iconPackRepository: IconPackRepository,
    allApps: List<AppInfo>,
    previewApps: List<AppInfo>,
    gridSize: Int,
    cornerRadius: Float,
    onBack: () -> Unit
) {
    // null stands for "no pack": the apps' own icons
    val options = remember(packs) { listOf<IconPackInfo?>(null) + packs }
    var applied by remember {
        // A pack that has been uninstalled since counts as none
        val selected = iconPackRepository.getSelectedIconPack()
        mutableStateOf(selected?.takeIf { pkg -> packs.any { it.packageName == pkg } })
    }
    val pagerState = rememberPagerState(
        initialPage = options.indexOfFirst { it?.packageName == applied }.coerceAtLeast(0),
        pageCount = { options.size }
    )
    val focusedIndex = pagerState.currentPage.coerceIn(0, options.lastIndex)
    val focused = options[focusedIndex]
    val isApplied = focused?.packageName == applied

    val view = LocalView.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        }
    }

    // The stage, its light and the coverage figure trail the rail: they follow once it rests on a
    // pack, so a fling across several packs doesn't render the icons of every pack it passes.
    var stagedPackage by remember { mutableStateOf(focused?.packageName) }
    LaunchedEffect(pagerState, options) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            delay(STAGE_FOLLOW_DELAY_MS)
            stagedPackage = options.getOrNull(page)?.packageName
        }
    }
    val stagedOption = options.firstOrNull { it?.packageName == stagedPackage }

    // Once the stage has its icons, the packs either side of it are rendered in the background,
    // one icon at a time, so swiping on to one of them dresses the stage at once.
    val context = LocalContext.current
    val maxRows = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 5
    LaunchedEffect(stagedPackage, previewApps) {
        if (previewApps.isEmpty()) return@LaunchedEffect
        delay(PREFETCH_DELAY_MS)
        val index = options.indexOfFirst { it?.packageName == stagedPackage }
        val imageLoader = SingletonImageLoader.get(context)
        withContext(Dispatchers.IO) {
            for (neighbor in listOf(index + 1, index - 1)) {
                if (neighbor !in options.indices) continue
                val pack = options[neighbor]?.packageName
                for (app in previewApps.take(gridSize * maxRows)) {
                    // Same model (and so the same cache entry) as the stage's own requests
                    imageLoader.execute(
                        ImageRequest.Builder(context).data(AppIconPreview(app, pack)).build()
                    )
                }
            }
        }
    }

    // Icons the user picked for single apps would silently survive (or silently vanish) under a
    // new pack, so applying asks which of the two they want.
    var askAboutPickedIcons by remember { mutableStateOf(false) }
    fun applyFocused(clearChoices: Boolean) {
        iconPackRepository.setSelectedIconPack(focused?.packageName, clearChoices)
        applied = focused?.packageName
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    val ambient = rememberAmbientColors(stagedOption?.logoModel())
    val contentBlur by animateDpAsState(
        targetValue = if (askAboutPickedIcons) 20.dp else 0.dp,
        animationSpec = tween(300),
        label = "contentBlur"
    )
    val isLandscape = maxRows == 3

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackdrop(ambient, Modifier.fillMaxSize())

        val contentModifier = Modifier
            .fillMaxSize()
            // The sheet below gets the attention; everything behind it goes out of focus
            .then(if (contentBlur > 0.dp) Modifier.blur(contentBlur) else Modifier)
            .windowInsetsPadding(WindowInsets.safeDrawing)

        val stageSwipe = Modifier.swipesRail(pagerState)
        val stage: @Composable (Modifier) -> Unit = { modifier ->
            IconStage(
                apps = previewApps,
                packPackage = stagedPackage,
                columns = gridSize,
                maxRows = maxRows,
                cornerRadius = cornerRadius,
                modifier = modifier.then(stageSwipe)
            )
        }
        val controls: @Composable () -> Unit = {
            PackTitle(
                options = options,
                focusedIndex = focusedIndex,
                coveragePackage = stagedPackage,
                allApps = allApps,
                iconPackRepository = iconPackRepository,
                accent = ambient.first()
            )
            Spacer(Modifier.height(20.dp))
            PackRail(
                options = options,
                pagerState = pagerState,
                appliedPackage = applied,
                cornerRadius = cornerRadius
            )
            Spacer(Modifier.height(20.dp))
            ApplyButton(
                isApplied = isApplied,
                onClick = {
                    if (iconPackRepository.getIconChoices().isNotEmpty()) {
                        askAboutPickedIcons = true
                    } else {
                        applyFocused(clearChoices = false)
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp)
            )
        }

        if (isLandscape) {
            Row(modifier = contentModifier) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ChooserTopBar(onBack)
                    stage(Modifier.weight(1f).fillMaxWidth())
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) { controls() }
            }
        } else {
            Column(modifier = contentModifier) {
                ChooserTopBar(onBack)
                stage(Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                controls()
            }
        }
    }

    if (askAboutPickedIcons) {
        val pickedApps = remember(allApps) {
            val choices = iconPackRepository.getIconChoices()
            allApps.filter { it.getIdentifier() in choices }
        }
        KeepPickedIconsSheet(
            pickedApps = pickedApps,
            // Until the app list has loaded, the stored choices are the best count there is
            pickedCount = pickedApps.size.takeIf { it > 0 } ?: iconPackRepository.getIconChoices().size,
            cornerRadius = cornerRadius,
            onKeep = {
                askAboutPickedIcons = false
                applyFocused(clearChoices = false)
            },
            onReplace = {
                askAboutPickedIcons = false
                applyFocused(clearChoices = true)
            },
            onDismiss = { askAboutPickedIcons = false }
        )
    }
}

@Composable
private fun ChooserTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.icon_pack_screen_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

/**
 * The preview: [apps] in the launcher's own grid ([columns] wide, the user's corner radius),
 * drawn with [packPackage], in as many rows as fit (up to [maxRows]). Behind the glass panel the
 * same icons are repeated, enlarged and blurred, so the pack's colors bloom out from under it.
 */
@Composable
private fun IconStage(
    apps: List<AppInfo>,
    packPackage: String?,
    columns: Int,
    maxRows: Int,
    cornerRadius: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val spacing = 18.dp
        val panelPadding = 26.dp
        // Up to the size icons have in the launcher, smaller if the grid is too wide for that
        val fitWidth = (maxWidth - panelPadding * 2 - spacing * (columns - 1)) / columns
        val iconSize = minOf(fitWidth, 60.dp).coerceAtLeast(24.dp)
        val fitRows = ((maxHeight - panelPadding * 2 + spacing) / (iconSize + spacing)).toInt()
            .coerceIn(1, maxRows)
        // No empty rows when there are only a few apps; the full grid (as placeholders) while
        // the apps are still loading
        val rows = if (apps.isEmpty()) fitRows else minOf(fitRows, (apps.size + columns - 1) / columns)
        val shown = apps.take(columns * rows)
        val shape = appIconShape(iconSize, cornerRadius)
        val panelShape = RoundedCornerShape(36.dp)
        // Room inside the bloom's own layer for the blur to fade out in, so it has no edge
        val bloomMargin = 72.dp
        val gridWidth = iconSize * columns + spacing * (columns - 1)
        val gridHeight = iconSize * rows + spacing * (rows - 1)
        // The panel's grid, recorded once and drawn twice: in the panel, and in the bloom
        val gridLayer = rememberGraphicsLayer()

        Box(
            modifier = Modifier
                // May spill over the stage's bounds; nothing here clips it
                .wrapContentSize(unbounded = true)
                .size(gridWidth + bloomMargin * 2, gridHeight + bloomMargin * 2)
                .graphicsLayer {
                    scaleX = 1.1f
                    scaleY = 1.1f
                    alpha = 0.55f
                }
                .blur(34.dp)
                // The same icons as the panel's, not a second set to load and animate
                .drawBehind {
                    translate(bloomMargin.toPx(), bloomMargin.toPx()) { drawLayer(gridLayer) }
                }
        )
        Box(
            modifier = Modifier
                .clip(panelShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), panelShape)
                .padding(panelPadding)
        ) {
            StageGrid(
                apps = shown,
                packPackage = packPackage,
                columns = columns,
                rows = rows,
                iconSize = iconSize,
                spacing = spacing,
                shape = shape,
                modifier = Modifier.drawWithContent {
                    gridLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(gridLayer)
                }
            )
        }
    }
}

/**
 * Lets the stage be swiped like the rail below it: dragging across a third of the stage moves
 * the rail by one pack, and letting go settles on the pack the swipe was heading for.
 */
@Composable
private fun Modifier.swipesRail(pagerState: PagerState): Modifier {
    val density = LocalDensity.current
    // Later packs lie to the left of the finger in LTR, to the right in RTL
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    val pageStride = with(density) { (RailLogoSize + RailPageSpacing).toPx() }
    val flingVelocity = with(density) { 400.dp.toPx() }
    // Written on layout, read only while dragging: never recomposes anything
    var width by remember { mutableIntStateOf(0) }
    val dragState = rememberDraggableState { delta ->
        val packWidth = if (width > 0) width / 3f else pageStride
        pagerState.dispatchRawDelta(delta * forward * pageStride / packWidth)
    }
    return this
        .onSizeChanged { width = it.width }
        .draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            // Catches the rail mid-settle instead of fighting it
            onDragStarted = { pagerState.stopScroll() },
            onDragStopped = { velocity ->
                val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val towardsLater = velocity * forward
                val target = when {
                    towardsLater > flingVelocity -> floor(position).toInt() + 1
                    towardsLater < -flingVelocity -> ceil(position).toInt() - 1
                    else -> position.roundToInt()
                }
                pagerState.animateScrollToPage(target.coerceIn(0, pagerState.pageCount - 1))
            }
        )
}

@Composable
private fun StageGrid(
    apps: List<AppInfo>,
    packPackage: String?,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    spacing: Dp,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                for (column in 0 until columns) {
                    val app = apps.getOrNull(row * columns + column)
                    if (app != null) {
                        StageIcon(
                            app = app,
                            packPackage = packPackage,
                            // The change of clothes ripples from the top-left corner
                            delayMillis = (row + column) * 35,
                            modifier = Modifier.size(iconSize).clip(shape)
                        )
                    } else {
                        // Keeps the grid's shape while the apps load (or when there are few)
                        Spacer(Modifier.size(iconSize))
                    }
                }
            }
        }
    }
}

@Composable
private fun StageIcon(
    app: AppInfo,
    packPackage: String?,
    delayMillis: Int,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        // AnimatedContent keys must not be null
        targetState = packPackage.orEmpty(),
        transitionSpec = {
            (scaleIn(tween(340, delayMillis, EaseOutBack), initialScale = 0.55f) +
                fadeIn(tween(180, delayMillis)))
                .togetherWith(
                    scaleOut(tween(160, delayMillis / 2), targetScale = 0.7f) +
                        fadeOut(tween(140, delayMillis / 2))
                )
                .using(SizeTransform(clip = false))
        },
        label = "stageIcon"
    ) { pack ->
        val model = remember(app, pack) { AppIconPreview(app, pack.ifEmpty { null }) }
        AsyncImage(
            model = model,
            contentDescription = app.label,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

/**
 * The focused pack's name and how many of the user's apps it has icons for. The count is only
 * worked out for [coveragePackage], the pack the rail has come to rest on (reading a pack's
 * appfilter for every pack swiped past would hold up the stage's icons).
 */
@Composable
private fun PackTitle(
    options: List<IconPackInfo?>,
    focusedIndex: Int,
    coveragePackage: String?,
    allApps: List<AppInfo>,
    iconPackRepository: IconPackRepository,
    accent: Color
) {
    val focusedPackage = options[focusedIndex]?.packageName
    val themedCount by produceState<Int?>(initialValue = null, coveragePackage, allApps) {
        value = null
        value = coveragePackage?.let { iconPackRepository.countThemedApps(it, allApps) }
    }
    val coverage = themedCount?.takeIf { allApps.isNotEmpty() && coveragePackage == focusedPackage }
    val coverageFraction by animateFloatAsState(
        targetValue = coverage?.let { it.toFloat() / allApps.size } ?: 0f,
        animationSpec = tween(500),
        label = "coverage"
    )
    val barColor by animateColorAsState(accent, tween(700), label = "coverageColor")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = focusedIndex,
            transitionSpec = {
                // The name follows the direction the rail was swiped in
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(260)) { it / 5 * direction } + fadeIn(tween(200)))
                    .togetherWith(
                        slideOutHorizontally(tween(200)) { -it / 5 * direction } + fadeOut(tween(120))
                    )
                    .using(SizeTransform(clip = false))
            },
            label = "packName"
        ) { index ->
            Text(
                text = options.getOrNull(index)?.label ?: stringResource(R.string.icon_pack_default),
                color = BentoColors.TextPrimary,
                style = BentoTypography.titleLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                focusedPackage == null -> stringResource(R.string.icon_pack_default_desc)
                coverage != null ->
                    stringResource(R.string.icon_pack_coverage, coverage, allApps.size)
                // Holds the line while the pack is being read
                else -> " "
            },
            color = BentoColors.TextSecondary,
            style = BentoTypography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        // Coverage again, as a bar; stays in place (empty) for the "no pack" option
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(3.dp)
                .graphicsLayer { alpha = if (focusedPackage == null) 0f else 1f }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(coverageFraction.coerceIn(0f, 1f))
                    .background(barColor)
            )
        }
    }
}

/**
 * The selector: every pack's own logo in a row that snaps to its center. Whatever is centered is
 * what the stage previews; the pack in use carries a check.
 */
@Composable
private fun PackRail(
    options: List<IconPackInfo?>,
    pagerState: PagerState,
    appliedPackage: String?,
    cornerRadius: Float
) {
    val scope = rememberCoroutineScope()
    val logoSize = RailLogoSize
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(logoSize),
            pageSpacing = RailPageSpacing,
            // Centers the snapped page
            contentPadding = PaddingValues(horizontal = ((maxWidth - logoSize) / 2).coerceAtLeast(0.dp)),
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(options.size)
            ),
            modifier = Modifier.fillMaxWidth().height(logoSize + 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            val option = options[page]
            val label = option?.label ?: stringResource(R.string.icon_pack_default)
            Box(
                modifier = Modifier
                    .size(logoSize)
                    // Read in the draw phase: sliding the rail repaints, it doesn't recompose
                    .graphicsLayer {
                        val distance = pagerState.getOffsetDistanceInPages(page).absoluteValue
                            .coerceIn(0f, 1f)
                        val scale = lerp(1f, 0.7f, distance)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1f, 0.5f, distance)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = label
                    ) { scope.launch { pagerState.animateScrollToPage(page) } }
            ) {
                val shape = appIconShape(logoSize, cornerRadius)
                if (option == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = remember(option) { option.logoModel() },
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(shape)
                    )
                }
                if (option?.packageName == appliedPackage) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(BentoColors.BackgroundDark)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(BentoColors.AccentGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.icon_pack_in_use),
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplyButton(
    isApplied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (isApplied) Color.White.copy(alpha = 0.08f) else Color.White,
        animationSpec = tween(250),
        label = "applyContainer"
    )
    val content by animateColorAsState(
        targetValue = if (isApplied) Color.White.copy(alpha = 0.7f) else Color.Black,
        animationSpec = tween(250),
        label = "applyContent"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(container)
            .clickable(enabled = !isApplied, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isApplied) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = stringResource(if (isApplied) R.string.icon_pack_in_use else R.string.icon_pack_apply),
            color = content,
            style = BentoTypography.labelButton
        )
    }
}

/**
 * Asked when a pack is applied while some apps carry individually picked icons: keep those, or
 * let the new pack take over everywhere. Dismissing it applies nothing.
 */
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet
@Composable
private fun KeepPickedIconsSheet(
    pickedApps: List<AppInfo>,
    pickedCount: Int,
    cornerRadius: Float,
    onKeep: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BentoColors.CardBackground,
        dragHandle = null
    ) {
        KeepPickedIconsContent(pickedApps, pickedCount, cornerRadius, onKeep, onReplace)
    }
}

/** What the sheet asks, and the two ways to answer. */
@Composable
private fun KeepPickedIconsContent(
    pickedApps: List<AppInfo>,
    pickedCount: Int,
    cornerRadius: Float,
    onKeep: () -> Unit,
    onReplace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BentoColors.TextLabel.copy(alpha = 0.5f))
        )
        Spacer(Modifier.height(12.dp))

        if (pickedApps.isNotEmpty()) {
            PickedIconsRow(pickedApps, cornerRadius)
            Spacer(Modifier.height(20.dp))
        }

        Text(
            text = stringResource(R.string.icon_pack_keep_picked_title),
            color = BentoColors.TextPrimary,
            style = BentoTypography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = pluralStringResource(R.plurals.icon_pack_keep_picked_message, pickedCount, pickedCount),
            color = BentoColors.TextSecondary,
            style = BentoTypography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .clickable(onClick = onKeep),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.icon_pack_keep_picked),
                color = Color.Black,
                style = BentoTypography.labelButton
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
                .clickable(onClick = onReplace),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.icon_pack_replace_picked),
                color = BentoColors.TextPrimary,
                style = BentoTypography.labelButton
            )
        }
    }
}

/** The apps in question, as they look right now, overlapping like a hand of cards. */
@Composable
private fun PickedIconsRow(apps: List<AppInfo>, cornerRadius: Float) {
    val context = LocalContext.current
    val iconSize = 52.dp
    val maxIcons = 5
    val shown = apps.take(if (apps.size > maxIcons) maxIcons - 1 else maxIcons)
    val more = apps.size - shown.size
    val shape = appIconShape(iconSize, cornerRadius)

    Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
        shown.forEachIndexed { index, app ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(app)
                    .memoryCacheKey(AppIconFetcher.memoryCacheKey(app))
                    .build(),
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .zIndex(index.toFloat())
                    .size(iconSize)
                    // A rim in the sheet's color separates each icon from the one under it
                    .background(BentoColors.CardBackground, shape)
                    .padding(3.dp)
                    .clip(appIconShape(iconSize - 6.dp, cornerRadius))
            )
        }
        if (more > 0) {
            Box(
                modifier = Modifier
                    .zIndex(shown.size.toFloat())
                    .size(iconSize)
                    .background(BentoColors.CardBackground, shape)
                    .padding(3.dp)
                    .clip(appIconShape(iconSize - 6.dp, cornerRadius))
                    .background(BentoColors.CardBackgroundLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$more",
                    color = BentoColors.TextPrimary,
                    style = BentoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}
