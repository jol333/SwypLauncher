package com.joyal.swyplauncher.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.ui.theme.BentoColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BentoSettingsScreen(
    isAssistantConfigured: Boolean,
    onSetupAssistant: () -> Unit,
    onOpenLauncher: () -> Unit,
    prefsFlow: MutableStateFlow<SharedPreferences>,
    preferencesRepository: PreferencesRepository? = null,
    installedApps: List<AppInfo> = emptyList(),
    screenEntryTimestamp: Long = 0L,
    shortcutsCount: Int = 0
) {
    val prefs by prefsFlow.collectAsState()
    var gridSize by remember { mutableStateOf(prefs.getInt("grid_size", 4)) }
    var cornerRadius by remember { mutableStateOf(prefs.getFloat("corner_radius", 0.75f)) }
    var autoOpenSingleResult by remember {
        mutableStateOf(
            prefs.getBoolean(
                "auto_open_single_result",
                false
            )
        )
    }
    var showModeOrderDialog by remember { mutableStateOf(false) }
    var showSortOrderDialog by remember { mutableStateOf(false) }
    // Track if user dismissed the bottom sheet in this session
    // Using screenEntryTimestamp as key forces reset each time user enters settings
    var userDismissedBottomSheet by remember(screenEntryTimestamp) { mutableStateOf(false) }
    // Show bottom sheet if not configured AND user hasn't dismissed it in this session
    val showAssistantBottomSheet = !isAssistantConfigured && !userDismissedBottomSheet
    var appSortOrder by remember {
        mutableStateOf(
            preferencesRepository?.getAppSortOrder()
                ?: com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME
        )
    }
    var backgroundBlurEnabled by remember {
        mutableStateOf(
            preferencesRepository?.isBackgroundBlurEnabled() ?: false
        )
    }
    var blurLevel by remember { mutableStateOf(preferencesRepository?.getBlurLevel() ?: 80) }

    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Get enabled modes count (mutable to update when dialog saves)
    var enabledModesCount by remember {
        val saved = prefs.getString("enabled_modes", null)
        mutableStateOf(if (saved != null) saved.split(",").size else 4)
    }

    // Calculate scroll progress for donate section animation
    val scrollProgress by remember {
        derivedStateOf {
            val lastItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastItem != null && lastItem.index == listState.layoutInfo.totalItemsCount - 1) {
                lastItem.offset + lastItem.size
                val viewportEnd = listState.layoutInfo.viewportEndOffset
                val visiblePortion =
                    (viewportEnd - lastItem.offset).toFloat() / lastItem.size.toFloat()
                min(1f, visiblePortion.coerceAtLeast(0f))
            } else {
                0f
            }
        }
    }

    // M3 Expressive spatial spring for jelly bounce effect
    // Low damping (0.65) creates the bounce, medium stiffness for responsive feel
    val expressiveSpatialSpring = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 300f
    )

    // Track card bounds for container transform
    var launchModesCardBounds by remember { mutableStateOf(Rect.Zero) }
    var sortAppsCardBounds by remember { mutableStateOf(Rect.Zero) }

    // Container transform animation progress (0 = card, 1 = popup)
    val modeOrderProgress by animateFloatAsState(
        targetValue = if (showModeOrderDialog) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f, // M3E spatial spring - jelly bounce
            stiffness = 280f
        ),
        label = "modeOrderProgress"
    )

    val sortOrderProgress by animateFloatAsState(
        targetValue = if (showSortOrderDialog) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f, // M3E spatial spring - jelly bounce
            stiffness = 280f
        ),
        label = "sortOrderProgress"
    )

    // Spatial awareness: Adjacent cards push away when dialog opens
    val density = LocalDensity.current
    val appShortcutsOffset by animateFloatAsState(
        targetValue = if (showModeOrderDialog) with(density) { 15.dp.toPx() } else 0f,
        animationSpec = expressiveSpatialSpring,
        label = "appShortcutsOffset"
    )
    val launchModesOffset by animateFloatAsState(
        targetValue = if (showModeOrderDialog) with(density) { (-8).dp.toPx() } else 0f,
        animationSpec = expressiveSpatialSpring,
        label = "launchModesOffset"
    )

    // Sort apps row: AutoOpenCard pushes left when SortOrderDialog opens
    val autoOpenOffset by animateFloatAsState(
        targetValue = if (showSortOrderDialog) with(density) { (-15).dp.toPx() } else 0f,
        animationSpec = expressiveSpatialSpring,
        label = "autoOpenOffset"
    )
    val sortAppsOffset by animateFloatAsState(
        targetValue = if (showSortOrderDialog) with(density) { 8.dp.toPx() } else 0f,
        animationSpec = expressiveSpatialSpring,
        label = "sortAppsOffset"
    )



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoColors.BackgroundDark)
    ) {
        val blurRadius = (modeOrderProgress * 10f + sortOrderProgress * 10f).dp

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = blurRadius),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image Header - full width, no margins, starts from top
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (!isAssistantConfigured) 260.dp else 220.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Background image
                    Image(
                        painter = painterResource(id = R.drawable.bento_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Content column for chip and logo
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 16.dp) // Extra gap below status bar
                    ) {
                        // "Set as default assistant" chip - only shown when not configured
                        if (!isAssistantConfigured) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0x3300BC7D)) // rgba(0, 188, 125, 0.2)
                                    .border(
                                        width = 1.dp,
                                        color = Color(0x4D00BC7D), // rgba(0, 188, 125, 0.3)
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { onSetupAssistant() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ElectricBolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Text(
                                    text = "Set as default assistant",
                                    color = Color.White,
                                    style = BentoTypography.bodyMedium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Logo
                        Image(
                            painter = painterResource(id = R.drawable.swyp_logo),
                            contentDescription = "Swyp Logo",
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Try Swyp Launcher Card - overlapping the header by 16dp
            item {
                TrySwypLauncherCard(
                    onOpenLauncher = onOpenLauncher,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp) // -16dp overlap + compensate for 16dp spacing
                )
            }

            // Launch Modes & App Shortcuts Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LaunchModesCard(
                        count = enabledModesCount,
                        onClick = { showModeOrderDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                launchModesCardBounds = coordinates.boundsInRoot()
                            }
                            .alpha(1f - modeOrderProgress), // Fade out as popup morphs in
                        offsetX = launchModesOffset
                    )
                    AppShortcutsCard(
                        count = shortcutsCount,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    context,
                                    com.joyal.swyplauncher.ShortcutsActivity::class.java
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        offsetX = appShortcutsOffset
                    )
                }
            }

            // Appearance Card with live preview
            item {
                AppearanceCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp),
                    gridSize = gridSize,
                    cornerRadius = cornerRadius,
                    onGridSizeChange = {
                        gridSize = it
                        prefs.edit().putInt("grid_size", gridSize).apply()
                    },
                    onCornerRadiusChange = {
                        cornerRadius = it
                        prefs.edit().putFloat("corner_radius", cornerRadius).apply()
                    },
                    previewApps = installedApps.take(6) // Always pass max for live preview while dragging
                )
            }

            // Auto-open & Sort apps by Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp)
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AutoOpenCard(
                        checked = autoOpenSingleResult,
                        onCheckedChange = {
                            autoOpenSingleResult = it
                            prefs.edit().putBoolean("auto_open_single_result", it).apply()
                        },
                        modifier = Modifier.weight(1f),
                        offsetX = autoOpenOffset
                    )
                    SortAppsByCard(
                        sortOrder = appSortOrder,
                        onClick = { showSortOrderDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                sortAppsCardBounds = coordinates.boundsInRoot()
                            }
                            .alpha(1f - sortOrderProgress), // Fade out as popup morphs in
                        offsetX = sortAppsOffset
                    )
                }
            }

            // Visual Effects Card
            item {
                VisualEffectsCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp),
                    blurEnabled = backgroundBlurEnabled,
                    onBlurEnabledChange = {
                        backgroundBlurEnabled = it
                        preferencesRepository?.setBackgroundBlurEnabled(it)
                        if (it) {
                            blurLevel = 80
                            preferencesRepository?.setBlurLevel(blurLevel)
                        }
                    },
                    blurLevel = blurLevel,
                    onBlurLevelChange = {
                        blurLevel = it
                        preferencesRepository?.setBlurLevel(blurLevel)
                    }
                )
            }

            // Donate Section & Footer
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp)
                ) {
                    DonateSection(scrollProgress = scrollProgress, context = context)

                    Spacer(Modifier.height(48.dp))

                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Privacy Policy",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://github.com/jol333/swyplauncher/blob/main/PRIVACY_POLICY.md")
                                }
                            )
                            Text(
                                text = "•",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Text(
                                text = "Source Code",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://github.com/jol333/swyplauncher")
                                }
                            )
                            Text(
                                text = "•",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Text(
                                text = "License",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://github.com/jol333/SwypLauncher/blob/main/LICENSE.md")
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val versionName = remember {
                            try {
                                context.packageManager.getPackageInfo(
                                    context.packageName,
                                    0
                                ).versionName
                            } catch (e: Exception) {
                                "Unknown"
                            }
                        }

                        Text(
                            text = "Version $versionName",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = Color.Gray.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(Modifier.height(100.dp))
                }
            }
        }

        // Bottom Sheet for Set as Default Assistant
        if (showAssistantBottomSheet) {
            AssistantBottomSheet(
                onDismiss = { userDismissedBottomSheet = true },
                onSetupClick = {
                    onSetupAssistant()
                    userDismissedBottomSheet = true
                }
            )
        }

        // Container transform overlay for ModeOrderDialog
        ContainerTransformPopup(
            isVisible = showModeOrderDialog || modeOrderProgress > 0f,
            progress = modeOrderProgress,
            cardBounds = launchModesCardBounds,
            popupHeight = 480.dp,
            onDismiss = { showModeOrderDialog = false },
            cardContent = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = BentoColors.AccentGreen
                        )
                        Text(
                            text = "LAUNCH MODES",
                            color = BentoColors.TextLabel,
                            style = BentoTypography.labelLarge
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = enabledModesCount.toString(),
                        color = BentoColors.AccentGreen,
                        style = BentoTypography.displayLarge
                    )
                    Text(
                        text = "Modes selected",
                        color = BentoColors.TextSecondary,
                        style = BentoTypography.bodyMedium
                    )
                }
            },
            popupContent = {
                com.joyal.swyplauncher.ModeOrderDialogContent(
                    prefs = prefs,
                    onDismiss = { showModeOrderDialog = false },
                    preferencesRepository = preferencesRepository,
                    onSave = { newCount -> enabledModesCount = newCount }
                )
            }
        )

        // Container transform overlay for SortOrderDialog
        ContainerTransformPopup(
            isVisible = showSortOrderDialog || sortOrderProgress > 0f,
            progress = sortOrderProgress,
            cardBounds = sortAppsCardBounds,
            popupHeight = 380.dp,
            onDismiss = { showSortOrderDialog = false },
            cardContent = {
                Column {
                    Text(
                        text = "SORT APPS BY",
                        color = BentoColors.TextLabel,
                        style = BentoTypography.labelLarge
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = when (appSortOrder) {
                            com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME -> "Name"
                            com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE -> "Usage"
                            com.joyal.swyplauncher.domain.repository.AppSortOrder.CATEGORY -> "Category"
                        },
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = BentoColors.AccentGreen,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(x = (-4).dp)
                    )
                    Text(
                        text = when (appSortOrder) {
                            com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME -> "Alphabetically sorted"
                            com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE -> "Most used apps appear first"
                            com.joyal.swyplauncher.domain.repository.AppSortOrder.CATEGORY -> "Grouped by category"
                        },
                        color = BentoColors.TextMuted,
                        style = BentoTypography.bodyMedium
                    )
                }
            },
            popupContent = {
                com.joyal.swyplauncher.SortOrderDialogContent(
                    currentOrder = appSortOrder,
                    onDismiss = { showSortOrderDialog = false },
                    onSelect = { order ->
                        appSortOrder = order
                        preferencesRepository?.setAppSortOrder(order)
                        showSortOrderDialog = false
                    },
                    context = context
                )
            }
        )
    }
}

@Composable
private fun ContainerTransformPopup(
    isVisible: Boolean,
    progress: Float,
    cardBounds: Rect,
    popupHeight: Dp,
    onDismiss: () -> Unit,
    cardContent: @Composable () -> Unit,
    popupContent: @Composable () -> Unit
) {
    if (isVisible) {
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

        // Calculate popup target dimensions
        val popupWidth = with(density) { 320.dp.toPx() } // Fixed width for popup
        val popupHeightPx = with(density) { popupHeight.toPx() }
        val popupX = (screenWidth - popupWidth) / 2 // Center horizontally
        val popupY = (screenHeight - popupHeightPx) / 2

        // Interpolate from card bounds to popup bounds
        val currentX = cardBounds.left + (popupX - cardBounds.left) * progress
        val currentY = cardBounds.top + (popupY - cardBounds.top) * progress
        val currentWidth = cardBounds.width + (popupWidth - cardBounds.width) * progress
        val currentHeight = cardBounds.height + (popupHeightPx - cardBounds.height) * progress

        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(progress * 0.6f)
                .background(Color.Black)
                .clickable(enabled = progress > 0.5f) { onDismiss() }
        )

        // Morphing container
        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .size(
                    width = with(density) { currentWidth.toDp() },
                    height = with(density) { currentHeight.toDp() }
                )
                .clip(RoundedCornerShape(24.dp))
                .background(BentoColors.CardBackground)
                .border(
                    width = 1.dp,
                    color = BentoColors.BorderLight,
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            // Card content fades out, popup content fades in
            if (progress < 0.5f) {
                // Show card content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(1f - progress * 2)
                        .padding(24.dp)
                ) {
                    cardContent()
                }
            }
            // Popup content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha((progress - 0.5f).coerceIn(0f, 0.5f) * 2)
            ) {
                popupContent()
            }
        }
    }
}