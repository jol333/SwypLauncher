package com.joyal.swyplauncher.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.ui.theme.BentoColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    shortcutsCount: Int = 0,
    gesturesCount: Int = 0,
    onLanguageChanged: () -> Unit = {}
) {
    val prefs by prefsFlow.collectAsState()
    var gridSize by remember { mutableStateOf(prefs.getInt("grid_size", 4)) }
    var cornerRadius by remember { mutableStateOf(prefs.getFloat("corner_radius", 0.85f)) }
    var autoOpenSingleResult by remember {
        mutableStateOf(
            prefs.getBoolean(
                "auto_open_single_result",
                false
            )
        )
    }
    var showModeOrderDialog by remember { mutableStateOf(false) }
    var showLaunchOptionsDialog by remember { mutableStateOf(false) }
    var showSortOrderDialog by remember { mutableStateOf(false) }
    var showConversionDialog by remember { mutableStateOf(false) }
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
    var loadAllAppsOnOpen by remember {
        mutableStateOf(
            preferencesRepository?.isLoadAllAppsOnOpenEnabled() ?: true
        )
    }
    var shortcutSearchEnabled by remember {
        mutableStateOf(
            preferencesRepository?.isShortcutSearchEnabled() ?: false
        )
    }
    // Bottom sheet asking the user to become the default assistant before enabling
    // shortcut search (the OS only grants shortcut access to the role holder)
    var showShortcutSearchAssistantSheet by remember { mutableStateOf(false) }
    // Role revoked → shortcut access is gone, so the setting switches itself off
    LaunchedEffect(isAssistantConfigured) {
        if (!isAssistantConfigured && shortcutSearchEnabled) {
            shortcutSearchEnabled = false
            preferencesRepository?.setShortcutSearchEnabled(false)
        }
    }
    var backgroundBlurEnabled by remember {
        mutableStateOf(
            preferencesRepository?.isBackgroundBlurEnabled() ?: false
        )
    }
    var blurLevel by remember { mutableStateOf(preferencesRepository?.getBlurLevel() ?: 80) }
    // Hide the Visual Effects section on devices where cross-window blur is disabled
    val systemBlurSupported = com.joyal.swyplauncher.ui.util.rememberSystemBlurSupported()
    
    // Language state
    var showLanguageDialog by remember { mutableStateOf(false) }
    var currentLanguage by remember {
        mutableStateOf(
            preferencesRepository?.getAppLanguage()
                ?: com.joyal.swyplauncher.domain.model.AppLanguage.SYSTEM
        )
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()



    // Get enabled modes count (mutable to update when dialog saves)
    var enabledModesCount by remember {
        val saved = prefs.getString("enabled_modes", null)
        mutableStateOf(if (saved != null) saved.split(",").size else 4)
    }

    // Conversion categories state
    var enabledConversionCount by remember {
        val saved = preferencesRepository?.getEnabledConversionCategories()
        mutableStateOf(saved?.size ?: TOTAL_CONVERSION_CATEGORIES)
    }
    var enabledConversionCategories by remember {
        val saved = preferencesRepository?.getEnabledConversionCategories()
        mutableStateOf(saved ?: ALL_CONVERSION_CATEGORIES.toSet())
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
    var languageCardBounds by remember { mutableStateOf(Rect.Zero) }
    var launchOptionsCardBounds by remember { mutableStateOf(Rect.Zero) }
    var conversionCardBounds by remember { mutableStateOf(Rect.Zero) }

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
    
    val languageProgress by animateFloatAsState(
        targetValue = if (showLanguageDialog) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f, // M3E spatial spring - jelly bounce
            stiffness = 280f
        ),
        label = "languageProgress"
    )

    val launchOptionsProgress by animateFloatAsState(
        targetValue = if (showLaunchOptionsDialog) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 280f
        ),
        label = "launchOptionsProgress"
    )

    val conversionProgress by animateFloatAsState(
        targetValue = if (showConversionDialog) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 280f
        ),
        label = "conversionProgress"
    )

    // Language & Conversions row pushes away when dialog opens
    val density = LocalDensity.current
    val conversionOffset by animateFloatAsState(
        targetValue = if (showLanguageDialog) with(density) { 15.dp.toPx() } else if (showConversionDialog) with(density) { 8.dp.toPx() } else 0f,
        animationSpec = expressiveSpatialSpring,
        label = "conversionOffset"
    )
    val launchModesOffset by animateFloatAsState(
        targetValue = if (showModeOrderDialog) with(density) { (-8).dp.toPx() } else if (showLaunchOptionsDialog) with(density) { (-15).dp.toPx() } else 0f,
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
    
    // Language card offset when dialog opens
    val languageOffset by animateFloatAsState(
        targetValue = if (showLanguageDialog) with(density) { 8.dp.toPx() } else if (showConversionDialog) with(density) { (-15).dp.toPx() } else 0f,
        animationSpec = expressiveSpatialSpring,
        label = "languageOffset"
    )

    // Launch options card offset when dialog opens
    val launchOptionsOffset by animateFloatAsState(
        targetValue = if (showLaunchOptionsDialog) with(density) { 8.dp.toPx() } else if (showModeOrderDialog) with(density) { 15.dp.toPx() } else 0f,
        animationSpec = expressiveSpatialSpring,
        label = "launchOptionsOffset"
    )

    // App shortcuts and Gestures don't have morphing dialogs, so no offsets are needed.
    val appShortcutsOffset = 0f
    val gesturesOffset = 0f



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoColors.BackgroundDark)
    ) {
        val blurRadius = (modeOrderProgress * 10f + sortOrderProgress * 10f + languageProgress * 10f + launchOptionsProgress * 10f + conversionProgress * 10f).dp

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
                                    text = stringResource(R.string.set_as_default_assistant),
                                    color = BentoColors.TextPrimary,
                                    style = BentoTypography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Logo
                        Image(
                            painter = painterResource(id = R.drawable.swyp_logo),
                            contentDescription = stringResource(R.string.app_name),
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

            // Launch Modes & Ways to open Row
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
                    LaunchOptionsCard(
                        onClick = { showLaunchOptionsDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                launchOptionsCardBounds = coordinates.boundsInRoot()
                            }
                            .alpha(1f - launchOptionsProgress),
                        offsetX = launchOptionsOffset
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

            // App shortcut search toggle (needs the default-assistant role for shortcut access)
            item {
                ShortcutSearchCard(
                    checked = shortcutSearchEnabled,
                    onCheckedChange = { wantEnabled ->
                        when {
                            !wantEnabled -> {
                                shortcutSearchEnabled = false
                                preferencesRepository?.setShortcutSearchEnabled(false)
                            }
                            isAssistantConfigured -> {
                                shortcutSearchEnabled = true
                                preferencesRepository?.setShortcutSearchEnabled(true)
                            }
                            else -> showShortcutSearchAssistantSheet = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp)
                )
            }

            // "When assistant opens" card: load-all-apps toggle + (when supported) blur background.
            item {
                WhenAssistantOpensCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp),
                    loadAllApps = loadAllAppsOnOpen,
                    onLoadAllAppsChange = {
                        loadAllAppsOnOpen = it
                        preferencesRepository?.setLoadAllAppsOnOpen(it)
                    },
                    showBlurOption = systemBlurSupported,
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
            
            // Language & Conversion Categories Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp)
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LanguageCard(
                        currentLanguage = currentLanguage,
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                languageCardBounds = coordinates.boundsInRoot()
                            }
                            .alpha(1f - languageProgress),
                        offsetX = languageOffset
                    )
                    ConversionCategoriesCard(
                        count = enabledConversionCount,
                        onClick = { showConversionDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                conversionCardBounds = coordinates.boundsInRoot()
                            }
                            .alpha(1f - conversionProgress),
                        offsetX = conversionOffset
                    )
                }
            }

            // App Shortcuts & Custom Gestures Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-32).dp)
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                    CustomGesturesCard(
                        count = gesturesCount,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    context,
                                    com.joyal.swyplauncher.CustomGesturesActivity::class.java
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        offsetX = gesturesOffset
                    )
                }
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
                                text = stringResource(R.string.privacy_policy),
                                style = BentoTypography.labelSmall,
                                color = BentoColors.TextMuted,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://github.com/jol333/swyplauncher/blob/main/PRIVACY_POLICY.md")
                                }
                            )
                            Text(
                                text = "•",
                                style = BentoTypography.labelSmall,
                                color = BentoColors.TextMuted
                            )
                            Text(
                                text = stringResource(R.string.source_code),
                                style = BentoTypography.labelSmall,
                                color = BentoColors.TextMuted,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://github.com/jol333/swyplauncher")
                                }
                            )
                            Text(
                                text = "•",
                                style = BentoTypography.labelSmall,
                                color = BentoColors.TextMuted
                            )
                            Text(
                                text = stringResource(R.string.license),
                                style = BentoTypography.labelSmall,
                                color = BentoColors.TextMuted,
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
                                ).versionName ?: context.getString(R.string.version_unknown)
                            } catch (e: Exception) {
                                context.getString(R.string.version_unknown)
                            }
                        }

                        Text(
                            text = stringResource(R.string.version_label, versionName),
                            style = BentoTypography.labelSmall,
                            color = BentoColors.TextMuted.copy(alpha = 0.5f)
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

        // Same sheet, shown when enabling shortcut search without holding the assistant role.
        // The toggle stays off; once the user grants the role and returns, they can enable it.
        if (showShortcutSearchAssistantSheet) {
            AssistantBottomSheet(
                onDismiss = { showShortcutSearchAssistantSheet = false },
                onSetupClick = {
                    onSetupAssistant()
                    showShortcutSearchAssistantSheet = false
                },
                message = stringResource(R.string.shortcut_search_assistant_needed)
            )
        }

        // Container transform overlay for ModeOrderDialog
        ContainerTransformPopup(
            isVisible = showModeOrderDialog || modeOrderProgress > 0f,
            progress = modeOrderProgress,
            cardBounds = launchModesCardBounds,
            onDismiss = { showModeOrderDialog = false },
            cardContent = {
                com.joyal.swyplauncher.ui.settings.LaunchModesCardContent(count = enabledModesCount)
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
            onDismiss = { showSortOrderDialog = false },
            cardContent = {
                com.joyal.swyplauncher.ui.settings.SortAppsByCardContent(sortOrder = appSortOrder)
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

        // Container transform overlay for LanguageDialog
        ContainerTransformPopup(
            isVisible = showLanguageDialog || languageProgress > 0f,
            progress = languageProgress,
            cardBounds = languageCardBounds,
            onDismiss = { showLanguageDialog = false },
            cardContent = {
                com.joyal.swyplauncher.ui.settings.LanguageCardContent(currentLanguage = currentLanguage)
            },
            popupContent = {
                LanguageDialogContent(
                    currentLanguage = currentLanguage,
                    onDismiss = { showLanguageDialog = false },
                    onSelect = { language ->
                        currentLanguage = language
                        preferencesRepository?.setAppLanguage(language)
                        com.joyal.swyplauncher.util.LocaleManager.applyLanguage(language)
                        onLanguageChanged()
                        showLanguageDialog = false
                    }
                )
            }
        )

        // Container transform overlay for LaunchOptionsDialog
        ContainerTransformPopup(
            isVisible = showLaunchOptionsDialog || launchOptionsProgress > 0f,
            progress = launchOptionsProgress,
            cardBounds = launchOptionsCardBounds,
            onDismiss = { showLaunchOptionsDialog = false },
            cardContent = {
                com.joyal.swyplauncher.ui.settings.LaunchOptionsCardContent()
            },
            popupContent = {
                LaunchOptionsDialogContent(
                    onDismiss = { showLaunchOptionsDialog = false }
                )
            }
        )

        // Container transform overlay for ConversionCategoriesDialog
        ContainerTransformPopup(
            isVisible = showConversionDialog || conversionProgress > 0f,
            progress = conversionProgress,
            cardBounds = conversionCardBounds,
            onDismiss = { showConversionDialog = false },
            cardContent = {
                ConversionCategoriesCardContent(count = enabledConversionCount)
            },
            popupContent = {
                ConversionCategoriesDialogContent(
                    initialEnabled = enabledConversionCategories,
                    onDismiss = { showConversionDialog = false },
                    onSave = { categories ->
                        enabledConversionCategories = categories
                        enabledConversionCount = categories.size
                        preferencesRepository?.setEnabledConversionCategories(categories)
                        showConversionDialog = false
                    }
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
    onDismiss: () -> Unit,
    cardContent: @Composable () -> Unit,
    popupContent: @Composable () -> Unit
) {
    // Handle back press when popup is visible (progress > 0.5 means popup is mostly visible)
    BackHandler(enabled = isVisible && progress > 0.5f) {
        onDismiss()
    }
    
    if (isVisible) {
        SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
            val popupWidthPx = 320.dp.roundToPx()
            val popupMaxHeightPx = (constraints.maxHeight * 0.8f).toInt()

            // Measure popup content to get target height
            val popupPlaceable = subcompose("popupContent") {
                Box(modifier = Modifier.width(320.dp)) {
                    popupContent()
                }
            }.first().measure(
                Constraints(
                    minWidth = popupWidthPx,
                    maxWidth = popupWidthPx,
                    minHeight = 0,
                    maxHeight = popupMaxHeightPx
                )
            )
            
            val targetHeightPx = popupPlaceable.height.toFloat()
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()

            // Calculate target position (centered)
            val popupX = (screenWidth - popupWidthPx) / 2f
            val popupY = (screenHeight - targetHeightPx) / 2f

            // Interpolate
            val currentX = cardBounds.left + (popupX - cardBounds.left) * progress
            val currentY = cardBounds.top + (popupY - cardBounds.top) * progress
            val currentWidth = cardBounds.width + (popupWidthPx - cardBounds.width) * progress
            val currentHeight = cardBounds.height + (targetHeightPx - cardBounds.height) * progress

            val contentPlaceable = subcompose("content") {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                width = currentWidth.toDp(),
                                height = currentHeight.toDp()
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(BentoColors.CardBackground)
                            .border(
                                width = 1.dp,
                                color = BentoColors.BorderLight,
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        // Card content fades out
                        if (progress < 0.5f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(1f - progress * 2)
                                    .padding(24.dp)
                            ) {
                                cardContent()
                            }
                        }
                        // Popup content fades in
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha((progress - 0.5f).coerceIn(0f, 0.5f) * 2)
                        ) {
                            popupContent()
                        }
                    }
                }
            }.first().measure(constraints)

            layout(constraints.maxWidth, constraints.maxHeight) {
                contentPlaceable.place(0, 0)
            }
        }
    }
}