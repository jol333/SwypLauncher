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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import kotlin.math.abs

// Per-control accents (design screenshot); fills for the two vertical sliders.
private val AccentWifi = BentoColors.AccentGreen
private val AccentBt = Color(0xFF22D3EE)
private val AccentSim = Color(0xFFE879F9)
private val AccentLocation = Color(0xFF3B82F6)
private val AccentRotate = Color(0xFFF59E0B)
private val AccentFlash = Color(0xFF00BBA7)
private val AccentAirplane = Color(0xFFFF5A5F)
private val AccentRinger = Color(0xFF22D3EE)
private val FillVolume = Color(0xFF8B7FD6)
private val FillBrightness = Color(0xFFC97B8E)
private val RingerIconOnAccent = Color(0xFF0B1020)

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
                    modifier = Modifier.widthIn(max = 480.dp)
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
        headerContent = headerContent
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
        if (isBlurEnabled) Color.White.copy(alpha = 0.08f) else BentoColors.CardBackground

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
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                modifier = Modifier.weight(1.15f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ControlPill(
                    label = state.wifi.label ?: stringResource(R.string.cc_wifi),
                    icon = Icons.Rounded.Wifi,
                    isOn = state.wifi.isOn,
                    isAvailable = state.wifi.isAvailable,
                    accent = AccentWifi,
                    cardBackground = cardBackground,
                    onClick = onWifiClick,
                    onLongClick = onWifiLongClick
                )
                ControlPill(
                    label = state.bluetooth.label ?: stringResource(R.string.cc_bluetooth),
                    icon = Icons.Rounded.Bluetooth,
                    isOn = state.bluetooth.isOn,
                    isAvailable = state.bluetooth.isAvailable,
                    accent = AccentBt,
                    cardBackground = cardBackground,
                    onClick = onBluetoothClick,
                    onLongClick = onBluetoothLongClick
                )
                ControlPill(
                    label = state.mobileData.label ?: stringResource(R.string.cc_sim_data),
                    icon = Icons.Rounded.SwapVert,
                    isOn = state.mobileData.isOn,
                    isAvailable = state.mobileData.isAvailable,
                    accent = AccentSim,
                    cardBackground = cardBackground,
                    onClick = onSimClick,
                    onLongClick = onSimLongClick
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(0.85f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VerticalSlider(
                        fraction = state.mediaVolume,
                        fillColor = FillVolume,
                        cardBackground = cardBackground,
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.cc_media_volume),
                        onSetFraction = setVolume,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        zeroIcon = Icons.AutoMirrored.Rounded.VolumeOff
                    )
                    VerticalSlider(
                        fraction = state.brightness,
                        fillColor = FillBrightness,
                        cardBackground = cardBackground,
                        icon = if (state.isAutoBrightness) Icons.Rounded.BrightnessAuto
                        else Icons.Rounded.LightMode,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoundControlSlot(
                icon = Icons.Rounded.LocationOn,
                contentDescription = stringResource(R.string.cc_location),
                isOn = state.location.isOn,
                isAvailable = state.location.isAvailable,
                accent = AccentLocation,
                cardBackground = cardBackground,
                onClick = onLocationClick,
                onLongClick = onLocationLongClick,
                modifier = Modifier.weight(1f)
            )
            RoundControlSlot(
                icon = Icons.Rounded.ScreenRotation,
                contentDescription = stringResource(R.string.cc_auto_rotate),
                isOn = state.autoRotate.isOn,
                isAvailable = state.autoRotate.isAvailable,
                accent = AccentRotate,
                cardBackground = cardBackground,
                onClick = onAutoRotateClick,
                onLongClick = onAutoRotateLongClick,
                modifier = Modifier.weight(1f)
            )
            RoundControlSlot(
                icon = if (state.flashlight.isOn) Icons.Rounded.FlashlightOn
                else Icons.Rounded.FlashlightOff,
                contentDescription = stringResource(R.string.cc_flashlight),
                isOn = state.flashlight.isOn,
                isAvailable = state.flashlight.isAvailable,
                accent = AccentFlash,
                cardBackground = cardBackground,
                onClick = onFlashlightClick,
                onLongClick = null,
                modifier = Modifier.weight(1f)
            )
            RoundControlSlot(
                icon = Icons.Rounded.Flight,
                contentDescription = stringResource(R.string.cc_airplane_mode),
                isOn = state.airplaneMode.isOn,
                isAvailable = state.airplaneMode.isAvailable,
                accent = AccentAirplane,
                cardBackground = cardBackground,
                onClick = onAirplaneClick,
                onLongClick = onAirplaneClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Soft radial accent wash anchored near the bottom edge; brush built once per size. */
private fun Modifier.accentGlow(accent: Color): Modifier = drawWithCache {
    val brush = Brush.radialGradient(
        colors = listOf(accent.copy(alpha = 0.25f), Color.Transparent),
        center = Offset(size.width / 2f, size.height * 0.85f),
        radius = (size.width * 0.7f).coerceAtLeast(1f)
    )
    onDrawBehind { drawRect(brush) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlPill(
    label: String,
    icon: ImageVector,
    isOn: Boolean,
    isAvailable: Boolean,
    accent: Color,
    cardBackground: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(38.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .then(
                if (isOn) {
                    Modifier
                        .background(cardBackground)
                        .accentGlow(accent)
                } else {
                    Modifier.border(1.dp, BentoColors.BorderLight, shape)
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (isAvailable) 1f else 0.4f)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isOn) accent else BentoColors.TextSecondary,
            modifier = Modifier.size(24.dp)
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
    fillColor: Color,
    cardBackground: Color,
    icon: ImageVector,
    contentDescription: String,
    onSetFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    zeroIcon: ImageVector? = null,
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
    val shape = RoundedCornerShape(28.dp)

    // One handler arbitrates tap / long-press / drag itself: separate detectors raced each
    // other and the grid+sheet gesture stack (a slow drag tripped long-press, which then
    // consumed the gesture), which is how the sliders ended up dead on real devices.
    Box(
        modifier = modifier
            .clip(shape)
            .background(cardBackground)
            .border(1.dp, BentoColors.BorderLight, shape)
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
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(displayed)
                .background(fillColor)
        )
        Icon(
            imageVector = if (zeroIcon != null && displayed == 0f) zeroIcon else icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .size(24.dp)
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
            .height(76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(cardBackground),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RingerSegment(
            mode = RingerMode.SILENT,
            icon = Icons.Rounded.NotificationsOff,
            contentDescription = stringResource(R.string.cc_silent),
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
        RingerSegment(
            mode = RingerMode.VIBRATE,
            icon = Icons.Rounded.Vibration,
            contentDescription = stringResource(R.string.cc_vibrate),
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
        RingerSegment(
            mode = RingerMode.RING,
            icon = Icons.Rounded.Notifications,
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
    icon: ImageVector,
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
        if (mode == selected) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AccentRinger, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = RingerIconOnAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = BentoColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Centers a [RoundControl] inside its weighted slot so it stays circular at any width. */
@Composable
private fun RoundControlSlot(
    icon: ImageVector,
    contentDescription: String,
    isOn: Boolean,
    isAvailable: Boolean,
    accent: Color,
    cardBackground: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        RoundControl(
            icon = icon,
            contentDescription = contentDescription,
            isOn = isOn,
            isAvailable = isAvailable,
            accent = accent,
            cardBackground = cardBackground,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .heightIn(max = 72.dp)
                .aspectRatio(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundControl(
    icon: ImageVector,
    contentDescription: String,
    isOn: Boolean,
    isAvailable: Boolean,
    accent: Color,
    cardBackground: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (isOn) {
                    Modifier
                        .background(cardBackground)
                        .accentGlow(accent)
                } else {
                    Modifier.border(1.dp, BentoColors.BorderLight, CircleShape)
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (isAvailable) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isOn) accent else BentoColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}
