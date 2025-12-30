package com.joyal.swyplauncher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.joyal.swyplauncher.ui.components.AppIconItem
import com.joyal.swyplauncher.ui.model.AppListItem
import com.joyal.swyplauncher.ui.util.combineAppListsWithHeaders
import com.joyal.swyplauncher.ui.viewmodel.IndexViewModel
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel

@Composable
fun IndexModeScreen(
    onDismiss: () -> Unit,
    launcherViewModel: LauncherViewModel,
    indexViewModel: IndexViewModel = hiltViewModel(),
    isBlurEnabled: Boolean = false,
    onAddShortcut: ((String) -> Unit)? = null
) {
    val launcherState by launcherViewModel.uiState.collectAsState()
    val indexState by indexViewModel.uiState.collectAsState()
    val gridSize by launcherViewModel.gridSize.collectAsState()
    val cornerRadius by launcherViewModel.cornerRadius.collectAsState()
    val sortOrder by launcherViewModel.appSortOrder.collectAsState()
    val availableLetters = remember(launcherState.apps) {
        launcherViewModel.getAvailableLetters()
    }

    val selectedLetter = indexState.selectedLetter
    val isExpanded = indexState.isExpanded

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

    Crossfade(
        targetState = launcherState.isLoading,
        animationSpec = tween(300)
    ) { loading ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
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

                // Auto-scroll to center selected letter
                LaunchedEffect(selectedLetter, isExpanded) {
                    if (isExpanded && selectedLetter != null) {
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        val showFade =
                            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridSize),
                            state = gridState,
                            contentPadding = PaddingValues(bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = allItems.size,
                                key = { index ->
                                    when (val item = allItems[index]) {
                                        is AppListItem.App -> item.appInfo.getIdentifier()
                                        is AppListItem.CategoryHeader -> "header_${item.category}"
                                        is AppListItem.Divider -> "divider"
                                    }
                                },
                                span = { index ->
                                    when (allItems[index]) {
                                        is AppListItem.CategoryHeader -> GridItemSpan(gridSize)
                                        is AppListItem.Divider -> GridItemSpan(gridSize)
                                        is AppListItem.App -> GridItemSpan(1)
                                    }
                                }
                            ) { index ->
                                when (val item = allItems[index]) {
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
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
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
                                                        LauncherViewModel.LauncherMode.INDEX,
                                                        selectedLetter?.toString() ?: ""
                                                    )
                                                    selectedAppIndex = -1
                                                    // Reset to grid view if no apps left after hiding
                                                    if (isExpanded && launcherState.indexFilteredApps.size == 1) {
                                                        indexViewModel.reset()
                                                        launcherViewModel.resetFilterIndex()
                                                    }
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
                }
            }
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