package com.joyal.swyplauncher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.ui.util.combineAppListsWithHeaders
import com.joyal.swyplauncher.ui.viewmodel.IndexViewModel
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun IndexModeScreen(
    onDismiss: () -> Unit,
    launcherViewModel: LauncherViewModel,
    indexViewModel: IndexViewModel = hiltViewModel(),
    isBlurEnabled: Boolean = false,
    letterSwipeEnabled: Boolean = false,
    onAddShortcut: ((String) -> Unit)? = null
) {
    val launcherState by launcherViewModel.uiState.collectAsState()
    val indexState by indexViewModel.uiState.collectAsState()
    val gridSize by launcherViewModel.gridSize.collectAsState()
    val cornerRadius by launcherViewModel.cornerRadius.collectAsState()
    val sortOrder by launcherViewModel.appSortOrder.collectAsState()
    // Use pre-computed availableLetters from UI state for instant display
    val availableLetters = launcherState.availableLetters

    val selectedLetter = indexState.selectedLetter
    val isExpanded = indexState.isExpanded

    // When Index is the only enabled mode, a horizontal swipe pages between adjacent letters
    // instead of switching tabs. Each letter's page needs its own app list ready so the slide
    // is smooth, so precompute the per-letter smart ordering once (refreshed when apps change,
    // e.g. after hiding an app) and group the filtered apps by first letter.
    var smartAppsByLetter by remember { mutableStateOf<Map<Char, List<AppInfo>>>(emptyMap()) }
    LaunchedEffect(letterSwipeEnabled, launcherState.apps) {
        if (letterSwipeEnabled) {
            smartAppsByLetter = launcherViewModel.computeSmartAppsByLetter()
        }
    }
    val filteredAppsByLetter = remember(letterSwipeEnabled, launcherState.apps) {
        if (letterSwipeEnabled) launcherState.apps.groupBy { it.firstLetter } else emptyMap()
    }

    // Track menu state outside of LazyGrid items to prevent state loss
    var selectedAppIndex by remember { mutableIntStateOf(-1) }

    // Scroll state for the apps grid
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Back handler: first back collapses, second dismisses
    BackHandler(enabled = isExpanded) {
        indexViewModel.reset()
        launcherViewModel.resetFilterIndex()
    }

    // Scroll to top when collapsing from expanded state
    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            // Give composition time to redraw before scrolling
            kotlinx.coroutines.delay(150)
            gridState.scrollToItem(0)
        }
    }

    BackHandler(enabled = !isExpanded) {
        onDismiss()
    }

    // Render content immediately with cached data - no loading spinner
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp)
    ) {
                // Animated index section with seamless transition
                val lazyListState = rememberLazyListState()
                val configuration = LocalConfiguration.current
                val density = LocalDensity.current
                
                // Orientation-aware settings for alphabet grid
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val alphabetGridColumns = if (isLandscape) 10 else 6
                val alphabetItemSize = if (isLandscape) 48.dp else 64.dp
                val alphabetItemSpacing = if (isLandscape) 8.dp else 12.dp

                // Auto-scroll to center selected letter (multi-mode / non-paging path; the
                // single-mode letter pager handles its own centering as you swipe).
                LaunchedEffect(selectedLetter, isExpanded) {
                    if (isExpanded && selectedLetter != null && !letterSwipeEnabled) {
                        val index = availableLetters.indexOf(selectedLetter)
                        if (index >= 0) {
                            val screenWidthPx =
                                with(density) { configuration.screenWidthDp.dp.toPx() }
                            val itemWidthPx = with(density) { 64.dp.toPx() }
                            val paddingPx = with(density) { 16.dp.toPx() }
                            val centerOffset =
                                ((screenWidthPx - paddingPx * 2) / 2 - itemWidthPx / 2).toInt()

                            lazyListState.animateScrollToItem(
                                index = index,
                                scrollOffset = -centerOffset
                            )
                        }
                    }
                }

                if (isExpanded && letterSwipeEnabled && availableLetters.isNotEmpty() && selectedLetter != null) {
                    // Index is the only enabled mode: a horizontal swipe pages between adjacent
                    // letters. The letter strip and the app grid slide together, keeping the
                    // selected letter centered (except at the first/last letter, where it rests at
                    // the edge). Swiping stops at the ends.
                    IndexLetterPager(
                        availableLetters = availableLetters,
                        initialLetter = selectedLetter,
                        smartAppsByLetter = smartAppsByLetter,
                        filteredAppsByLetter = filteredAppsByLetter,
                        sortOrder = sortOrder,
                        gridSize = gridSize,
                        cornerRadius = cornerRadius,
                        isBlurEnabled = isBlurEnabled,
                        newlyInstalledAppPackage = launcherState.newlyInstalledAppPackage,
                        onLetterSettled = { letter ->
                            indexViewModel.setSelectedLetter(letter)
                            launcherViewModel.filterAppsByFirstLetterIndex(letter)
                        },
                        onLaunchApp = { app ->
                            launcherViewModel.launchApp(app.packageName, app.activityName)
                            onDismiss()
                        },
                        onHideApp = { app, letter ->
                            launcherViewModel.hideApp(
                                app.getIdentifier(),
                                LauncherViewModel.LauncherMode.INDEX,
                                letter.toString()
                            )
                        },
                        onCollapse = {
                            indexViewModel.reset()
                            launcherViewModel.resetFilterIndex()
                        },
                        onAddShortcut = onAddShortcut
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                    ) {
                        if (isExpanded) {
                            // Horizontal scrollable row
                            LazyRow(
                                state = lazyListState,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp)
                            ) {
                                items(
                                    items = availableLetters,
                                    key = { letter -> "letter_$letter" }
                                ) { letter ->
                                    LetterIndexItem(
                                        letter = letter,
                                        isSelected = letter == selectedLetter,
                                        isBlurEnabled = isBlurEnabled,
                                        onClick = {
                                            indexViewModel.setSelectedLetter(letter)
                                            launcherViewModel.filterAppsByFirstLetterIndex(letter)
                                        }
                                    )
                                }
                            }
                        } else {
                            // Grid layout - more columns in landscape
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(alphabetGridColumns),
                                contentPadding = PaddingValues(0.dp),
                                horizontalArrangement = Arrangement.spacedBy(alphabetItemSpacing),
                                verticalArrangement = Arrangement.spacedBy(alphabetItemSpacing),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = availableLetters,
                                    key = { letter -> "letter_$letter" }
                                ) { letter ->
                                    LetterIndexItem(
                                        letter = letter,
                                        isSelected = false,
                                        isBlurEnabled = isBlurEnabled,
                                        itemSize = alphabetItemSize,
                                        onClick = {
                                            indexViewModel.setSelectedLetter(letter)
                                            indexViewModel.setExpanded(true)
                                            launcherViewModel.filterAppsByFirstLetterIndex(letter)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Apps list section with animation
                    // Show all apps when in grid view, filtered apps when expanded
                    // Memoize the combined list to avoid recalculation
                    val allItems = remember(
                        isExpanded,
                        launcherState.indexSmartApps,
                        launcherState.indexFilteredApps,
                        launcherState.apps,
                        sortOrder,
                        gridSize
                    ) {
                        if (isExpanded) {
                            combineAppListsWithHeaders(
                                launcherState.indexSmartApps,
                                launcherState.indexFilteredApps,
                                sortOrder,
                                isSearching = true,
                                gridSize = gridSize
                            )
                        } else {
                            combineAppListsWithHeaders(
                                launcherState.indexSmartApps,
                                launcherState.apps,
                                sortOrder,
                                isSearching = false,
                                gridSize = gridSize
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = allItems.isNotEmpty(),
                        enter = fadeIn(animationSpec = tween(500)) +
                                scaleIn(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    initialScale = 0.8f
                                ),
                        exit = fadeOut(animationSpec = tween(300)) +
                                scaleOut(animationSpec = tween(300))
                    ) {
                        AppResultGrid(
                            items = allItems,
                            gridSize = gridSize,
                            cornerRadius = cornerRadius,
                            gridState = gridState,
                            newlyInstalledAppPackage = launcherState.newlyInstalledAppPackage,
                            selectedAppIndex = selectedAppIndex,
                            onSetSelectedIndex = { selectedAppIndex = it },
                            onLaunchApp = { app ->
                                launcherViewModel.launchApp(app.packageName, app.activityName)
                                onDismiss()
                            },
                            onHideApp = { app ->
                                launcherViewModel.hideApp(
                                    app.getIdentifier(),
                                    LauncherViewModel.LauncherMode.INDEX,
                                    selectedLetter?.toString() ?: ""
                                )
                                // Reset to grid view if no apps left after hiding
                                if (isExpanded && launcherState.indexFilteredApps.size == 1) {
                                    indexViewModel.reset()
                                    launcherViewModel.resetFilterIndex()
                                }
                            },
                            onAddShortcut = onAddShortcut,
                            contentPadding = PaddingValues(bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            centerItems = true
                        )
                    }
                }
            }
}


/**
 * Single-mode Index letter pager.
 *
 * Renders the alphabet strip above a [HorizontalPager] of per-letter app grids. The strip and the
 * grid slide together: as the pager moves, the strip is scrolled so the active letter stays
 * horizontally centered. Because the strip is a [LazyRow] that clamps at its content bounds, the
 * first and last letters rest against the edge instead of centering. Tapping a letter animates the
 * pager to it, and the pager naturally stops at both ends (no wrap-around).
 *
 * Each page's content is read from the precomputed [smartAppsByLetter] / [filteredAppsByLetter]
 * maps so neighbouring pages are ready before they slide into view.
 */
@Composable
private fun IndexLetterPager(
    availableLetters: List<Char>,
    initialLetter: Char,
    smartAppsByLetter: Map<Char, List<AppInfo>>,
    filteredAppsByLetter: Map<Char, List<AppInfo>>,
    sortOrder: com.joyal.swyplauncher.domain.repository.AppSortOrder,
    gridSize: Int,
    cornerRadius: Float,
    isBlurEnabled: Boolean,
    newlyInstalledAppPackage: String?,
    onLetterSettled: (Char) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onHideApp: (AppInfo, Char) -> Unit,
    onCollapse: () -> Unit,
    onAddShortcut: ((String) -> Unit)?
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Strip metrics: LetterIndexItem is 64.dp wide with 12.dp gaps between items.
    val itemWidthPx = with(density) { 64.dp.toPx() }
    val itemPitchPx = with(density) { (64.dp + 12.dp).toPx() }

    val pagerState = rememberPagerState(
        initialPage = availableLetters.indexOf(initialLetter).coerceAtLeast(0),
        pageCount = { availableLetters.size }
    )

    // Track the open long-press menu; reset it whenever the active letter changes.
    var selectedAppIndex by remember { mutableIntStateOf(-1) }

    // Publish the active letter to the rest of Index mode (selection state, hide context).
    LaunchedEffect(pagerState, availableLetters) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedAppIndex = -1
            availableLetters.getOrNull(page)?.let(onLetterSettled)
        }
    }

    // Keep the active letter centered in the strip, tracking the pager continuously so the strip
    // slides in lock-step with the grid. LazyRow clamps at its content bounds, so the first/last
    // letter rests against the edge instead of centering. The viewport width is part of the
    // snapshot so the strip centers once the row has been laid out.
    LaunchedEffect(pagerState, lazyListState, availableLetters.size) {
        snapshotFlow {
            lazyListState.layoutInfo.viewportSize.width to
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        }.collect { (viewportWidth, position) ->
            if (viewportWidth <= 0) return@collect
            val centerOffset = viewportWidth / 2f - itemWidthPx / 2f
            val index = position.toInt().coerceIn(0, (availableLetters.size - 1).coerceAtLeast(0))
            val fraction = position - index
            lazyListState.scrollToItem(index, (-centerOffset + fraction * itemPitchPx).roundToInt())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // The strip stays manually scrollable so any letter can be reached and tapped. The
            // centering effect below only re-runs when the pager moves (swipe the grid or tap a
            // letter), so scrolling the strip by hand isn't fought.
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            itemsIndexed(
                items = availableLetters,
                key = { _, letter -> "letter_$letter" }
            ) { index, letter ->
                LetterIndexItem(
                    letter = letter,
                    isSelected = index == pagerState.currentPage,
                    isBlurEnabled = isBlurEnabled,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // Pre-compose the neighbouring letters so they're ready as they slide in.
            beyondViewportPageCount = 1,
            key = { page -> availableLetters[page] }
        ) { page ->
            val letter = availableLetters[page]
            val pageItems = remember(letter, smartAppsByLetter, filteredAppsByLetter, sortOrder, gridSize) {
                combineAppListsWithHeaders(
                    smartAppsByLetter[letter] ?: emptyList(),
                    filteredAppsByLetter[letter] ?: emptyList(),
                    sortOrder,
                    isSearching = true,
                    gridSize = gridSize
                )
            }
            val pageGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            AppResultGrid(
                items = pageItems,
                gridSize = gridSize,
                cornerRadius = cornerRadius,
                gridState = pageGridState,
                newlyInstalledAppPackage = newlyInstalledAppPackage,
                selectedAppIndex = if (page == pagerState.currentPage) selectedAppIndex else -1,
                onSetSelectedIndex = { selectedAppIndex = it },
                onLaunchApp = onLaunchApp,
                onHideApp = { app ->
                    // Hiding the last app for this letter empties the page; fall back to the grid.
                    val wasLastForLetter = (filteredAppsByLetter[letter]?.size ?: 0) <= 1
                    onHideApp(app, letter)
                    if (wasLastForLetter) onCollapse()
                },
                onAddShortcut = onAddShortcut,
                contentPadding = PaddingValues(bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                centerItems = true
            )
        }
    }
}


@Composable
fun LetterIndexItem(
    letter: Char,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = false,
    itemSize: androidx.compose.ui.unit.Dp = 64.dp
) {
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0x0AFFFFFF),
            Color(0x0FFFFFFF)
        )
    )

    val selectedGradientBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0x3AFFFFFF),
            Color(0x4FFFFFFF)
        )
    )

    val backgroundColor = if (isBlurEnabled) {
        if (isSelected) Color(0x331E1E1E) else Color(0x330E0E0E)
    } else {
        if (isSelected) Color(0xFF1E1E1E) else Color(0xFF0E0E0E)
    }
    val borderBrush = if (isSelected) selectedGradientBrush else gradientBrush

    // Adjust text style based on item size
    val textStyle = if (itemSize < 64.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(itemSize)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = letter.toString(),
                style = textStyle,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isSelected) Color(0xFFFFFFFF) else Color(0xFFE0E0E0)
            )
        }
    }
}