package com.joyal.swyplauncher.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppIconChoice
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.IconPackIcon
import com.joyal.swyplauncher.domain.model.IconPackInfo
import com.joyal.swyplauncher.domain.repository.IconPackRepository
import com.joyal.swyplauncher.ui.components.AmbientBackdrop
import com.joyal.swyplauncher.ui.components.appIconShape
import com.joyal.swyplauncher.ui.components.rememberAmbientColors
import com.joyal.swyplauncher.ui.settings.BentoTypography
import com.joyal.swyplauncher.ui.theme.BentoColors
import com.joyal.swyplauncher.ui.util.AppIconPreview
import com.joyal.swyplauncher.ui.util.logoModel
import com.joyal.swyplauncher.ui.util.rememberSystemBlurSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picks an icon for one app, from any installed icon pack. Opened from the app's long-press menu
 * and drawn over the (blurred) assistant.
 *
 * The icon the user taps moves into the large preview at the top and tints the screen; nothing is
 * saved until they confirm. The first tab gathers what every pack offers for this very app, the
 * other tabs open one pack each to browse or search all of its icons.
 */
@Composable
fun IconPickerScreen(
    app: AppInfo,
    iconPackRepository: IconPackRepository,
    cornerRadius: Float,
    onDismiss: () -> Unit
) {
    val appId = app.getIdentifier()
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val close = {
        keyboard?.hide()
        onDismiss()
    }
    BackHandler(onBack = close)

    val currentChoice = remember(appId) { iconPackRepository.getIconChoices()[appId] }
    val appliedPack = remember { iconPackRepository.getSelectedIconPack() }
    // Tapped, but not saved yet
    var selection by remember(appId) { mutableStateOf<AppIconChoice?>(null) }

    // Null until the installed packs are known (they usually are: the long-press menu asked)
    val packs by produceState(initialValue = iconPackRepository.peekIconPacks()) {
        value = iconPackRepository.getIconPacks()
    }
    // Page 0 is the "for this app" tab, page n browses packs[n - 1]; swiping walks the tabs
    val pagerState = rememberPagerState { 1 + packs.orEmpty().size }
    val scope = rememberCoroutineScope()
    // A search field on the page swiped away shouldn't leave the keyboard behind
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { keyboard?.hide() }
    }

    // Preview models are drawn larger than grid icons, so ask for a sharper render
    val sharpApp = remember(app) { app.copy(iconDensity = DisplayMetrics.DENSITY_XXXHIGH) }
    val originalModel = remember(sharpApp) { AppIconPreview(sharpApp, null) }
    // What the app shows today. If its picked icon can't be loaded any more (pack removed), that
    // is what the applied pack gives it.
    var pickedIconBroken by remember(appId) { mutableStateOf(false) }
    val currentModel: Any = when {
        currentChoice is AppIconChoice.FromPack && !pickedIconBroken -> currentChoice.icon
        currentChoice == AppIconChoice.Original -> originalModel
        else -> remember(sharpApp, appliedPack) { AppIconPreview(sharpApp, appliedPack) }
    }
    val previewModel: Any = when (val picked = selection) {
        null -> currentModel
        AppIconChoice.Original -> originalModel
        is AppIconChoice.FromPack -> picked.icon
    }

    val density = LocalDensity.current
    val ime = WindowInsets.ime
    // The keyboard takes half the screen; the preview shrinks to leave the grid some room
    val imeVisible by remember(density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Nothing behind the picker should react to touches
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        AmbientBackdrop(
            colors = rememberAmbientColors(previewModel),
            modifier = Modifier.fillMaxSize(),
            base = Color.Black.copy(alpha = if (rememberSystemBlurSupported()) 0.74f else 0.9f)
        )

        val header: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = close) {
                    Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (currentChoice != null) {
                    // Back to whatever the applied icon pack (or the app itself) provides
                    TextButton(
                        onClick = {
                            iconPackRepository.setIconChoice(appId, null)
                            close()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.icon_picker_reset),
                            color = BentoColors.TextSecondary,
                            style = BentoTypography.bodyMedium
                        )
                    }
                }
            }
        }
        val preview: @Composable () -> Unit = {
            IconPreview(
                model = previewModel,
                label = app.label,
                compact = imeVisible && !isLandscape,
                cornerRadius = cornerRadius,
                onError = { if (selection == null) pickedIconBroken = true }
            )
        }
        val browser: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier) {
                PackTabs(
                    packs = packs.orEmpty(),
                    // Where the pager is headed, so a tapped tab lights up at once rather than
                    // each tab passed on the way
                    selectedPage = pagerState.targetPage,
                    onOpen = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                    cornerRadius = cornerRadius
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        key = { page -> if (page == 0) "" else packs.orEmpty()[page - 1].packageName },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pack = packs?.getOrNull(page - 1)?.packageName
                        if (pack == null) {
                            ForThisAppGrid(
                                app = app,
                                originalModel = originalModel,
                                packs = packs,
                                iconPackRepository = iconPackRepository,
                                selection = selection,
                                onSelect = { selection = it },
                                cornerRadius = cornerRadius
                            )
                        } else {
                            PackIconGrid(
                                packPackage = pack,
                                iconPackRepository = iconPackRepository,
                                selection = selection,
                                onSelect = { selection = it },
                                cornerRadius = cornerRadius
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = selection != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        ConfirmButton(
                            onClick = {
                                val picked = selection
                                // With no pack applied, the app's own icon is simply "no choice"
                                val choice = picked.takeUnless {
                                    it == AppIconChoice.Original && appliedPack == null
                                }
                                iconPackRepository.setIconChoice(appId, choice)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                close()
                            }
                        )
                    }
                }
            }
        }

        val contentModifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
        if (isLandscape) {
            Row(modifier = contentModifier) {
                Column(
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    header()
                    Spacer(Modifier.weight(1f))
                    preview()
                    Spacer(Modifier.weight(1.4f))
                }
                browser(Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp))
            }
        } else {
            Column(modifier = contentModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                header()
                preview()
                Spacer(Modifier.height(if (imeVisible) 8.dp else 20.dp))
                browser(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** The app's would-be icon, large, glowing in its own colors, with the app's name under it. */
@Composable
private fun IconPreview(
    model: Any,
    label: String,
    compact: Boolean,
    cornerRadius: Float,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val iconSize by animateDpAsState(
        targetValue = if (compact) 56.dp else 96.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "previewSize"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow: the icon again, larger and out of focus. It overflows this box without taking
            // up room, and brings its own margin for the blur to fade out in: a blurred layer is
            // cut off at its bounds, which would show as a faint square around the icon.
            Crossfade(
                targetState = model,
                animationSpec = tween(260),
                modifier = Modifier.matchParentSize().wrapContentSize(unbounded = true),
                label = "iconGlow"
            ) { shown ->
                AsyncImage(
                    model = remember(shown) { previewRequest(context, shown) },
                    contentDescription = null,
                    alpha = 0.6f,
                    modifier = Modifier
                        .blur(30.dp)
                        .padding(GlowMargin)
                        .size(iconSize * 1.3f)
                )
            }
            AnimatedContent(
                targetState = model,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.55f, stiffness = 420f), initialScale = 0.6f) +
                        fadeIn(tween(140)))
                        .togetherWith(scaleOut(tween(140), targetScale = 0.8f) + fadeOut(tween(110)))
                        .using(SizeTransform(clip = false))
                },
                label = "iconPreview"
            ) { shown ->
                AsyncImage(
                    model = remember(shown) { previewRequest(context, shown) },
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    onError = { onError() },
                    modifier = Modifier
                        .size(iconSize)
                        .clip(appIconShape(iconSize, cornerRadius))
                )
            }
        }
        AnimatedVisibility(visible = !compact) {
            Text(
                text = label,
                color = BentoColors.TextPrimary,
                style = BentoTypography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

private val GlowMargin = 64.dp

// Its own cache entry: the grid's copy of a pack icon is rendered at grid size
private fun previewRequest(context: Context, model: Any) =
    ImageRequest.Builder(context).data(model).apply {
        if (model is IconPackIcon) {
            memoryCacheKey("pack_icon_large_${model.packPackage}/${model.drawableName}")
        }
    }.build()

/**
 * "For this app", then one tab per installed pack, each wearing the pack's logo. Tab n opens pager
 * page n; the selected tab is scrolled to the middle of the row so swiping never loses it.
 */
@Composable
private fun PackTabs(
    packs: List<IconPackInfo>,
    selectedPage: Int,
    onOpen: (Int) -> Unit,
    cornerRadius: Float
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedPage) {
        // Bring it on screen first if it isn't, so its size is known
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selectedPage }) {
            listState.animateScrollToItem(selectedPage)
        }
        val info = listState.layoutInfo
        val tab = info.visibleItemsInfo.firstOrNull { it.index == selectedPage } ?: return@LaunchedEffect
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
        listState.animateScrollBy((tab.offset + tab.size / 2 - viewportCenter).toFloat())
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "for_this_app") {
            PackTab(
                selected = selectedPage == 0,
                label = stringResource(R.string.icon_picker_for_this_app),
                onClick = { onOpen(0) }
            ) { tint ->
                Icon(Icons.Outlined.AutoAwesome, null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        itemsIndexed(packs, key = { _, pack -> pack.packageName }) { index, pack ->
            PackTab(
                selected = selectedPage == index + 1,
                label = pack.label,
                onClick = { onOpen(index + 1) }
            ) {
                AsyncImage(
                    model = remember(pack) { pack.logoModel() },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clip(appIconShape(24.dp, cornerRadius))
                )
            }
        }
    }
}

@Composable
private fun PackTab(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    leading: @Composable (tint: Color) -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.08f),
        label = "tabContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
        label = "tabContent"
    )
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        leading(content)
        Text(
            text = label,
            color = content,
            style = BentoTypography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )
    }
}

private class PackSuggestion(val pack: IconPackInfo, val icon: IconPackIcon)

/** The app's own icon, plus the icon each installed pack has made for it. */
@Composable
private fun ForThisAppGrid(
    app: AppInfo,
    originalModel: AppIconPreview,
    packs: List<IconPackInfo>?,
    iconPackRepository: IconPackRepository,
    selection: AppIconChoice?,
    onSelect: (AppIconChoice) -> Unit,
    cornerRadius: Float
) {
    // Null while the packs are being read (a large pack takes a moment the first time)
    val suggestions by produceState<List<PackSuggestion>?>(initialValue = null, app, packs) {
        value = null
        value = packs?.let {
            withContext(Dispatchers.IO) {
                it.mapNotNull { pack ->
                    iconPackRepository.findIcon(pack.packageName, app)
                        ?.let { match -> PackSuggestion(pack, match.icon) }
                }
            }
        }
    }
    // A pack can list an icon for the app that it doesn't actually contain; those drop out
    var unloadable by remember(app) { mutableStateOf(emptySet<IconPackIcon>()) }
    val shown = suggestions?.filter { it.icon !in unloadable }
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().topFadeOnScroll(gridState)
    ) {
        item(key = "original") {
            IconTile(
                model = originalModel,
                contentDescription = stringResource(R.string.icon_picker_original),
                caption = stringResource(R.string.icon_picker_original),
                selected = selection == AppIconChoice.Original,
                iconSize = 60.dp,
                cornerRadius = cornerRadius,
                onClick = { onSelect(AppIconChoice.Original) }
            )
        }
        items(shown.orEmpty(), key = { it.pack.packageName }) { suggestion ->
            IconTile(
                model = suggestion.icon,
                contentDescription = suggestion.pack.label,
                caption = suggestion.pack.label,
                selected = (selection as? AppIconChoice.FromPack)?.icon == suggestion.icon,
                iconSize = 60.dp,
                cornerRadius = cornerRadius,
                onClick = { onSelect(AppIconChoice.FromPack(suggestion.icon)) },
                onError = { unloadable = unloadable + suggestion.icon },
                modifier = Modifier.animateItem()
            )
        }
        if (shown != null && shown.isEmpty()) {
            item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                GridMessage(stringResource(R.string.icon_picker_no_match))
            }
        }
    }
}

/** Every icon of one pack, with a search field on top. */
@Composable
private fun PackIconGrid(
    packPackage: String,
    iconPackRepository: IconPackRepository,
    selection: AppIconChoice?,
    onSelect: (AppIconChoice) -> Unit,
    cornerRadius: Float
) {
    // Null while the pack's icon list is being read
    val icons by produceState<List<IconPackIcon>?>(initialValue = null, packPackage) {
        value = iconPackRepository.getAllIcons(packPackage)
    }
    var query by remember(packPackage) { mutableStateOf("") }
    // Listed by the pack but missing or corrupt in its APK. Found out only when a tile tries to
    // load one (checking thousands up front would take seconds), at which point the tile leaves.
    var unloadable by remember(packPackage) { mutableStateOf(emptySet<String>()) }
    val shown = remember(icons, query, unloadable) {
        val words = query.trim().split(' ', '_').filter { it.isNotEmpty() }
        icons?.filter { icon ->
            icon.drawableName !in unloadable &&
                words.all { icon.drawableName.contains(it, ignoreCase = true) }
        }
    }
    val selectedIcon = (selection as? AppIconChoice.FromPack)?.icon
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(68.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize().topFadeOnScroll(gridState)
    ) {
        if (!icons.isNullOrEmpty()) {
            item(key = "search", span = { GridItemSpan(maxLineSpan) }, contentType = "search") {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)
                )
            }
        }
        when {
            shown == null -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.7f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            shown.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                GridMessage(
                    stringResource(
                        if (icons.isNullOrEmpty()) R.string.icon_picker_pack_empty
                        else R.string.icon_picker_no_results
                    )
                )
            }

            // Names are unique here (the repository de-duplicates), as lazy grid keys must be
            else -> items(shown, key = { it.drawableName }, contentType = { "icon" }) { icon ->
                IconTile(
                    model = icon,
                    contentDescription = icon.drawableName.replace('_', ' '),
                    selected = icon == selectedIcon,
                    iconSize = 48.dp,
                    cornerRadius = cornerRadius,
                    onClick = { onSelect(AppIconChoice.FromPack(icon)) },
                    onError = { unloadable = unloadable + icon.drawableName },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}


private fun Modifier.topFadeOnScroll(state: LazyGridState): Modifier = this
    .graphicsLayer {
        // Offscreen, so the fade cuts into the icons and not into the backdrop behind them. Only
        // while it's needed: an unscrolled grid draws as before.
        compositingStrategy = if (state.canScrollBackward) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        if (!state.canScrollBackward) return@drawWithContent
        val fadePx = TopFade.toPx()
        val strength = if (state.firstVisibleItemIndex > 0) 1f
        else (state.firstVisibleItemScrollOffset / fadePx).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 1f - strength),
                1f to Color.Black,
                endY = fadePx
            ),
            size = Size(size.width, fadePx),
            blendMode = BlendMode.DstIn
        )
    }

private val TopFade = 28.dp

@Composable
private fun IconTile(
    model: Any,
    contentDescription: String,
    selected: Boolean,
    iconSize: Dp,
    cornerRadius: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onError: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(20.dp)
    val fill by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
        label = "tileFill"
    )
    val ring by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent,
        label = "tileRing"
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.5.dp, ring, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onError = { onError?.invoke() },
            modifier = Modifier.size(iconSize).clip(appIconShape(iconSize, cornerRadius))
        )
        if (caption != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = caption,
                color = BentoColors.TextSecondary,
                style = BentoTypography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = BentoColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.icon_picker_search_hint),
                    color = BentoColors.TextMuted,
                    style = BentoTypography.bodyMedium
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = BentoTypography.bodyMedium.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.clear),
                tint = BentoColors.TextMuted,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
                    .padding(5.dp)
            )
        }
    }
}

@Composable
private fun GridMessage(text: String) {
    Text(
        text = text,
        color = BentoColors.TextMuted,
        style = BentoTypography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)
    )
}

@Composable
private fun ConfirmButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.icon_picker_confirm),
            color = Color.Black,
            style = BentoTypography.labelButton
        )
    }
}
