package com.joyal.swyplauncher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.joyal.swyplauncher.ui.components.AppIconItem
import com.joyal.swyplauncher.ui.model.AppListItem
import com.joyal.swyplauncher.ui.util.combineAppListsWithHeaders
import com.joyal.swyplauncher.ui.viewmodel.KeyboardViewModel
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel

@Composable
fun KeyboardModeScreen(
    onDismiss: () -> Unit,
    launcherViewModel: LauncherViewModel,
    keyboardViewModel: KeyboardViewModel = hiltViewModel(),
    isActive: Boolean = true,
    isInitialMode: Boolean = false,
    isBlurEnabled: Boolean = false,
    onAddShortcut: ((String) -> Unit)? = null
) {
    val launcherState by launcherViewModel.uiState.collectAsState()
    val keyboardState by keyboardViewModel.uiState.collectAsState()
    val gridSize by launcherViewModel.gridSize.collectAsState()
    val cornerRadius by launcherViewModel.cornerRadius.collectAsState()
    val autoOpenSingleResult by launcherViewModel.autoOpenSingleResult.collectAsState()
    val sortOrder by launcherViewModel.appSortOrder.collectAsState()
    val searchQuery = keyboardState.searchQuery
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    // Track if we were searching, to detect clear events and reset scroll
    var wasSearching by remember { mutableStateOf(false) }

    // Track menu state outside of LazyGrid items to prevent state loss
    var selectedAppIndex by remember { mutableIntStateOf(-1) }

    // Auto-focus and show keyboard when active
    LaunchedEffect(isActive) {
        if (isActive) {
            // Add delay only when switching modes (not initial mode)
            if (!isInitialMode) {
                kotlinx.coroutines.delay(800)
            }
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    // Filter apps based on search query with debounce
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            kotlinx.coroutines.delay(200) // Debounce by 200ms for keyboard input
            launcherViewModel.filterAppsKeyboard(searchQuery)
        } else {
            launcherViewModel.resetFilterKeyboard()
        }
    }

    // Re-apply filter when loading completes if there's an active search term
    // This handles the case where user searches while apps are still loading
    var wasLoading by remember { mutableStateOf(launcherState.isLoading) }
    LaunchedEffect(launcherState.isLoading, searchQuery) {
        if (wasLoading && !launcherState.isLoading && searchQuery.isNotEmpty()) {
            launcherViewModel.filterAppsKeyboard(searchQuery)
        }
        wasLoading = launcherState.isLoading
    }

    // When query is cleared (transition from non-empty -> empty), reset scroll to top
    LaunchedEffect(searchQuery) {
        if (wasSearching && searchQuery.isEmpty()) {
            // Give time for the list to recompose with full app list before scrolling
            kotlinx.coroutines.delay(50)
            gridState.scrollToItem(0)
        }
        wasSearching = searchQuery.isNotEmpty()
    }

    // Auto-open single result if enabled
    LaunchedEffect(launcherState.keyboardFilteredApps, autoOpenSingleResult, searchQuery) {
        if (autoOpenSingleResult &&
            searchQuery.isNotEmpty() &&
            launcherState.keyboardFilteredApps.size == 1
        ) {
            kotlinx.coroutines.delay(300) // Small delay to avoid accidental opens
            val app = launcherState.keyboardFilteredApps[0]
            launcherViewModel.launchApp(app.packageName, app.activityName)
            onDismiss()
        }
    }

    // Dismiss keyboard when user starts scrolling
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    // Handle back button: clear search query if not empty, otherwise close bottom sheet
    BackHandler(enabled = searchQuery.isNotEmpty()) {
        keyboardViewModel.clearSearchQuery()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { keyboardViewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .border(
                    width = 0.5.dp,
                    color = Color(0xFF363636),
                    shape = MaterialTheme.shapes.extraLarge
                )
                .focusRequester(focusRequester),
            placeholder = { Text("Search apps...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { keyboardViewModel.clearSearchQuery() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isBlurEnabled) Color(0x33111111) else Color(0xFF111111),
                unfocusedContainerColor = if (isBlurEnabled) Color(0x33111111) else Color(0xFF111111),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color(0xFFFFFFFF),
                unfocusedTextColor = Color(0xFFFFFFFF),
                focusedPlaceholderColor = Color(0xFFFFFFFF).copy(alpha = 0.5f),
                unfocusedPlaceholderColor = Color(0xFFFFFFFF).copy(alpha = 0.5f)
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                }
            )
        )

        // App results - show smart 4 apps initially, all on scroll
        Crossfade(
            targetState = launcherState.isLoading,
            modifier = Modifier.weight(1f)
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (launcherState.keyboardCalculatorResult != null) {
                // Show calculator result
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = com.joyal.swyplauncher.util.CalculatorUtil.normalizeForDisplay(
                                searchQuery
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "= ${launcherState.keyboardCalculatorResult}",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (launcherState.keyboardSmartApps.isNotEmpty() || (searchQuery.isNotEmpty() && launcherState.keyboardFilteredApps.isNotEmpty()) || (searchQuery.isEmpty() && launcherState.apps.isNotEmpty())) {
                val appsToShow =
                    if (searchQuery.isEmpty()) launcherState.apps else launcherState.keyboardFilteredApps

                val combinedAppList = remember(
                    launcherState.keyboardSmartApps,
                    appsToShow,
                    sortOrder,
                    searchQuery,
                    gridSize
                ) {
                    combineAppListsWithHeaders(
                        launcherState.keyboardSmartApps,
                        appsToShow,
                        sortOrder,
                        isSearching = searchQuery.isNotEmpty(),
                        gridSize = gridSize
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val showFade =
                        gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridSize),
                        state = gridState,
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
                                                LauncherViewModel.LauncherMode.KEYBOARD,
                                                searchQuery
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
            } else if (searchQuery.isNotEmpty()) {
                val context = LocalContext.current
                val isHiddenApp = launcherState.hiddenApps.any {
                    it.label.equals(searchQuery, ignoreCase = true)
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                                OutlinedButton(
                                    onClick = {
                                        val intent =
                                            android.content.Intent(android.content.Intent.ACTION_VIEW)
                                                .apply {
                                                    data = android.net.Uri.parse(
                                                        "https://www.google.com/search?q=${
                                                            android.net.Uri.encode(searchQuery)
                                                        }"
                                                    )
                                                }
                                        context.startActivity(intent)
                                        onDismiss()
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = com.joyal.swyplauncher.R.drawable.google_search),
                                        contentDescription = "Search on Google",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(
                                        modifier = Modifier.width(
                                            8.dp
                                        )
                                    )
                                    Text("Google")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent =
                                            android.content.Intent(android.content.Intent.ACTION_VIEW)
                                                .apply {
                                                    data = android.net.Uri.parse(
                                                        "https://play.google.com/store/search?q=${
                                                            android.net.Uri.encode(searchQuery)
                                                        }&c=apps"
                                                    )
                                                }
                                        context.startActivity(intent)
                                        onDismiss()
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = com.joyal.swyplauncher.R.drawable.play_store),
                                        contentDescription = "Search on Play Store",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(
                                        modifier = Modifier.width(
                                            8.dp
                                        )
                                    )
                                    Text("Play Store")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}