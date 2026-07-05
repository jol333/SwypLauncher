package com.joyal.swyplauncher.ui

import android.content.Intent

import android.os.Build
import android.os.Bundle
import android.view.RoundedCorner
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.hilt.navigation.compose.hiltViewModel
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.ui.screens.HandwritingModeScreen
import com.joyal.swyplauncher.ui.screens.IndexModeScreen
import com.joyal.swyplauncher.ui.screens.KeyboardModeScreen
import com.joyal.swyplauncher.ui.screens.VoiceModeScreen
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.ui.theme.SwypLauncherTheme
import com.joyal.swyplauncher.ui.util.rememberSystemBlurSupported
import com.joyal.swyplauncher.ui.viewmodel.LauncherViewModel
import com.joyal.swyplauncher.ui.viewmodel.ModeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

@AndroidEntryPoint
class AssistActivity : AppCompatActivity() {
    private val prewarmLauncherViewModel: LauncherViewModel by viewModels()
    private var hasResumedOnce = false

    @Inject
    lateinit var preferencesRepository: com.joyal.swyplauncher.domain.repository.PreferencesRepository

    @Inject
    lateinit var getInstalledAppsUseCase: com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase

    @Inject
    lateinit var shortcutSearchRepository: com.joyal.swyplauncher.domain.repository.ShortcutSearchRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Loads apps before composition
        prewarmLauncherViewModel

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        // Make this activity appear as an overlay
        window.apply {
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            // Ensure keyboard adjusts properly without resizing the window
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }

        // Get the initial mode from intent extra (for app shortcuts)
        val initialModeString = intent?.getStringExtra("launcher_mode")
        val initialMode = try {
            initialModeString?.let { LauncherMode.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }

        setContent {
            SwypLauncherTheme {
                AssistantScreen(
                    onDismiss = ::finish,
                    activity = this,
                    preferencesRepository = preferencesRepository,
                    getInstalledAppsUseCase = getInstalledAppsUseCase,
                    shortcutSearchRepository = shortcutSearchRepository,
                    initialMode = initialMode
                )
            }
        }

        // Defer window blur setup to after composition to reduce startup blocking time
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.os.Handler(mainLooper).post {
                setupWindowBlur()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Kill activity when it's no longer visible
        if (hasResumedOnce && !isChangingConfigurations) {
            finish()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        hasResumedOnce = true
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun setupWindowBlur() {
        if (preferencesRepository.isBackgroundBlurEnabled()) {
            val blurLevel = preferencesRepository.getBlurLevel()
            window.setBackgroundBlurRadius(blurLevel)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val attributes = window.attributes
            attributes.setBlurBehindRadius(blurLevel)
            window.attributes = attributes
        } else {
            window.setBackgroundBlurRadius(0)
            val attributes = window.attributes
            attributes.setBlurBehindRadius(0)
            attributes.flags =
                attributes.flags and android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            window.attributes = attributes
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun AssistantScreen(
    onDismiss: () -> Unit,
    activity: AppCompatActivity,
    preferencesRepository: com.joyal.swyplauncher.domain.repository.PreferencesRepository,
    getInstalledAppsUseCase: com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase,
    shortcutSearchRepository: com.joyal.swyplauncher.domain.repository.ShortcutSearchRepository,
    initialMode: LauncherMode? = null,
    isFromAssistant: Boolean = false,
    isFromHome: Boolean = false,
    launcherViewModel: LauncherViewModel = hiltViewModel(),
    modeViewModel: ModeViewModel = hiltViewModel()
) {
    val selectedMode by modeViewModel.selectedMode.collectAsState()
    val modes by modeViewModel.enabledModes.collectAsState()
    val uiState by launcherViewModel.uiState.collectAsState()
    val allAppsRevealed by launcherViewModel.allAppsRevealed.collectAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val motionScheme = remember { MotionScheme.expressive() }
    val context = LocalContext.current

    var showShortcutEditor by remember { mutableStateOf(false) }
    var shortcutAppId by remember { mutableStateOf<String?>(null) }

    // Set initial mode if provided (from app shortcut)
    LaunchedEffect(initialMode) {
        if (initialMode != null) {
            modeViewModel.setMode(initialMode)
        }
    }

    val blurEnabled = rememberEffectiveBlurEnabled(preferencesRepository)

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    var showUsageStatsPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        modeViewModel.refreshEnabledModes()
        if (launcherViewModel.shouldPromptForUsageStatsPermission()) {
            delay(500)
            showUsageStatsPrompt = true
        }
    }

    val initialMode = remember { selectedMode }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val minSheetFraction = if (isLandscape) 0.90f else 0.64f
    val maxSheetFraction = 1f
    val dismissThresholdPx = 300f
    val sheetFractionAnim = remember(isLandscape) { Animatable(minSheetFraction) }
    val dragOffsetY = remember { Animatable(0f) }

    val pagerState = rememberPagerState(
        initialPage = modes.indexOf(selectedMode).coerceAtLeast(0),
        pageCount = { modes.size }
    )

    var programmaticScrolls by remember { mutableStateOf(0) }

    LaunchedEffect(pagerState.currentPage) {
        if (programmaticScrolls == 0) {
            val current = modes[pagerState.currentPage]
            if (current != selectedMode) modeViewModel.setMode(current)
        }
    }

    LaunchedEffect(selectedMode) {
        val targetPage = modes.indexOf(selectedMode)
        if (targetPage != pagerState.currentPage && targetPage >= 0) {
            programmaticScrolls++
            try {
                pagerState.animateScrollToPage(targetPage)
            } finally {
                programmaticScrolls--
            }
        }
    }

    val cornerRadius = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val topLeft =
                view.display?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
            val topRight =
                view.display?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
            val radiusPx = max(topLeft, topRight)
            if (radiusPx > 0) (radiusPx / view.resources.displayMetrics.density).dp else 24.dp
        } else 24.dp
    }

    val handleDismiss: (Boolean) -> Unit = { resetOffset ->
        scope.launch {
            view.clearFocus()
            keyboardController?.hide()
            if (resetOffset) dragOffsetY.snapTo(0f)
            visibleState.targetState = false
            delay(300)
            onDismiss()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidth = maxWidth
        val fullHeightPx = with(density) { maxHeight.toPx() }

        val navbarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
        val bottomInsetPx = max(navbarBottomPx, imeBottomPx)
        val bottomInsetDp = with(density) { bottomInsetPx.toDp() }

        val cornerRadiusPx = with(density) { cornerRadius.toPx() }

        val keyboardMinVisibleContentPx = with(density) { 256.dp.toPx() }

        val navbarTargetFraction = if (fullHeightPx > 0f) {
            minSheetFraction + navbarBottomPx / fullHeightPx
        } else minSheetFraction

        val imeTargetFraction = if (fullHeightPx > 0f && imeBottomPx > 0f) {
            (keyboardMinVisibleContentPx + imeBottomPx + cornerRadiusPx) / fullHeightPx
        } else 0f

        val effectiveMinSheetFraction = maxOf(navbarTargetFraction, imeTargetFraction)
            .coerceIn(minSheetFraction, maxSheetFraction)

        val expansionRangePx = fullHeightPx * (maxSheetFraction - effectiveMinSheetFraction)

        val previousEffectiveMin = remember { mutableStateOf(effectiveMinSheetFraction) }

        LaunchedEffect(effectiveMinSheetFraction) {
            val previousMin = previousEffectiveMin.value
            val sheetWasAtPreviousMin = sheetFractionAnim.value <= previousMin + 0.001f
            previousEffectiveMin.value = effectiveMinSheetFraction

            when {
                sheetFractionAnim.value < effectiveMinSheetFraction -> {
                    sheetFractionAnim.animateTo(
                        effectiveMinSheetFraction,
                        motionScheme.defaultSpatialSpec()
                    )
                }
                sheetWasAtPreviousMin && effectiveMinSheetFraction < sheetFractionAnim.value -> {
                    sheetFractionAnim.animateTo(
                        effectiveMinSheetFraction,
                        motionScheme.defaultSpatialSpec()
                    )
                }
            }
        }

        val deferConfigured = remember { !preferencesRepository.isLoadAllAppsOnOpenEnabled() }
        if (deferConfigured) {
            LaunchedEffect(effectiveMinSheetFraction) {
                snapshotFlow { sheetFractionAnim.value }.collect { fraction ->
                    if (fraction > effectiveMinSheetFraction + 0.06f) {
                        launcherViewModel.revealAllApps()
                    }
                }
            }
            LaunchedEffect(allAppsRevealed) {
                if (allAppsRevealed && sheetFractionAnim.value < maxSheetFraction) {
                    sheetFractionAnim.animateTo(maxSheetFraction, motionScheme.defaultSpatialSpec())
                }
            }
        }

        val nestedScrollConnection = remember(expansionRangePx, effectiveMinSheetFraction) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val dy = available.y

                    if (dy < 0f && dragOffsetY.value > 0f) {
                        val consume = (-dy).coerceAtMost(dragOffsetY.value)
                        scope.launch { dragOffsetY.snapTo(dragOffsetY.value - consume) }
                        return Offset(0f, -consume)
                    }

                    if (dy < 0f && dragOffsetY.value <= 0.01f && sheetFractionAnim.value < maxSheetFraction && expansionRangePx > 0f) {
                        val deltaFraction = (-dy) / expansionRangePx
                        val newFraction =
                            (sheetFractionAnim.value + deltaFraction).coerceAtMost(maxSheetFraction)
                        val consumed = (newFraction - sheetFractionAnim.value) * expansionRangePx

                        if (consumed > 0f) {
                            scope.launch { sheetFractionAnim.snapTo(newFraction) }
                            return Offset(0f, -consumed)
                        }
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    val dy = available.y

                    if (sheetFractionAnim.value > effectiveMinSheetFraction && expansionRangePx > 0f) {
                        val deltaFraction = dy / expansionRangePx
                        val newFraction =
                            (sheetFractionAnim.value - deltaFraction).coerceAtLeast(effectiveMinSheetFraction)
                        val consumed = (sheetFractionAnim.value - newFraction) * expansionRangePx

                        if (consumed > 0f) {
                            scope.launch { sheetFractionAnim.snapTo(newFraction) }
                            return Offset(0f, consumed)
                        }
                    }

                    if (sheetFractionAnim.value <= effectiveMinSheetFraction) {
                        scope.launch { dragOffsetY.snapTo((dragOffsetY.value + dy).coerceAtLeast(0f)) }
                        return Offset(0f, dy)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (available.y < 0f && dragOffsetY.value <= 0.01f && sheetFractionAnim.value < maxSheetFraction) {
                        sheetFractionAnim.animateTo(
                            maxSheetFraction,
                            motionScheme.fastSpatialSpec()
                        )
                        return available
                    }
                    if (available.y < 0f && dragOffsetY.value > 0f) {
                        dragOffsetY.animateTo(0f, motionScheme.fastSpatialSpec())
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    if (sheetFractionAnim.value in (effectiveMinSheetFraction + 0.001f)..<maxSheetFraction) {
                        val target =
                            if (sheetFractionAnim.value >= (effectiveMinSheetFraction + maxSheetFraction) / 2f)
                                maxSheetFraction else effectiveMinSheetFraction
                        sheetFractionAnim.animateTo(target, motionScheme.defaultSpatialSpec())
                    }

                    if (sheetFractionAnim.value <= effectiveMinSheetFraction && dragOffsetY.value > 0f) {
                        if (dragOffsetY.value > dismissThresholdPx) handleDismiss(false)
                        else dragOffsetY.animateTo(0f, motionScheme.fastSpatialSpec())
                    }
                    return Velocity.Zero
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val backdropRange = (maxSheetFraction - effectiveMinSheetFraction).coerceAtLeast(0.0001f)
            val backdropOpacity =
                ((sheetFractionAnim.value - effectiveMinSheetFraction) / backdropRange)
                    .coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backdropOpacity))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { handleDismiss(true) }
                    )
            )

            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInVertically(
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    initialOffsetY = { it }
                ),
                exit = slideOutVertically(
                    animationSpec = motionScheme.slowSpatialSpec(),
                    targetOffsetY = { it }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(sheetFractionAnim.value)
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                        .nestedScroll(nestedScrollConnection)
                        .pointerInput(sheetFractionAnim.value, effectiveMinSheetFraction) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (sheetFractionAnim.value <= effectiveMinSheetFraction) {
                                            if (dragOffsetY.value > dismissThresholdPx) {
                                                handleDismiss(false)
                                            } else if (dragOffsetY.value > 0f) {
                                                dragOffsetY.animateTo(
                                                    0f,
                                                    motionScheme.fastSpatialSpec()
                                                )
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        if (dragOffsetY.value > 0f) {
                                            dragOffsetY.animateTo(
                                                0f,
                                                motionScheme.fastSpatialSpec()
                                            )
                                        }
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    if (sheetFractionAnim.value <= effectiveMinSheetFraction && dragAmount > 0f) {
                                        change.consume()
                                        scope.launch {
                                            dragOffsetY.snapTo(
                                                (dragOffsetY.value + dragAmount).coerceAtLeast(
                                                    0f
                                                )
                                            )
                                        }
                                    } else if (dragAmount < 0f && dragOffsetY.value > 0f) {
                                        change.consume()
                                        val consume = (-dragAmount).coerceAtMost(dragOffsetY.value)
                                        scope.launch { dragOffsetY.snapTo(dragOffsetY.value - consume) }
                                    }
                                }
                            )
                        }
                ) {
                    val backgroundColor = if (blurEnabled)
                        Color(0xAA000000)
                    else Color.Black
                    val noiseBrush = if (blurEnabled) rememberNoiseBrush() else null

                    InvertedCorners(
                        cornerRadius = cornerRadius,
                        backgroundColor = backgroundColor,
                        onDismiss = { handleDismiss(true) }
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(
                                if (blurEnabled && noiseBrush != null) {
                                    Modifier.drawBehind {
                                        drawRect(
                                            brush = noiseBrush,
                                            alpha = 0.15f,
                                            blendMode = BlendMode.Screen
                                        )
                                    }
                                } else Modifier
                            ),
                        color = backgroundColor,
                        contentColor = Color.White,
                        tonalElevation = 0.dp
                    ) {
                        var showHiddenApps by remember { mutableStateOf(false) }

                        val biometricPrompt = remember {
                            val executor = ContextCompat.getMainExecutor(activity)
                            androidx.biometric.BiometricPrompt(
                                activity,
                                executor,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        showHiddenApps = true
                                    }
                                })
                        }

                        val promptInfo = remember {
                            BiometricPrompt.PromptInfo.Builder()
                                .setTitle(context.getString(R.string.biometric_authenticate_title))
                                .setSubtitle(context.getString(R.string.biometric_authenticate_subtitle))
                                .setAllowedAuthenticators(
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                )
                                .build()
                        }

                        val authenticateForHiddenApps: () -> Unit = {
                            biometricPrompt.authenticate(promptInfo)
                        }

                        DisposableEffect(showHiddenApps) {
                            val window = activity.window
                            if (showHiddenApps) {
                                window?.addFlags(
                                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                                )
                            }
                            onDispose {
                                window?.clearFlags(
                                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                                )
                            }
                        }

                        val topPadding = with(LocalDensity.current) {
                            (WindowInsets.statusBars.getTop(this)
                                .toDp() - cornerRadius).coerceAtLeast(0.dp)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomInsetDp)
                        ) {
                            if (isLandscape) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    val notchPadding = with(LocalDensity.current) {
                                        WindowInsets.statusBars.getTop(this).toDp() + 8.dp
                                    }
                                    val leftSystemInset = with(LocalDensity.current) {
                                        WindowInsets.systemBars.getLeft(this, androidx.compose.ui.unit.LayoutDirection.Ltr).toDp()
                                    }
                                    VerticalModeSwitcher(
                                        selectedMode = selectedMode,
                                        enabledModes = modes,
                                        onModeSelected = modeViewModel::setMode,
                                        onShowHiddenApps = authenticateForHiddenApps,
                                        isBlurEnabled = blurEnabled,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(start = leftSystemInset + notchPadding, end = 12.dp)
                                    )

                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(1f),
                                        beyondViewportPageCount = 0,
                                        userScrollEnabled = modes.size > 1
                                    ) { page ->
                                        when (val mode = modes[page]) {
                                            LauncherMode.HANDWRITING -> HandwritingModeScreen(
                                                onDismiss = { handleDismiss(true) },
                                                launcherViewModel = launcherViewModel,
                                                onAddShortcut = { appId ->
                                                    shortcutAppId = appId
                                                    showShortcutEditor = true
                                                }
                                            )

                                            LauncherMode.INDEX -> IndexModeScreen(
                                                onDismiss = { handleDismiss(true) },
                                                launcherViewModel = launcherViewModel,
                                                isBlurEnabled = blurEnabled,
                                                letterSwipeEnabled = modes.size == 1,
                                                onAddShortcut = { appId ->
                                                    shortcutAppId = appId
                                                    showShortcutEditor = true
                                                }
                                            )

                                            LauncherMode.KEYBOARD -> KeyboardModeScreen(
                                                onDismiss = { handleDismiss(true) },
                                                launcherViewModel = launcherViewModel,
                                                isActive = page == pagerState.currentPage,
                                                isInitialMode = mode == initialMode,
                                                isBlurEnabled = blurEnabled,
                                                onAddShortcut = { appId ->
                                                    shortcutAppId = appId
                                                    showShortcutEditor = true
                                                }
                                            )

                                            LauncherMode.VOICE -> VoiceModeScreen(
                                                onDismiss = { handleDismiss(true) },
                                                launcherViewModel = launcherViewModel,
                                                isActive = page == pagerState.currentPage,
                                                onAddShortcut = { appId ->
                                                    shortcutAppId = appId
                                                    showShortcutEditor = true
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {

                                    ModeSwitcher(
                                        selectedMode = selectedMode,
                                        enabledModes = modes,
                                        onModeSelected = modeViewModel::setMode,
                                        onShowHiddenApps = authenticateForHiddenApps,
                                        isBlurEnabled = blurEnabled,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = topPadding)
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    )

                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        beyondViewportPageCount = 0,
                                        userScrollEnabled = modes.size > 1
                                    ) { page ->
                                        when (val mode = modes[page]) {
                                            LauncherMode.HANDWRITING -> HandwritingModeScreen(
                                                onDismiss = { handleDismiss(true) },
                                                launcherViewModel = launcherViewModel,
                                                onAddShortcut = { appId ->
                                                    shortcutAppId = appId
                                                    showShortcutEditor = true
                                                }
                                            )

                                            LauncherMode.INDEX -> IndexModeScreen(
                                                onDismiss = { handleDismiss(true) },
                                                launcherViewModel = launcherViewModel,
                                                isBlurEnabled = blurEnabled,
                                                letterSwipeEnabled = modes.size == 1,
                                                onAddShortcut = { appId ->
                                                    shortcutAppId = appId
                                                    showShortcutEditor = true
                                                }
                                            )

                                            LauncherMode.KEYBOARD -> KeyboardModeScreen(
                                                onDismiss = { handleDismiss(true) },
                                                launcherViewModel = launcherViewModel,
                                                isActive = page == pagerState.currentPage,
                                                isInitialMode = mode == initialMode,
                                                isBlurEnabled = blurEnabled,
                                                onAddShortcut = { appId ->
                                                    shortcutAppId = appId
                                                    showShortcutEditor = true
                                                }
                                            )

                                            LauncherMode.VOICE -> VoiceModeScreen(
                                                onDismiss = { handleDismiss(true) },
                                                launcherViewModel = launcherViewModel,
                                                isActive = page == pagerState.currentPage,
                                                onAddShortcut = { appId ->
                                                    shortcutAppId = appId
                                                    showShortcutEditor = true
                                                }
                                            )
                                        }
                                    }
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = uiState.showHideAppTooltip,
                                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = topPadding + 72.dp, end = 16.dp)
                                ) {
                                    val numButtons = modes.size + 1
                                    val totalGapSize = 8.dp * modes.size
                                    val horizontalPadding = 32.dp
                                    val availableWidth = screenWidth - horizontalPadding - totalGapSize
                                    val buttonWidth = availableWidth / numButtons
                                    val caretOffset = buttonWidth / 2

                                    HideAppTooltip(
                                        onDismiss = { launcherViewModel.dismissHideAppTooltip() },
                                        isBlurEnabled = blurEnabled,
                                        caretOffsetFromRight = caretOffset
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = showHiddenApps,
                                enter = slideInVertically(tween(400, easing = EaseOut)) { it },
                                exit = slideOutVertically(tween(350, easing = EaseIn)) { it }
                            ) {
                                com.joyal.swyplauncher.ui.screens.HiddenAppsScreen(
                                    onDismiss = { showHiddenApps = false },
                                    launcherViewModel = launcherViewModel
                                )
                            }
                        }
                    }
                }
            }

            if (showUsageStatsPrompt) {
                UsageStatsPermissionPromptDialog(
                    onDismiss = {
                        showUsageStatsPrompt = false
                        launcherViewModel.markUsageStatsPermissionPrompted()
                    },
                    onGrantPermission = {
                        launcherViewModel.openUsageStatsSettings()
                        launcherViewModel.markUsageStatsPermissionPrompted()
                        showUsageStatsPrompt = false
                    }
                )
            }
        }

        if (showShortcutEditor) {
            val shortcuts = preferencesRepository.getAppShortcuts()
            com.joyal.swyplauncher.ui.components.ShortcutEditorScreen(
                initialShortcut = "",
                initialSelectedApps = shortcutAppId?.let { setOf(it) } ?: emptySet(),
                existingShortcutNames = shortcuts.keys,
                preferencesRepository = preferencesRepository,
                getInstalledAppsUseCase = getInstalledAppsUseCase,
                shortcutSearchRepository = shortcutSearchRepository,
                onSave = { name, apps ->
                    val newShortcuts = shortcuts + (name to apps)
                    preferencesRepository.setAppShortcuts(newShortcuts)
                    showShortcutEditor = false
                    shortcutAppId = null
                },
                onCancel = {
                    showShortcutEditor = false
                    shortcutAppId = null
                }
            )
        }
    }
}

@Composable
fun UsageStatsPermissionPromptDialog(
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usage_stats_prompt_title)) },
        text = {
            Text(stringResource(R.string.usage_stats_prompt_message))
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onGrantPermission) {
                Text(stringResource(R.string.allow))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now))
            }
        }
    )
}

/**
 * Resolves the effective blur state by combining the user preference with the system's
 * runtime support for cross-window blur (see [rememberSystemBlurSupported]). When the system
 * won't blur bg, the sheet falls back to its opaque background instead of looking like a translucent dim layer.
 */
@Composable
private fun rememberEffectiveBlurEnabled(
    preferencesRepository: com.joyal.swyplauncher.domain.repository.PreferencesRepository
): Boolean {
    val userBlurPref = remember { preferencesRepository.isBackgroundBlurEnabled() }
    if (!userBlurPref) return false
    return rememberSystemBlurSupported()
}

@Composable
private fun rememberNoiseBrush(): ShaderBrush {
    return remember {
        val noiseSize = 128
        val noiseBitmap = android.graphics.Bitmap.createBitmap(
            noiseSize,
            noiseSize,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(noiseBitmap)
        val paint = android.graphics.Paint()
        val random = java.util.Random(42) // Fixed seed for consistent pattern

        for (x in 0 until noiseSize) {
            for (y in 0 until noiseSize) {
                val brightness = random.nextInt(128) + 128 // 128-255 range for lighter noise
                val alpha = random.nextInt(60) + 30 // 30-90 range for visibility
                paint.color = android.graphics.Color.argb(
                    alpha,
                    brightness,
                    brightness,
                    brightness
                )
                canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }

        val imageBitmap = noiseBitmap.asImageBitmap()
        ShaderBrush(
            ImageShader(
                imageBitmap,
                TileMode.Repeated,
                TileMode.Repeated
            )
        )
    }
}



@Composable
private fun InvertedCorners(
    cornerRadius: androidx.compose.ui.unit.Dp,
    backgroundColor: Color,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cornerRadius)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Box(
            modifier = Modifier
                .size(cornerRadius)
                .align(Alignment.TopStart)
                .background(backgroundColor, InvertedCornerShapeTopLeft())
        )
        Box(
            modifier = Modifier
                .size(cornerRadius)
                .align(Alignment.TopEnd)
                .background(backgroundColor, InvertedCornerShapeTopRight())
        )
    }
}

@Composable
fun ModeSwitcher(
    selectedMode: LauncherMode,
    enabledModes: List<LauncherMode>,
    onModeSelected: (LauncherMode) -> Unit,
    onShowHiddenApps: () -> Unit,
    isBlurEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val allModes = listOf(
            Triple(R.drawable.ic_handwriting, stringResource(R.string.mode_draw), LauncherMode.HANDWRITING),
            Triple(R.drawable.ic_index, stringResource(R.string.mode_index), LauncherMode.INDEX),
            Triple(R.drawable.ic_keyboard, stringResource(R.string.mode_type), LauncherMode.KEYBOARD),
            Triple(R.drawable.ic_microphone, stringResource(R.string.mode_voice), LauncherMode.VOICE)
        )

        val modes = enabledModes.mapNotNull { enabledMode ->
            allModes.find { it.third == enabledMode }
        }

        modes.forEachIndexed { index, (icon, label, mode) ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            ModeButton(
                iconRes = icon,
                label = label,
                isSelected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                isBlurEnabled = isBlurEnabled
            )
        }

        Spacer(Modifier.width(8.dp))
        ModeButton(
            iconRes = R.drawable.ic_settings,
            label = stringResource(R.string.settings),
            isSelected = false,
            onClick = {
                context.startActivity(
                    Intent(
                        context,
                        com.joyal.swyplauncher.MainActivity::class.java
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
            },
            onDoubleTap = onShowHiddenApps,
            isBlurEnabled = isBlurEnabled
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun RowScope.ModeButton(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    isBlurEnabled: Boolean = false
) {
    // Use Sunny shape for settings icon, otherwise use cookie shapes
    val isSettingsIcon = iconRes == R.drawable.ic_settings
    val motionScheme = MotionScheme.expressive()

    val (backgroundColor, contentColor) = when {
        isSelected && isBlurEnabled -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        isSelected -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        isSettingsIcon -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val morphProgress = remember { Animatable(if (isSelected) 1f else 0f) }
    val rotationDegrees = remember { Animatable(if (isSelected) 180f else 0f) }

    LaunchedEffect(isSelected) {
        launch {
            morphProgress.animateTo(
                if (isSelected) 1f else 0f,
                motionScheme.slowSpatialSpec()
            )
        }
        launch {
            rotationDegrees.animateTo(
                if (isSelected) 180f else 0f,
                motionScheme.slowSpatialSpec()
            )
        }
    }

    Box(
        modifier = Modifier
            .height(48.dp)
            .weight(1f)
            .then(
                if (onDoubleTap != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onDoubleClick = onDoubleTap
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                }
            )
            .drawBehind {
                rotate(rotationDegrees.value, center) {
                    val androidPath = android.graphics.Path()

                    if (isSettingsIcon) {
                        MaterialShapes.Sunny.toPath(androidPath)
                    } else {
                        Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie7Sided)
                            .toPath(morphProgress.value, androidPath)
                    }

                    val bounds = android.graphics.RectF()
                    androidPath.computeBounds(bounds, true)
                    val scale = size.minDimension / maxOf(bounds.width(), bounds.height())

                    android.graphics.Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            (size.width - bounds.width() * scale) / 2f - bounds.left * scale,
                            (size.height - bounds.height() * scale) / 2f - bounds.top * scale
                        )
                        androidPath.transform(this)
                    }

                    val composePath = androidPath.asComposePath()

                    // Draw filled background
                    drawPath(composePath, backgroundColor)

                    // Draw semi-transparent white outline when blur is enabled
                    if (isBlurEnabled) {
                        drawPath(
                            composePath,
                            color = Color.White.copy(alpha = 0.2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Vertical ModeSwitcher for landscape orientation.
 * Arranges mode buttons in a vertical column instead of horizontal row.
 */
@Composable
fun VerticalModeSwitcher(
    selectedMode: LauncherMode,
    enabledModes: List<LauncherMode>,
    onModeSelected: (LauncherMode) -> Unit,
    onShowHiddenApps: () -> Unit,
    isBlurEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        val allModes = listOf(
            Triple(R.drawable.ic_handwriting, stringResource(R.string.mode_draw), LauncherMode.HANDWRITING),
            Triple(R.drawable.ic_index, stringResource(R.string.mode_index), LauncherMode.INDEX),
            Triple(R.drawable.ic_keyboard, stringResource(R.string.mode_type), LauncherMode.KEYBOARD),
            Triple(R.drawable.ic_microphone, stringResource(R.string.mode_voice), LauncherMode.VOICE)
        )

        val modes = enabledModes.mapNotNull { enabledMode ->
            allModes.find { it.third == enabledMode }
        }

        modes.forEach { (icon, label, mode) ->
            VerticalModeButton(
                iconRes = icon,
                label = label,
                isSelected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                isBlurEnabled = isBlurEnabled
            )
        }

        VerticalModeButton(
            iconRes = R.drawable.ic_settings,
            label = stringResource(R.string.settings),
            isSelected = false,
            onClick = {
                context.startActivity(
                    Intent(
                        context,
                        com.joyal.swyplauncher.MainActivity::class.java
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
            },
            onDoubleTap = onShowHiddenApps,
            isBlurEnabled = isBlurEnabled
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ColumnScope.VerticalModeButton(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    isBlurEnabled: Boolean = false
) {
    val isSettingsIcon = iconRes == R.drawable.ic_settings
    val motionScheme = MotionScheme.expressive()

    val (backgroundColor, contentColor) = when {
        isSelected && isBlurEnabled -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        isSelected -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        isSettingsIcon -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val morphProgress = remember { Animatable(if (isSelected) 1f else 0f) }
    val rotationDegrees = remember { Animatable(if (isSelected) 180f else 0f) }

    LaunchedEffect(isSelected) {
        launch {
            morphProgress.animateTo(
                if (isSelected) 1f else 0f,
                motionScheme.slowSpatialSpec()
            )
        }
        launch {
            rotationDegrees.animateTo(
                if (isSelected) 180f else 0f,
                motionScheme.slowSpatialSpec()
            )
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp) // Fixed size for vertical layout
            .then(
                if (onDoubleTap != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onDoubleClick = onDoubleTap
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                }
            )
            .drawBehind {
                rotate(rotationDegrees.value, center) {
                    val androidPath = android.graphics.Path()

                    if (isSettingsIcon) {
                        MaterialShapes.Sunny.toPath(androidPath)
                    } else {
                        Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie7Sided)
                            .toPath(morphProgress.value, androidPath)
                    }

                    val bounds = android.graphics.RectF()
                    androidPath.computeBounds(bounds, true)
                    val scale = size.minDimension / maxOf(bounds.width(), bounds.height())

                    android.graphics.Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            (size.width - bounds.width() * scale) / 2f - bounds.left * scale,
                            (size.height - bounds.height() * scale) / 2f - bounds.top * scale
                        )
                        androidPath.transform(this)
                    }

                    val composePath = androidPath.asComposePath()

                    drawPath(composePath, backgroundColor)

                    if (isBlurEnabled) {
                        drawPath(
                            composePath,
                            color = Color.White.copy(alpha = 0.2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

class InvertedCornerShapeTopLeft : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, 0f)
            arcTo(Rect(0f, 0f, size.width, size.height), -180f, -90f, false)
            close()
        })
    }
}

class InvertedCornerShapeTopRight : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            moveTo(size.width, size.height)
            lineTo(size.width, 0f)
            arcTo(Rect(0f, 0f, size.width, size.height), 0f, 90f, false)
            close()
        })
    }
}

@Composable
fun HideAppTooltip(
    onDismiss: () -> Unit,
    isBlurEnabled: Boolean,
    caretOffsetFromRight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    rememberCoroutineScope()

    // Auto-dismiss after 5 seconds
    LaunchedEffect(Unit) {
        delay(5000)
        onDismiss()
    }

    val backgroundColor = if (isBlurEnabled)
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
    else
        MaterialTheme.colorScheme.tertiaryContainer

    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss
        ),
        horizontalAlignment = Alignment.End
    ) {
        // Caret pointing upwards
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(8.dp)
                .offset(x = -(caretOffsetFromRight - 8.dp)) // Center caret relative to button center
                .drawBehind {
                    val path = Path().apply {
                        moveTo(size.width / 2f, 0f) // Top point
                        lineTo(size.width, size.height) // Bottom right
                        lineTo(0f, size.height) // Bottom left
                        close()
                    }
                    drawPath(path, backgroundColor)
                }
        )

        // Tooltip content
        Row(
            modifier = Modifier
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.hide_app_tooltip_text),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}