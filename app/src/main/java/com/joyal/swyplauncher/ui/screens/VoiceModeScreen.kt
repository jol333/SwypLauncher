package com.joyal.swyplauncher.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.joyal.swyplauncher.domain.model.RecognitionResult
import com.joyal.swyplauncher.ui.components.AppIconItem
import com.joyal.swyplauncher.ui.model.AppListItem
import com.joyal.swyplauncher.ui.util.combineAppListsWithHeaders
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel
import com.joyal.swyplauncher.ui.viewmodel.VoiceViewModel
import java.util.Locale

@Composable
fun VoiceModeScreen(
    onDismiss: () -> Unit,
    launcherViewModel: LauncherViewModel,
    voiceViewModel: VoiceViewModel = hiltViewModel(),
    isActive: Boolean = true,
    onAddShortcut: ((String) -> Unit)? = null
) {
    val voiceState by voiceViewModel.uiState.collectAsState()
    val launcherState by launcherViewModel.uiState.collectAsState()
    val gridSize by launcherViewModel.gridSize.collectAsState()
    val cornerRadius by launcherViewModel.cornerRadius.collectAsState()
    val sortOrder by launcherViewModel.appSortOrder.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // Track menu state outside of LazyGrid items to prevent state loss
    var selectedAppIndex by remember { mutableIntStateOf(-1) }

    // Grid state for scrolling
    val voiceGridState = rememberLazyGridState()

    // Permission state
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var autoLaunched by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        permanentlyDenied = !isGranted && activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.RECORD_AUDIO
            )
        } ?: false

        if (isGranted) {
            voiceViewModel.startListening()
        }
    }

    fun refreshPermission() {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        if (granted) permanentlyDenied = false
    }

    // Clean up spoken text (remove "open/launch/start/run", trailing "app", punctuation)
    fun cleanupSpokenText(input: String): String {
        return input.trim()
            .let { s ->
                listOf("open ", "launch ", "start ", "run ").fold(s) { acc, prefix ->
                    if (acc.startsWith(
                            prefix,
                            ignoreCase = true
                        )
                    ) acc.substring(prefix.length) else acc
                }
            }
            .replace(Regex("\\bapp\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[\\p{Punct}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // Normalize strings for exact match compare (case-insensitive, ignore spaces/punct)
    fun normalizeForMatch(s: String): String {
        return s.trim().lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{Nd}]"), "")
    }

    // Initial permission check and start - only when active
    LaunchedEffect(isActive) {
        refreshPermission()
        if (isActive) {
            if (hasPermission) {
                voiceViewModel.startListening()
            } else if (!permissionRequestedOnce) {
                permissionRequestedOnce = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            // Stop listening when not active
            voiceViewModel.stopListening()
        }
    }

    // Re-check permission when resuming (e.g., returning from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val before = hasPermission
                refreshPermission()
                if (!before && hasPermission) {
                    voiceViewModel.startListening()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Reset autoLaunch guard when listening restarts
    LaunchedEffect(voiceState.isListening) {
        if (voiceState.isListening) autoLaunched = false
    }

    // Filter apps based on live transcription with debounce to reduce excessive filtering
    LaunchedEffect(voiceState.transcription) {
        if (voiceState.transcription.isNotEmpty()) {
            kotlinx.coroutines.delay(300) // Debounce by 300ms
            val searchQuery = cleanupSpokenText(voiceState.transcription)
            launcherViewModel.filterAppsVoice(searchQuery)
        } else {
            launcherViewModel.resetFilterVoice()
        }
    }

    // Ensure final result also drives filtering (in case it differs from interim transcription)
    LaunchedEffect(voiceState.recognitionResult) {
        if (voiceState.recognitionResult is RecognitionResult.Success) {
            val text = (voiceState.recognitionResult as RecognitionResult.Success).text
            if (text.isNotEmpty()) {
                launcherViewModel.filterAppsVoice(cleanupSpokenText(text))
            }
        }
    }

    // Auto-launch when there's exactly one filtered app AND its name is an exact match
    // Triggers on filtered list changes, transcription updates, or final recognition result
    LaunchedEffect(
        launcherState.voiceFilteredApps,
        voiceState.transcription,
        voiceState.recognitionResult
    ) {
        if (autoLaunched) return@LaunchedEffect

        val termFromResult = (voiceState.recognitionResult as? RecognitionResult.Success)
            ?.text
            ?.let { cleanupSpokenText(it) }
            ?.takeIf { it.isNotBlank() }

        val termFromTranscription = cleanupSpokenText(voiceState.transcription)
            .takeIf { it.isNotBlank() }

        val searchTerm = termFromResult ?: termFromTranscription
        val filtered = launcherState.voiceFilteredApps

        if (!searchTerm.isNullOrBlank() && filtered.size == 1) {
            val app = filtered.first()
            val isExactMatch = normalizeForMatch(app.label) == normalizeForMatch(searchTerm)
            if (isExactMatch) {
                autoLaunched = true
                voiceViewModel.stopListening()
                launcherViewModel.launchApp(app.packageName, app.activityName)
                onDismiss()
            }
        }
    }

    // Stop listening when screen is dismissed or becomes inactive
    DisposableEffect(isActive) {
        onDispose {
            if (!isActive) {
                voiceViewModel.stopListening()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Listening state with wave animation and stop button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    !hasPermission -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Microphone permission required",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (permanentlyDenied) {
                                Button(onClick = {
                                    val intent =
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data =
                                                Uri.fromParts("package", context.packageName, null)
                                        }
                                    context.startActivity(intent)
                                }) {
                                    Text("Open Settings")
                                }
                            } else {
                                Button(onClick = {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }) {
                                    Text("Enable Microphone")
                                }
                            }
                        }
                    }

                    voiceState.isListening -> {
                        VoiceWaveAnimation(modifier = Modifier.size(60.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Listening...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(onClick = { voiceViewModel.stopListening() }) {
                            Text("Stop")
                        }
                    }

                    else -> {
                        // Show Start Listening button when not listening (error state, stopped, etc.)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (voiceState.recognitionResult is RecognitionResult.Error) {
                                Text(
                                    text = (voiceState.recognitionResult as RecognitionResult.Error).message,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Button(onClick = { voiceViewModel.startListening() }) {
                                Text("Start Listening")
                            }
                        }
                    }
                }
            }
        }

        // "Recognized:" label and "Clear" button - similar to handwriting mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (voiceState.transcription.isNotEmpty()) {
                Text(
                    text = "Recognized: ${voiceState.transcription}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                )
                TextButton(
                    onClick = {
                        voiceViewModel.clearTranscription()
                        launcherViewModel.resetFilterVoice()
                        if (!voiceState.isListening) {
                            voiceViewModel.startListening()
                        }
                    }
                ) {
                    Text(
                        text = "CLEAR",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Scroll to top when transcription is cleared - with delay for UI recomposition
        LaunchedEffect(voiceState.transcription) {
            if (voiceState.transcription.isEmpty()) {
                kotlinx.coroutines.delay(100) // Give time for UI to recompose with full list
                voiceGridState.scrollToItem(0)
            }
        }

        // Apps grid
        Crossfade(
            targetState = launcherState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (launcherState.voiceCalculatorResult != null) {
                // Show calculator result
                Column(
                    modifier = Modifier
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
                                voiceState.transcription
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "= ${launcherState.voiceCalculatorResult}",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                val showSmart =
                    launcherState.voiceSmartApps.isNotEmpty() || (if (voiceState.transcription.isEmpty()) launcherState.apps else launcherState.voiceFilteredApps).isNotEmpty()
                if (showSmart) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val appsToShow =
                            if (voiceState.transcription.isEmpty()) launcherState.apps else launcherState.voiceFilteredApps

                        val combinedAppList = remember(
                            launcherState.voiceSmartApps,
                            appsToShow,
                            sortOrder,
                            voiceState.transcription,
                            gridSize
                        ) {
                            combineAppListsWithHeaders(
                                launcherState.voiceSmartApps,
                                appsToShow,
                                sortOrder,
                                isSearching = voiceState.transcription.isNotEmpty(),
                                gridSize = gridSize
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            val showFade =
                                voiceGridState.firstVisibleItemIndex > 0 || voiceGridState.firstVisibleItemScrollOffset > 0

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridSize),
                                state = voiceGridState,
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
                                modifier = Modifier.fillMaxWidth()
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
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.12f
                                                )
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
                                                    val searchQuery =
                                                        cleanupSpokenText(voiceState.transcription)
                                                    launcherViewModel.hideApp(
                                                        item.appInfo.getIdentifier(),
                                                        LauncherViewModel.LauncherMode.VOICE,
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
                    }
                } else if (voiceState.transcription.isNotEmpty()) {
                    val isHiddenApp = launcherState.hiddenApps.any {
                        it.label.equals(voiceState.transcription, ignoreCase = true)
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
                                                Intent(Intent.ACTION_VIEW)
                                                    .apply {
                                                        data = Uri.parse(
                                                            "https://www.google.com/search?q=${
                                                                Uri.encode(voiceState.transcription)
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
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Google")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val intent =
                                                Intent(Intent.ACTION_VIEW)
                                                    .apply {
                                                        data = Uri.parse(
                                                            "https://play.google.com/store/search?q=${
                                                                Uri.encode(voiceState.transcription)
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
    }
}

@Composable
fun VoiceWaveAnimation(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        drawCircle(
            color = primaryColor.copy(alpha = 0.3f),
            radius = radius * scale,
            center = center
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.5f),
            radius = radius * 0.7f * scale,
            center = center
        )
        drawCircle(
            color = primaryColor,
            radius = radius * 0.4f,
            center = center
        )
    }
}