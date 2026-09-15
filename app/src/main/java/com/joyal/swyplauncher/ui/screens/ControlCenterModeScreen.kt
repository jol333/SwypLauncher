package com.joyal.swyplauncher.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.ActiveMediaSession
import com.joyal.swyplauncher.domain.model.RingerMode
import com.joyal.swyplauncher.service.MediaNotificationListenerService
import com.joyal.swyplauncher.ui.theme.BentoColors
import com.joyal.swyplauncher.ui.util.combineAppListsWithHeaders
import com.joyal.swyplauncher.ui.viewmodel.ControlCenterViewModel
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel
import com.joyal.swyplauncher.util.safeStartActivity
import coil3.compose.AsyncImage
import kotlin.math.abs

private val ControlCenterCard = Color(0xFF171718)
private val ControlCenterOffBorder = Color(0xFF27272A)
private val WifiGlow = Color(0xFF5EE9B5)
private val BluetoothGlow = Color(0xFF66E1FF)
private val SimDataGlow = Color(0xFFD867FA)

@Composable
private fun FigmaVector(
    rawRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    AsyncImage(
        model = rawRes,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
fun ControlCenterModeScreen(
    onDismiss: () -> Unit,
    launcherViewModel: LauncherViewModel,
    isSheetExpanded: Boolean,
    isBlurEnabled: Boolean = false,
    onAddShortcut: ((String) -> Unit)? = null,
    controlCenterViewModel: ControlCenterViewModel = hiltViewModel()
) {
    val launcherState by launcherViewModel.uiState.collectAsState()
    val gridSize by launcherViewModel.gridSize.collectAsState()
    val cornerRadius by launcherViewModel.cornerRadius.collectAsState()
    val sortOrder by launcherViewModel.appSortOrder.collectAsState()
    val gridState = rememberLazyGridState()

    // Track menu state outside of LazyGrid items to prevent state loss
    var selectedAppIndex by remember { mutableIntStateOf(-1) }

    DisposableEffect(Unit) {
        controlCenterViewModel.onVisible()
        onDispose { controlCenterViewModel.onHidden() }
    }

    // Toggles changed inside Settings pages/panels must show when the user comes back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controlCenterViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The app list joins the controls only once the sheet is fully expanded
    val items = remember(launcherState.apps, sortOrder, gridSize, isSheetExpanded) {
        if (isSheetExpanded) {
            combineAppListsWithHeaders(
                emptyList(),
                launcherState.apps,
                sortOrder,
                isSearching = false,
                gridSize = gridSize
            )
        } else {
            emptyList()
        }
    }

    // Panel collects its own state, so slider drags never recompose the grid
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val headerContent: @Composable () -> Unit = remember(controlCenterViewModel, isBlurEnabled) {
        {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                ControlCenterPanel(
                    viewModel = controlCenterViewModel,
                    isBlurEnabled = isBlurEnabled,
                    onDismiss = { currentOnDismiss() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    AppResultGrid(
        items = items,
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
            // Hiding is global (prefs-backed); KEYBOARD with a blank query just picks the
            // refresh branch that republishes the full visible list — there is no
            // control-center entry in the view model's per-mode filter enum.
            launcherViewModel.hideApp(app.getIdentifier(), LauncherViewModel.LauncherMode.KEYBOARD)
        },
        onAddShortcut = onAddShortcut,
        headerContent = headerContent,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 100.dp)
    )
}

/** Eats the rest of a gesture so ancestors (grid scroll, sheet drag) don't act on it. */
private suspend fun AwaitPointerEventScope.consumeUntilRelease() {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) break
    }
}

/** Starts [intent] without toasting; fallback chains toast once themselves. */
private fun tryStartActivity(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent) }.isSuccess

/** Tries each Settings [actions] in order until one resolves; toasts once when none do. */
private fun openSettings(context: Context, vararg actions: String) {
    val opened = actions.any { action ->
        tryStartActivity(context, Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    if (!opened) {
        Toast.makeText(context, R.string.cc_settings_page_unavailable, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shows the system "turn on location?" dialog via the GMS resolution flow. Falls back to
 * the location Settings page on devices without Play Services or when nothing is resolvable.
 */
private fun promptEnableLocation(
    context: Context,
    launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
    onFallback: () -> Unit
) {
    runCatching {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
            )
            .setAlwaysShow(true)
            .build()
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(request)
            .addOnFailureListener { error ->
                val resolution = (error as? ResolvableApiException)?.resolution
                if (resolution != null) {
                    runCatching {
                        launcher.launch(IntentSenderRequest.Builder(resolution).build())
                    }.onFailure { onFallback() }
                } else {
                    onFallback()
                }
            }
    }.onFailure { onFallback() }
}

/** Deep-links to this app's notification-listener toggle; falls back to the full list. */
private fun openNotificationAccessSettings(context: Context) {
    val component = ComponentName(context, MediaNotificationListenerService::class.java)
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            component.flattenToString()
        )
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!tryStartActivity(context, detail)) {
        openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

/** Opens the "Modify system settings" page for this app (brightness / auto-rotate access). */
private fun requestWriteSettings(context: Context) {
    context.safeStartActivity(
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        R.string.cc_settings_page_unavailable
    )
}

@Composable
private fun ControlCenterPanel(
    viewModel: ControlCenterViewModel,
    isBlurEnabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val mediaState by viewModel.mediaState.collectAsState()
    val cardBackground =
        if (isBlurEnabled) Color.White.copy(alpha = 0.08f) else ControlCenterCard

    val toast: (Int) -> Unit = remember(context) {
        { res -> Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show() }
    }

    // Handlers read the current state at click time (viewModel.state.value) so they can
    // stay remembered forever — pills then skip recomposition during slider drags.
    val wifiNameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Only precise location un-redacts the SSID; approximate keeps the generic title
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.restartObservers()
        openSettings(
            context,
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY,
            Settings.ACTION_WIFI_SETTINGS
        )
    }
    val onWifiClick = remember(context, viewModel, wifiNameLauncher) {
        {
            val wifi = viewModel.state.value.wifi
            when {
                !wifi.isAvailable -> toast(R.string.cc_feature_unavailable)
                // Strictly one-shot ask (a deny used to re-trigger it on every tap) so the
                // tile can show the network name, with a toast saying why location comes up;
                // the panel opens from the launcher callback either way
                wifi.isOn && wifi.label == null && !viewModel.isWifiNameAccessGranted() &&
                    viewModel.consumeWifiNamePermissionPrompt() -> {
                    toast(R.string.cc_wifi_name_needs_location)
                    wifiNameLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
                // Apps can't toggle Wi-Fi directly; the internet panel carries the toggle
                else -> openSettings(
                    context,
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY,
                    Settings.ACTION_WIFI_SETTINGS
                )
            }
        }
    }
    val onWifiLongClick = remember(context, viewModel) {
        {
            if (!viewModel.state.value.wifi.isAvailable) toast(R.string.cc_feature_unavailable)
            else openSettings(context, Settings.ACTION_WIFI_SETTINGS)
        }
    }

    var pendingBtTarget by remember { mutableStateOf(false) }
    val fireBtToggle: (Boolean) -> Unit = remember(context) {
        { enable ->
            // System consent dialogs; REQUEST_DISABLE is handled on virtually all OEMs
            val intent = if (enable) {
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            } else {
                Intent("android.bluetooth.adapter.action.REQUEST_DISABLE")
            }
            if (!tryStartActivity(context, intent)) {
                openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            }
        }
    }
    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Re-register observers so the connected device's name can now be read
            viewModel.restartObservers()
            fireBtToggle(pendingBtTarget)
        } else {
            toast(R.string.cc_bt_permission_denied)
        }
    }
    val onBluetoothClick = remember(context, viewModel, btPermLauncher) {
        {
            val bt = viewModel.state.value.bluetooth
            when {
                !bt.isAvailable -> toast(R.string.cc_feature_unavailable)
                !viewModel.isBluetoothConnectGranted() -> {
                    pendingBtTarget = !bt.isOn
                    btPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
                else -> fireBtToggle(!bt.isOn)
            }
        }
    }
    val onBluetoothLongClick = remember(context, viewModel) {
        {
            if (!viewModel.state.value.bluetooth.isAvailable) toast(R.string.cc_feature_unavailable)
            else openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
        }
    }

    val onSimClick = remember(context, viewModel) {
        {
            if (!viewModel.state.value.mobileData.isAvailable) toast(R.string.cc_feature_unavailable)
            // The mobile-data toggle itself is system-app only; the panel hosts it
            else openSettings(
                context,
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY,
                Settings.ACTION_WIRELESS_SETTINGS
            )
        }
    }
    val onSimLongClick = remember(context, viewModel) {
        {
            if (!viewModel.state.value.mobileData.isAvailable) toast(R.string.cc_feature_unavailable)
            else openSettings(
                context,
                Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
                Settings.ACTION_WIRELESS_SETTINGS
            )
        }
    }

    val setVolume: (Float) -> Unit = remember(viewModel) { viewModel::setMediaVolume }
    val setBrightness: (Float) -> Unit = remember(viewModel) { viewModel::setBrightness }
    // Returns true (blocked) when write-settings access is missing, after prompting for it
    val brightnessGate: () -> Boolean = remember(context, viewModel) {
        {
            val blocked = !viewModel.hasWriteSettingsAccess()
            if (blocked) {
                toast(R.string.cc_needs_write_settings)
                requestWriteSettings(context)
            }
            blocked
        }
    }
    val onBrightnessLongPress: () -> Unit = remember(context, viewModel) {
        {
            if (!brightnessGate()) {
                val enable = !viewModel.state.value.isAutoBrightness
                if (viewModel.setAutoBrightness(enable)) {
                    toast(
                        if (enable) R.string.cc_auto_brightness_on
                        else R.string.cc_auto_brightness_off
                    )
                }
            }
        }
    }

    val onSelectRinger: (RingerMode) -> Unit = remember(context, viewModel) {
        { mode ->
            if (!viewModel.setRingerMode(mode)) {
                if (!viewModel.hasNotificationPolicyAccess()) {
                    toast(R.string.cc_needs_dnd_access)
                    context.safeStartActivity(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        R.string.cc_settings_page_unavailable
                    )
                } else {
                    // Access is granted but the OEM refused the switch
                    toast(R.string.cc_feature_unavailable)
                }
            }
        }
    }

    // Location can only be turned ON by the GMS resolution dialog and only turned OFF in
    // Settings; airplane mode is a secure setting — deep link only.
    val locationDialogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { viewModel.refresh() }
    val onLocationClick = remember(context, viewModel, locationDialogLauncher) {
        {
            val location = viewModel.state.value.location
            when {
                !location.isAvailable -> toast(R.string.cc_feature_unavailable)
                location.isOn -> openSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                else -> promptEnableLocation(context, locationDialogLauncher) {
                    openSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                }
            }
        }
    }
    val onLocationLongClick = remember(context, viewModel) {
        {
            if (!viewModel.state.value.location.isAvailable) toast(R.string.cc_feature_unavailable)
            else openSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
    }
    val onAutoRotateClick = remember(context, viewModel) {
        {
            val rotate = viewModel.state.value.autoRotate
            if (!rotate.isAvailable) {
                toast(R.string.cc_feature_unavailable)
            } else if (!viewModel.setAutoRotate(!rotate.isOn)) {
                toast(R.string.cc_needs_write_settings)
                requestWriteSettings(context)
            }
        }
    }
    val onAutoRotateLongClick = remember(context, viewModel) {
        {
            if (!viewModel.state.value.autoRotate.isAvailable) toast(R.string.cc_feature_unavailable)
            else openSettings(context, Settings.ACTION_DISPLAY_SETTINGS)
        }
    }
    val onFlashlightClick = remember(context, viewModel) {
        {
            val flash = viewModel.state.value.flashlight
            if (!flash.isAvailable) toast(R.string.cc_feature_unavailable)
            else if (!viewModel.setFlashlight(!flash.isOn)) toast(R.string.cc_flashlight_busy)
        }
    }
    val onAirplaneClick = remember(context, viewModel) {
        {
            if (!viewModel.state.value.airplaneMode.isAvailable) toast(R.string.cc_feature_unavailable)
            else openSettings(
                context,
                Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                Settings.ACTION_WIRELESS_SETTINGS
            )
        }
    }

    val onToggleMediaPlay = remember(viewModel) { viewModel::toggleMediaPlayPause }
    val onMediaPrevious = remember(viewModel) { viewModel::skipMediaToPrevious }
    val onMediaNext = remember(viewModel) { viewModel::skipMediaToNext }
    val onMediaSeek: (Long) -> Unit = remember(viewModel) { viewModel::seekMediaTo }
    val onOpenMediaApp = remember(context, viewModel, onDismiss) {
        {
            if (viewModel.openMediaApp()) onDismiss()
            else toast(R.string.cc_media_open_app_failed)
        }
    }
    val onMediaAccessClick = remember(context) {
        { openNotificationAccessSettings(context) }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val mediaSession = mediaState.session
        // Latched so the card keeps rendering while it animates out after the session clears;
        // once the exit transition idles the AnimatedVisibility node itself is dropped so the
        // column's spacedBy gap doesn't linger.
        var lastMediaSession by remember { mutableStateOf<ActiveMediaSession?>(null) }
        if (mediaSession != null) lastMediaSession = mediaSession
        val mediaCardVisible = remember { MutableTransitionState(false) }
        mediaCardVisible.targetState = mediaSession != null
        if (mediaSession != null || !mediaCardVisible.isIdle) {
            AnimatedVisibility(
                visibleState = mediaCardVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                lastMediaSession?.let { latchedSession ->
                    MediaControlsCard(
                        session = latchedSession,
                        cardBackground = cardBackground,
                        onTogglePlay = onToggleMediaPlay,
                        onSkipPrevious = onMediaPrevious,
                        onSkipNext = onMediaNext,
                        onSeekTo = onMediaSeek,
                        onOpenApp = onOpenMediaApp
                    )
                }
            }
        } else if (mediaState.needsNotificationAccess) {
            MediaAccessPromptCard(
                cardBackground = cardBackground,
                onClick = onMediaAccessClick
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ControlPill(
                    label = state.wifi.label ?: stringResource(R.string.cc_wifi),
                    onIconRes = R.raw.cc_wifi_on,
                    offIconRes = R.raw.cc_wifi_off,
                    isOn = state.wifi.isOn,
                    isAvailable = state.wifi.isAvailable,
                    glow = WifiGlow,
                    cardBackground = cardBackground,
                    onClick = onWifiClick,
                    onLongClick = onWifiLongClick
                )
                ControlPill(
                    label = state.bluetooth.label ?: stringResource(R.string.cc_bluetooth),
                    onIconRes = R.raw.cc_bluetooth_on,
                    offIconRes = R.raw.cc_bluetooth_off,
                    isOn = state.bluetooth.isOn,
                    isAvailable = state.bluetooth.isAvailable,
                    glow = BluetoothGlow,
                    cardBackground = cardBackground,
                    onClick = onBluetoothClick,
                    onLongClick = onBluetoothLongClick
                )
                ControlPill(
                    label = state.mobileData.label ?: stringResource(R.string.cc_sim_data),
                    onIconRes = R.raw.cc_sim_data_on,
                    offIconRes = R.raw.cc_sim_data_off,
                    isOn = state.mobileData.isOn,
                    isAvailable = state.mobileData.isAvailable,
                    glow = SimDataGlow,
                    cardBackground = cardBackground,
                    onClick = onSimClick,
                    onLongClick = onSimLongClick
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    VerticalSlider(
                        fraction = state.mediaVolume,
                        cardBackground = cardBackground,
                        fillGradientColors = listOf(Color(0xFF6750A4), Color(0xFF6750A4).copy(alpha = 0.64f)),
                        onIconRes = R.raw.cc_media_volume_on,
                        offIconRes = R.raw.cc_media_volume_off,
                        contentDescription = stringResource(R.string.cc_media_volume),
                        onSetFraction = setVolume,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    VerticalSlider(
                        fraction = state.brightness,
                        cardBackground = cardBackground,
                        fillGradientColors = listOf(Color(0xFFC97892).copy(alpha = 0.8f), Color(0xFF633B48)),
                        onIconRes = R.raw.cc_brightness_on,
                        offIconRes = R.raw.cc_brightness_off,
                        contentDescription = stringResource(R.string.cc_brightness),
                        onSetFraction = setBrightness,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        gate = brightnessGate,
                        onLongPress = onBrightnessLongPress
                    )
                }
                RingerModeRow(
                    selected = state.ringerMode,
                    cardBackground = cardBackground,
                    onSelect = onSelectRinger
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RoundControlSlot(
                onIconRes = R.raw.cc_location_on,
                offIconRes = R.raw.cc_location_off,
                contentDescription = stringResource(R.string.cc_location),
                isOn = state.location.isOn,
                isAvailable = state.location.isAvailable,
                cardBackground = cardBackground,
                onClick = onLocationClick,
                onLongClick = onLocationLongClick,
                modifier = Modifier.weight(1f)
            )
            RoundControlSlot(
                onIconRes = R.raw.cc_rotation_on,
                offIconRes = R.raw.cc_rotation_off,
                contentDescription = stringResource(R.string.cc_auto_rotate),
                isOn = state.autoRotate.isOn,
                isAvailable = state.autoRotate.isAvailable,
                cardBackground = cardBackground,
                onClick = onAutoRotateClick,
                onLongClick = onAutoRotateLongClick,
                modifier = Modifier.weight(1f)
            )
            RoundControlSlot(
                onIconRes = R.raw.cc_flashlight_on,
                offIconRes = R.raw.cc_flashlight_off,
                contentDescription = stringResource(R.string.cc_flashlight),
                isOn = state.flashlight.isOn,
                isAvailable = state.flashlight.isAvailable,
                cardBackground = cardBackground,
                onClick = onFlashlightClick,
                onLongClick = null,
                modifier = Modifier.weight(1f)
            )
            RoundControlSlot(
                onIconRes = R.raw.cc_airplane_on,
                offIconRes = R.raw.cc_airplane_off,
                contentDescription = stringResource(R.string.cc_airplane_mode),
                isOn = state.airplaneMode.isOn,
                isAvailable = state.airplaneMode.isAvailable,
                cardBackground = cardBackground,
                onClick = onAirplaneClick,
                onLongClick = onAirplaneClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Figma's low, wide radial glow for the connected-control tiles. */
private fun Modifier.accentGlow(accent: Color): Modifier = drawWithCache {
    val center = Offset(size.width / 2f, size.height)
    val brush = Brush.radialGradient(
        colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
        center = center,
        radius = (size.width * 0.45f).coerceAtLeast(1f)
    )
    onDrawBehind {
        withTransform({
            scale(scaleX = 1f, scaleY = 0.35f, pivot = center)
        }) { drawRect(brush) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlPill(
    label: String,
    onIconRes: Int,
    offIconRes: Int,
    isOn: Boolean,
    isAvailable: Boolean,
    glow: Color,
    cardBackground: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(40.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(shape)
            .then(
                if (isOn) {
                    Modifier
                        .background(cardBackground)
                        .border(1.dp, Color(0x1A000000), shape)
                        .accentGlow(glow)
                } else {
                    Modifier.border(1.dp, ControlCenterOffBorder, shape)
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (isAvailable) 1f else 0.4f)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FigmaVector(
            rawRes = if (isOn) onIconRes else offIconRes,
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isOn) Color.White else BentoColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VerticalSlider(
    fraction: Float,
    cardBackground: Color,
    fillGradientColors: List<Color>,
    onIconRes: Int,
    offIconRes: Int,
    contentDescription: String,
    onSetFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    gate: (() -> Boolean)? = null,
    onLongPress: (() -> Unit)? = null
) {
    // Local value while the finger is down so display never waits on the state round-trip
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentFraction by rememberUpdatedState(fraction)
    val currentOnSet by rememberUpdatedState(onSetFraction)
    val currentGate by rememberUpdatedState(gate)
    val currentLongPress by rememberUpdatedState(onLongPress)

    val displayed = (dragFraction ?: fraction).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(40.dp)

    // One handler arbitrates tap / long-press / drag itself: separate detectors raced each
    // other and the grid+sheet gesture stack (a slow drag tripped long-press, which then
    // consumed the gesture), which is how the sliders ended up dead on real devices.
    Box(
        modifier = modifier
            .clip(shape)
            .background(cardBackground)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    val hasLongPress = currentLongPress != null
                    val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                    var lastEventTime = down.uptimeMillis
                    var dx = 0f
                    var dy = 0f
                    var fraction = currentFraction

                    // Decide: tap, long-press, vertical drag, or horizontal (the pager's)
                    while (true) {
                        val event = if (hasLongPress) {
                            withTimeoutOrNull((deadline - lastEventTime).coerceAtLeast(1L)) {
                                awaitPointerEvent()
                            }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // Held still past the timeout: long-press
                            currentLongPress?.invoke()
                            consumeUntilRelease()
                            return@awaitEachGesture
                        }
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                        lastEventTime = change.uptimeMillis
                        if (!change.pressed) {
                            // Tap: jump straight to the released position
                            if (currentGate?.invoke() != true && size.height > 0) {
                                currentOnSet((1f - change.position.y / size.height).coerceIn(0f, 1f))
                            }
                            change.consume()
                            return@awaitEachGesture
                        }
                        val delta = change.positionChange()
                        dx += abs(delta.x)
                        dy += abs(delta.y)
                        if (dy > slop && dy > dx) {
                            change.consume()
                            break
                        }
                        if (dx > slop && dx >= dy) return@awaitEachGesture
                    }

                    // Vertical drag: the slider owns the rest of the gesture
                    if (currentGate?.invoke() == true) {
                        consumeUntilRelease() // blocked: swallow it so the sheet doesn't move
                        return@awaitEachGesture
                    }
                    try {
                        dragFraction = fraction
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (size.height > 0) {
                                fraction = (fraction - change.positionChange().y / size.height)
                                    .coerceIn(0f, 1f)
                                dragFraction = fraction
                                // Conflated in the view model, so per-frame emits are safe
                                currentOnSet(fraction)
                            }
                            change.consume()
                        }
                    } finally {
                        dragFraction = null
                    }
                }
            }
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val waveHeight = 12.dp.toPx()
                    val baseY = (h + waveHeight) * (1f - displayed)
                    val sx = w / 79f
                    val sy = waveHeight / 11f

                    val path = Path().apply {
                        moveTo(0f, baseY - (11f * sy))
                        lineTo(9.8869f * sx, baseY - (12f - 6.25633f) * sy)
                        cubicTo(
                            16.0192f * sx, baseY - (12f - 9.51654f) * sy,
                            23.4014f * sx, baseY - (12f - 9.37389f) * sy,
                            29.4031f * sx, baseY - (12f - 5.87918f) * sy
                        )
                        cubicTo(
                            35.6439f * sx, baseY - (12f - 2.2453f) * sy,
                            43.3561f * sx, baseY - (12f - 2.2453f) * sy,
                            49.5969f * sx, baseY - (12f - 5.87918f) * sy
                        )
                        cubicTo(
                            55.5986f * sx, baseY - (12f - 9.37389f) * sy,
                            62.9808f * sx, baseY - (12f - 9.51654f) * sy,
                            69.1131f * sx, baseY - (12f - 6.25633f) * sy
                        )
                        lineTo(w, baseY - (11f * sy))
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }

                    val gradientStart = (baseY - waveHeight).coerceIn(0f, h)
                    val gradientEnd = h.coerceAtLeast(gradientStart + 1f)
                    val brush = Brush.verticalGradient(
                        colors = fillGradientColors,
                        startY = gradientStart,
                        endY = gradientEnd
                    )

                    onDrawBehind {
                        drawPath(path = path, brush = brush)
                    }
                }
        )
        FigmaVector(
            rawRes = if (displayed == 0f) offIconRes else onIconRes,
            contentDescription = contentDescription,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .size(32.dp)
        )
    }
}

@Composable
private fun RingerModeRow(
    selected: RingerMode,
    cardBackground: Color,
    onSelect: (RingerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(cardBackground)
            .ringerModeGlow(selected),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RingerSegment(
            mode = RingerMode.SILENT,
            onIconRes = R.raw.cc_ringer_silent_on,
            offIconRes = R.raw.cc_ringer_silent_off,
            contentDescription = stringResource(R.string.cc_silent),
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
        RingerSegment(
            mode = RingerMode.VIBRATE,
            onIconRes = R.raw.cc_ringer_vibrate_on,
            offIconRes = R.raw.cc_ringer_vibrate_off,
            contentDescription = stringResource(R.string.cc_vibrate),
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
        RingerSegment(
            mode = RingerMode.RING,
            onIconRes = R.raw.cc_ringer_ring_on,
            offIconRes = R.raw.cc_ringer_ring_off,
            contentDescription = stringResource(R.string.cc_ring),
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RingerSegment(
    mode: RingerMode,
    onIconRes: Int,
    offIconRes: Int,
    contentDescription: String,
    selected: RingerMode,
    onSelect: (RingerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onSelect(mode) },
        contentAlignment = Alignment.Center
    ) {
        FigmaVector(
            rawRes = if (mode == selected) onIconRes else offIconRes,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Figma's selected ringer state is expressed by the pill background, not a selected icon chip. */
private fun Modifier.ringerModeGlow(selected: RingerMode): Modifier = drawWithCache {
    val (center, radius, stops) = when (selected) {
        RingerMode.SILENT -> Triple(
            Offset(size.width * 0.029f, size.height * 0.525f),
            size.height * 2.56f,
            arrayOf(0f to Color(0x30FF7474), 1f to Color.Transparent)
        )
        RingerMode.VIBRATE -> Triple(
            Offset(size.width * 0.5f, size.height * 0.5f),
            size.width * 0.37f,
            arrayOf(0f to Color(0x2E66E1FF), 1f to Color.Transparent)
        )
        RingerMode.RING -> Triple(
            Offset(size.width * 0.874f, size.height * 0.525f),
            size.height * 2.54f,
            arrayOf(
                0f to Color(0x2015FF00),
                0.25f to Color(0x183AFF00),
                0.5f to Color(0x1060FF00),
                1f to Color.Transparent
            )
        )
    }
    val brush = Brush.radialGradient(colorStops = stops, center = center, radius = radius)
    onDrawBehind { drawRect(brush) }
}

/** Centers a Figma-sized lower control inside its weighted grid slot. */
@Composable
private fun RoundControlSlot(
    onIconRes: Int,
    offIconRes: Int,
    contentDescription: String,
    isOn: Boolean,
    isAvailable: Boolean,
    cardBackground: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    RoundControl(
        onIconRes = onIconRes,
        offIconRes = offIconRes,
        contentDescription = contentDescription,
        isOn = isOn,
        isAvailable = isAvailable,
        cardBackground = cardBackground,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.aspectRatio(1f)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundControl(
    onIconRes: Int,
    offIconRes: Int,
    contentDescription: String,
    isOn: Boolean,
    isAvailable: Boolean,
    cardBackground: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(40.dp))
            .then(
                if (isOn) {
                    Modifier.background(cardBackground)
                } else {
                    Modifier.border(1.dp, ControlCenterOffBorder, RoundedCornerShape(40.dp))
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (isAvailable) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        FigmaVector(
            rawRes = if (isOn) onIconRes else offIconRes,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp)
        )
    }
}
