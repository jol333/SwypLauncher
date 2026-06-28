package com.joyal.swyplauncher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.AppSortOrder
import com.joyal.swyplauncher.ui.components.AppIconItem
import com.joyal.swyplauncher.ui.components.InteractiveCurrencyConverter
import com.joyal.swyplauncher.ui.components.InteractiveTimeZoneConverter
import com.joyal.swyplauncher.ui.components.InteractiveUnitConverter
import com.joyal.swyplauncher.ui.components.ResultDisplay
import com.joyal.swyplauncher.ui.model.AppListItem
import com.joyal.swyplauncher.ui.state.CurrencyResultState
import com.joyal.swyplauncher.ui.state.TimeZoneResultState
import com.joyal.swyplauncher.ui.state.UnitResultState
import com.joyal.swyplauncher.ui.util.combineAppListsWithHeaders
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel
import com.joyal.swyplauncher.util.CalculatorUtil
import com.joyal.swyplauncher.util.copyToClipboard
import com.joyal.swyplauncher.util.playStoreSearchIntent
import com.joyal.swyplauncher.util.safeStartActivity
import com.joyal.swyplauncher.util.shareText
import com.joyal.swyplauncher.util.webSearchIntent

/**
 * Shared composables for the launcher's search-driven mode screens
 * (handwriting, keyboard, voice) and the index screen.
 *
 * Previously each screen duplicated the app grid, the converter panels and the
 * "no apps found" fallback. They now all funnel through the composables here.
 */

/**
 * The scrollable app grid shown by every mode. Renders category headers, dividers
 * and app icons from a pre-combined [items] list, plus a top fade edge when scrolled.
 *
 * The caller owns [selectedAppIndex] so the long-press context-menu state survives
 * grid recomposition. [centerItems] / [horizontalArrangement] / [contentPadding]
 * cover the small layout differences between the search modes and the index mode.
 */
@Composable
fun AppResultGrid(
    items: List<AppListItem>,
    gridSize: Int,
    cornerRadius: Float,
    gridState: LazyGridState,
    newlyInstalledAppPackage: String?,
    selectedAppIndex: Int,
    onSetSelectedIndex: (Int) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onHideApp: (AppInfo) -> Unit,
    onAddShortcut: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 100.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(24.dp),
    centerItems: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val showFade =
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridSize),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = items.size,
                key = { index ->
                    when (val item = items[index]) {
                        is AppListItem.App -> item.appInfo.getIdentifier()
                        is AppListItem.CategoryHeader -> "header_${item.category}"
                        is AppListItem.Divider -> "divider"
                    }
                },
                span = { index ->
                    when (items[index]) {
                        is AppListItem.CategoryHeader -> GridItemSpan(gridSize)
                        is AppListItem.Divider -> GridItemSpan(gridSize)
                        is AppListItem.App -> GridItemSpan(1)
                    }
                }
            ) { index ->
                when (val item = items[index]) {
                    is AppListItem.CategoryHeader -> CategoryHeaderRow(item.category)

                    is AppListItem.Divider -> HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )

                    is AppListItem.App -> {
                        val appIcon: @Composable () -> Unit = {
                            AppIconItem(
                                app = item.appInfo,
                                onClick = { onLaunchApp(item.appInfo) },
                                showBadge = item.appInfo.getIdentifier() == newlyInstalledAppPackage,
                                onLongClick = { onSetSelectedIndex(index) },
                                showContextMenu = selectedAppIndex == index,
                                onDismissMenu = { onSetSelectedIndex(-1) },
                                onHide = {
                                    onHideApp(item.appInfo)
                                    onSetSelectedIndex(-1)
                                },
                                onAddShortcut = onAddShortcut?.let { callback ->
                                    { callback(item.appInfo.getIdentifier()) }
                                },
                                cornerRadiusPercent = cornerRadius
                            )
                        }
                        if (centerItems) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) { appIcon() }
                        } else {
                            appIcon()
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
                            colors = listOf(Color.Black, Color.Transparent)
                        )
                    )
            )
        }
    }
}

@Composable
private fun CategoryHeaderRow(category: String) {
    Text(
        text = category,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
    )
}

/**
 * The currency conversion panel. Wires the converter directly to
 * [LauncherViewModel.updateCurrencyConversion] for the given [mode], which is the
 * only thing that differed between modes at the call sites.
 */
@Composable
fun CurrencyResultPanel(
    state: CurrencyResultState,
    mode: LauncherViewModel.CurrencyMode,
    launcherViewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        InteractiveCurrencyConverter(
            state = state,
            onAmountChanged = { isSource, amount ->
                launcherViewModel.updateCurrencyConversion(
                    amount = amount,
                    fromCode = state.fromCode,
                    toCode = state.toCode,
                    isSourceChanged = isSource,
                    mode = mode
                )
            },
            onCurrencyChanged = { isSource, newCode ->
                if (isSource) {
                    launcherViewModel.updateCurrencyConversion(
                        amount = state.targetAmount ?: 0.0,
                        fromCode = newCode,
                        toCode = state.toCode,
                        isSourceChanged = false,
                        mode = mode
                    )
                } else {
                    launcherViewModel.updateCurrencyConversion(
                        amount = state.sourceAmount,
                        fromCode = state.fromCode,
                        toCode = newCode,
                        isSourceChanged = true,
                        mode = mode
                    )
                }
            }
        )
    }
}

/**
 * The unit conversion panel. Mirrors [CurrencyResultPanel], wiring the converter to
 * [LauncherViewModel.updateUnitConversion] for the given [mode].
 */
@Composable
fun UnitResultPanel(
    state: UnitResultState,
    mode: LauncherViewModel.CurrencyMode,
    launcherViewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        InteractiveUnitConverter(
            state = state,
            onAmountChanged = { isSource, amount ->
                launcherViewModel.updateUnitConversion(
                    amount = amount,
                    fromId = state.fromId,
                    toId = state.toId,
                    isSourceChanged = isSource,
                    mode = mode
                )
            },
            onUnitChanged = { isSource, newId ->
                if (isSource) {
                    launcherViewModel.updateUnitConversion(
                        amount = state.targetAmount ?: 0.0,
                        fromId = newId,
                        toId = state.toId,
                        isSourceChanged = false,
                        mode = mode
                    )
                } else {
                    launcherViewModel.updateUnitConversion(
                        amount = state.sourceAmount,
                        fromId = state.fromId,
                        toId = newId,
                        isSourceChanged = true,
                        mode = mode
                    )
                }
            }
        )
    }
}

/**
 * The time-zone conversion panel. Mirrors [CurrencyResultPanel], wiring the
 * converter's edit / swap / country-switch callbacks to [LauncherViewModel].
 */
@Composable
fun TimeZoneResultPanel(
    state: TimeZoneResultState,
    mode: LauncherViewModel.CurrencyMode,
    launcherViewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        InteractiveTimeZoneConverter(
            state = state,
            onEditTime = { zoneId, hour, minute, pref ->
                launcherViewModel.updateTimeZoneTime(zoneId, hour, minute, pref, mode)
            },
            onChangeCountry = { isPrimary, iso ->
                launcherViewModel.changeTimeZoneCountry(isPrimary, iso, mode)
            },
            onSwap = { launcherViewModel.swapTimeZones(mode) }
        )
    }
}

/**
 * Fallback shown when a non-empty query matches no apps. Offers web/Play Store search
 * plus copy/share of the query text. Hidden-app matches suppress the action buttons
 * (the app exists, it's just hidden), matching the previous per-screen behaviour.
 */
@Composable
fun NoAppsFoundContent(
    query: String,
    isHiddenApp: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.no_apps_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isHiddenApp) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchActionButton(
                            label = stringResource(R.string.google),
                            contentDescription = stringResource(R.string.search_google_desc),
                            painter = painterResource(id = R.drawable.google_search),
                            onClick = {
                                if (context.safeStartActivity(webSearchIntent(query))) onDismiss()
                            }
                        )
                        SearchActionButton(
                            label = stringResource(R.string.play_store),
                            contentDescription = stringResource(R.string.search_play_store_desc),
                            painter = painterResource(id = R.drawable.play_store),
                            onClick = {
                                if (context.safeStartActivity(playStoreSearchIntent(query))) onDismiss()
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchActionButton(
                            label = stringResource(R.string.copy),
                            contentDescription = stringResource(R.string.copy_to_clipboard_desc),
                            imageVector = Icons.Default.ContentCopy,
                            iconTint = MaterialTheme.colorScheme.onSurface,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            onClick = { context.copyToClipboard(query) }
                        )
                        SearchActionButton(
                            label = stringResource(R.string.share),
                            contentDescription = stringResource(R.string.share_text_desc),
                            imageVector = Icons.Default.Share,
                            iconTint = MaterialTheme.colorScheme.onSurface,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                context.shareText(query, context.getString(R.string.share))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchActionButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    painter: Painter? = null,
    imageVector: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified,
) {
    OutlinedButton(onClick = onClick) {
        when {
            painter != null -> Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )

            imageVector != null -> Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = labelColor)
    }
}

/**
 * The full result area shared by the three search-driven modes (handwriting, keyboard,
 * voice). Picks exactly one of: calculator result, currency panel, unit panel, the app
 * grid, or the no-apps-found fallback — the same if/else ladder each screen used to
 * inline. Loading state stays with the caller (each wraps this differently).
 *
 * @param query the text to display/search with (recognized text, search query or transcription)
 * @param hideSearchQuery the query passed to [LauncherViewModel.hideApp]; differs from [query]
 *   for voice, which hides using cleaned-up spoken text
 * @param appsToShow apps to render alongside [smartApps] (all apps when not searching)
 * @param loadAllAppsOnOpen user preference; when false the full list is deferred on open
 * @param allAppsRevealed whether the user has already revealed the full list this session
 * @param onRevealAllApps invoked by the "Show all apps" button to load the rest of the list
 */
@Composable
fun SearchModeResults(
    query: String,
    hideSearchQuery: String,
    calculatorResult: String?,
    currencyResult: CurrencyResultState?,
    unitResult: UnitResultState?,
    timeZoneResult: TimeZoneResultState?,
    smartApps: List<AppInfo>,
    appsToShow: List<AppInfo>,
    hiddenApps: List<AppInfo>,
    sortOrder: AppSortOrder,
    gridSize: Int,
    cornerRadius: Float,
    gridState: LazyGridState,
    newlyInstalledAppPackage: String?,
    selectedAppIndex: Int,
    onSetSelectedIndex: (Int) -> Unit,
    launcherViewModel: LauncherViewModel,
    launcherMode: LauncherViewModel.LauncherMode,
    currencyMode: LauncherViewModel.CurrencyMode,
    onAddShortcut: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    loadAllAppsOnOpen: Boolean = true,
    allAppsRevealed: Boolean = true,
    onRevealAllApps: () -> Unit = {},
) {
    // When the user has turned off "load all apps on open", defer ALL app content — including the
    // suggested apps row — until they reveal it, so the sheet opens as fast as possible. Only
    // applies while not searching (a query always shows full results).
    val deferFullList = !loadAllAppsOnOpen && !allAppsRevealed && query.isEmpty()

    when {
        calculatorResult != null -> ResultDisplay(
            inputText = CalculatorUtil.normalizeForDisplay(query),
            resultText = "= $calculatorResult",
            clipboardValue = calculatorResult,
            modifier = modifier
        )

        currencyResult != null ->
            CurrencyResultPanel(currencyResult, currencyMode, launcherViewModel, modifier)

        unitResult != null ->
            UnitResultPanel(unitResult, currencyMode, launcherViewModel, modifier)

        timeZoneResult != null ->
            TimeZoneResultPanel(timeZoneResult, currencyMode, launcherViewModel, modifier)

        deferFullList -> DeferredAppsPlaceholder(
            onReveal = onRevealAllApps,
            modifier = modifier
        )

        smartApps.isNotEmpty() || appsToShow.isNotEmpty() -> {
            val combinedAppList = remember(smartApps, appsToShow, sortOrder, query, gridSize) {
                combineAppListsWithHeaders(
                    smartApps,
                    appsToShow,
                    sortOrder,
                    isSearching = query.isNotEmpty(),
                    gridSize = gridSize
                )
            }
            AppResultGrid(
                items = combinedAppList,
                gridSize = gridSize,
                cornerRadius = cornerRadius,
                gridState = gridState,
                newlyInstalledAppPackage = newlyInstalledAppPackage,
                selectedAppIndex = selectedAppIndex,
                onSetSelectedIndex = onSetSelectedIndex,
                onLaunchApp = { app ->
                    launcherViewModel.launchApp(app.packageName, app.activityName)
                    onDismiss()
                },
                onHideApp = { app ->
                    launcherViewModel.hideApp(app.getIdentifier(), launcherMode, hideSearchQuery)
                },
                onAddShortcut = onAddShortcut,
                modifier = modifier
            )
        }

        query.isNotEmpty() -> {
            val isHiddenApp = hiddenApps.any { it.label.equals(query, ignoreCase = true) }
            NoAppsFoundContent(
                query = query,
                isHiddenApp = isHiddenApp,
                onDismiss = onDismiss,
                modifier = modifier
            )
        }
    }
}

/**
 * Placeholder shown when the app list is deferred ("load all apps on open" is off): no apps are
 * composed at all, just the "Show all apps" button. The button lives as the single item of a
 * [LazyColumn] so dragging anywhere — including on the button — scrolls (which the sheet turns
 * into an expand-and-reveal), while a tap reveals directly. Keeps the open as cheap as possible.
 */
@Composable
fun DeferredAppsPlaceholder(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            ShowAllAppsButton(onClick = onReveal)
        }
    }
}

/**
 * Subtle translucent pill shown when the full app list is deferred (the "load all apps on open"
 * preference is off). Tapping it reveals the rest of the list. Kept non-private so the Index mode
 * screen can reuse it.
 */
@Composable
fun ShowAllAppsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = Color.White.copy(alpha = 0.06f),
        contentColor = Color.White.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.55f)
            )
            Text(
                text = stringResource(R.string.show_all_apps),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
