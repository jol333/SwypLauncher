package com.joyal.swyplauncher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val loadAllAppsOnOpen by launcherViewModel.loadAllAppsOnOpen.collectAsState()
    val allAppsRevealed by launcherViewModel.allAppsRevealed.collectAsState()
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
            placeholder = { Text(stringResource(com.joyal.swyplauncher.R.string.search_apps_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(com.joyal.swyplauncher.R.string.search)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { keyboardViewModel.clearSearchQuery() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(com.joyal.swyplauncher.R.string.clear)
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
            } else {
                val appsToShow =
                    if (searchQuery.isEmpty()) launcherState.apps else launcherState.keyboardFilteredApps
                SearchModeResults(
                    query = searchQuery,
                    hideSearchQuery = searchQuery,
                    calculatorResult = launcherState.keyboardCalculatorResult,
                    currencyResult = launcherState.keyboardCurrencyResult,
                    unitResult = launcherState.keyboardUnitResult,
                    timeZoneResult = launcherState.keyboardTimeZoneResult,
                    smartApps = launcherState.keyboardSmartApps,
                    appsToShow = appsToShow,
                    hiddenApps = launcherState.hiddenApps,
                    sortOrder = sortOrder,
                    gridSize = gridSize,
                    cornerRadius = cornerRadius,
                    gridState = gridState,
                    newlyInstalledAppPackage = launcherState.newlyInstalledAppPackage,
                    selectedAppIndex = selectedAppIndex,
                    onSetSelectedIndex = { selectedAppIndex = it },
                    launcherViewModel = launcherViewModel,
                    launcherMode = LauncherViewModel.LauncherMode.KEYBOARD,
                    currencyMode = LauncherViewModel.CurrencyMode.KEYBOARD,
                    onAddShortcut = onAddShortcut,
                    onDismiss = onDismiss,
                    loadAllAppsOnOpen = loadAllAppsOnOpen,
                    allAppsRevealed = allAppsRevealed,
                    onRevealAllApps = { launcherViewModel.revealAllApps() }
                )
            }
        }
    }
}
