package com.joyal.swyplauncher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joyal.swyplauncher.ui.components.AppContextMenu
import com.joyal.swyplauncher.ui.components.AppIconItem
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsScreen(
    onDismiss: () -> Unit,
    launcherViewModel: LauncherViewModel
) {
    val launcherState by launcherViewModel.uiState.collectAsState()
    val gridSize by launcherViewModel.gridSize.collectAsState()
    val cornerRadius by launcherViewModel.cornerRadius.collectAsState()
    var selectedAppIdentifier by remember { mutableStateOf<String?>(null) }
    var isInitialLoad by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        launcherViewModel.getHiddenApps()
    }

    // Track when the initial load completes
    LaunchedEffect(launcherState.hiddenApps) {
        if (isInitialLoad) {
            isInitialLoad = false
        }
    }

    // Handle back press to dismiss hidden apps screen
    BackHandler(onBack = onDismiss)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hidden Apps") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        when {
            isInitialLoad -> {
                // Show loading indicator during initial load
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            launcherState.hiddenApps.isEmpty() -> {
                // Show "No hidden apps" message when list is empty after loading
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hidden apps",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            else -> {
                // Show the grid of hidden apps
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridSize),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    itemsIndexed(
                        items = launcherState.hiddenApps,
                        key = { _, app -> app.getIdentifier() }
                    ) { _, app ->
                        Box {
                            AppIconItem(
                                app = app,
                                onClick = {
                                    launcherViewModel.launchApp(
                                        app.packageName,
                                        app.activityName
                                    )
                                    onDismiss()
                                },
                                onLongClick = {
                                    selectedAppIdentifier = app.getIdentifier()
                                },
                                cornerRadiusPercent = cornerRadius
                            )

                            if (selectedAppIdentifier == app.getIdentifier()) {
                                AppContextMenu(
                                    app = app,
                                    onDismiss = { selectedAppIdentifier = null },
                                    onUnhide = {
                                        launcherViewModel.unhideApp(app.getIdentifier())
                                        launcherViewModel.getHiddenApps()
                                        selectedAppIdentifier = null
                                    },
                                    showHideOption = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}