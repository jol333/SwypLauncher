package com.joyal.swyplauncher

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt as mathSqrt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.CustomGesture
import com.joyal.swyplauncher.domain.model.InkPoint
import com.joyal.swyplauncher.domain.model.InkStroke
import com.joyal.swyplauncher.domain.model.NormalizedPoint
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.ui.components.AppSelectionItem
import com.joyal.swyplauncher.ui.theme.SwypLauncherTheme
import com.joyal.swyplauncher.util.GestureRecognizer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class CustomGesturesActivity : AppCompatActivity() {
    @Inject lateinit var preferencesRepository: PreferencesRepository
    @Inject lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwypLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    CustomGesturesOrchestrator(
                        onBack = { finish() },
                        preferencesRepository = preferencesRepository,
                        getInstalledAppsUseCase = getInstalledAppsUseCase
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomGesturesOrchestrator(
    onBack: () -> Unit,
    preferencesRepository: PreferencesRepository,
    getInstalledAppsUseCase: GetInstalledAppsUseCase
) {
    var gestures by remember { mutableStateOf(preferencesRepository.getCustomGestures()) }
    var isEditing by remember { mutableStateOf(false) }
    var editingGesture by remember { mutableStateOf<CustomGesture?>(null) }
    var isFinishing by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditing) { isEditing = false; editingGesture = null }
    BackHandler(enabled = !isEditing && !isFinishing) { isFinishing = true }

    LaunchedEffect(Unit) {
        val installedIds = withContext(Dispatchers.IO) {
            getInstalledAppsUseCase().map { it.getIdentifier() }.toSet()
        }
        val cleaned = gestures.mapNotNull { g ->
            val keep = g.appIds.filter { it in installedIds }.toSet()
            if (keep.isEmpty()) null else g.copy(appIds = keep)
        }
        if (cleaned != gestures) {
            gestures = cleaned
            preferencesRepository.setCustomGestures(cleaned)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SwipeDismissContainer(
            orientation = Orientation.Horizontal,
            onDismiss = { if (isFinishing) onBack() else isFinishing = true },
            startFromEdge = true,
            manualDismissTrigger = isFinishing
        ) {
            CustomGesturesListScreen(
                gestures = gestures,
                onBack = { isFinishing = true },
                onAddClick = { editingGesture = null; isEditing = true },
                onEditClick = { editingGesture = it; isEditing = true },
                onDeleteClick = { id ->
                    val updated = gestures.filterNot { it.id == id }
                    gestures = updated
                    preferencesRepository.setCustomGestures(updated)
                },
                getInstalledAppsUseCase = getInstalledAppsUseCase
            )
        }

        if (isEditing) {
            val editorScrollState = rememberLazyListState()
            SwipeDismissContainer(
                orientation = Orientation.Vertical,
                onDismiss = { isEditing = false; editingGesture = null },
                lazyListState = editorScrollState,
                startFromEdge = true
            ) {
                CustomGestureEditorScreen(
                    initial = editingGesture,
                    getInstalledAppsUseCase = getInstalledAppsUseCase,
                    onSave = { gesture ->
                        val idx = gestures.indexOfFirst { it.id == gesture.id }
                        val updated = if (idx >= 0) {
                            gestures.toMutableList().also { it[idx] = gesture }
                        } else {
                            gestures + gesture
                        }
                        gestures = updated
                        preferencesRepository.setCustomGestures(updated)
                        isEditing = false
                        editingGesture = null
                    },
                    onCancel = { isEditing = false; editingGesture = null },
                    scrollState = editorScrollState
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomGesturesListScreen(
    gestures: List<CustomGesture>,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (CustomGesture) -> Unit,
    onDeleteClick: (String) -> Unit,
    getInstalledAppsUseCase: GetInstalledAppsUseCase
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gestures_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Add, stringResource(R.string.create)) },
                text = { Text(stringResource(R.string.new_gesture)) }
            )
        }
    ) { padding ->
        if (gestures.isEmpty()) {
            GestureEmptyState(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.gestures_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                items(gestures, key = { it.id }) { gesture ->
                    GestureCard(
                        gesture = gesture,
                        onClick = { onEditClick(gesture) },
                        onDelete = { onDeleteClick(gesture.id) },
                        getInstalledAppsUseCase = getInstalledAppsUseCase
                    )
                }
            }
        }
    }
}

@Composable
private fun GestureEmptyState(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Gesture,
            contentDescription = null,
            modifier = Modifier.size(80.dp).padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Text(
            stringResource(R.string.no_gestures_yet),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.gestures_empty_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GestureCard(
    gesture: CustomGesture,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    getInstalledAppsUseCase: GetInstalledAppsUseCase
) {
    var allApps by remember { mutableStateOf(listOf<AppInfo>()) }
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { getInstalledAppsUseCase() }
    }
    val selectedApps = remember(allApps, gesture.appIds) {
        allApps.filter { it.getIdentifier() in gesture.appIds }
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GesturePreview(strokes = gesture.previewStrokes, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f).animateContentSize()) {
                AnimatedVisibility(
                    visible = selectedApps.isNotEmpty(),
                    enter = fadeIn() + expandVertically()
                ) {
                    OverlappingAppIcons(apps = selectedApps)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun GesturePreview(
    strokes: List<List<NormalizedPoint>>,
    modifier: Modifier = Modifier
) {
    val sw = with(LocalDensity.current) { 3.dp.toPx() }
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E0E0E))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = 8f
            val s = kotlin.math.min(size.width, size.height) - pad * 2
            val ox = (size.width - s) / 2f; val oy = (size.height - s) / 2f
            strokes.forEach { stroke ->
                if (stroke.size > 1) {
                    val pts = stroke.map { Offset(ox + it.x * s, oy + it.y * s) }
                    drawPath(
                        smoothPath(pts),
                        color = Color.White.copy(alpha = 0.9f),
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomGestureEditorScreen(
    initial: CustomGesture?,
    getInstalledAppsUseCase: GetInstalledAppsUseCase,
    onSave: (CustomGesture) -> Unit,
    onCancel: () -> Unit,
    scrollState: LazyListState
) {
    var selectedApps by remember { mutableStateOf(initial?.appIds ?: emptySet()) }
    val newStrokes = remember { mutableStateListOf<InkStroke>() }
    var useExistingTemplate by remember { mutableStateOf(initial != null) }

    var allApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var showShapeError by remember { mutableStateOf(false) }

    // Adaptive canvas: full size while drawing, compact preview once a shape is captured.
    // Editing an existing gesture starts compact; creating a new one starts expanded.
    var isCanvasExpanded by remember { mutableStateOf(initial == null) }
    // Resetting to 0L cancels the pending auto-collapse (used on drag start / redraw).
    var lastStrokeEndTime by remember { mutableLongStateOf(0L) }

    val searchInteractionSource = remember { MutableInteractionSource() }
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        val apps = withContext(Dispatchers.IO) { getInstalledAppsUseCase() }
        allApps = apps
        selectedApps = selectedApps.filter { it in apps.map { a -> a.getIdentifier() } }.toSet()
        isLoading = false
        if (initial != null && selectedApps.isNotEmpty()) {
            val firstSelectedIndex = apps.indexOfFirst { it.getIdentifier() in selectedApps }
            if (firstSelectedIndex > 0) scrollState.scrollToItem(firstSelectedIndex)
        }
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) { searchFocusRequester.requestFocus(); keyboardController?.show() }
    }

    // Collapse 1.2s after the last stroke ends — the grace window lets the user
    // add more strokes for multi-stroke gestures without the canvas snapping away.
    LaunchedEffect(lastStrokeEndTime) {
        if (lastStrokeEndTime > 0L) {
            kotlinx.coroutines.delay(1200)
            isCanvasExpanded = false
        }
    }

    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }
    val hasShape = newStrokes.isNotEmpty() || (useExistingTemplate && initial != null)
    val showExpandedHeader = isCanvasExpanded || !hasShape

    fun resetToExpanded() {
        newStrokes.clear()
        useExistingTemplate = false
        isCanvasExpanded = true
        lastStrokeEndTime = 0L
    }

    fun trySave() {
        when {
            !hasShape -> {
                showShapeError = true
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.gesture_shape_required)) }
            }
            selectedApps.isEmpty() -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.select_at_least_one_app))
            }
            else -> {
                val template: List<NormalizedPoint>
                val preview: List<List<NormalizedPoint>>
                if (useExistingTemplate && initial != null) {
                    template = initial.template; preview = initial.previewStrokes
                } else {
                    val computed = GestureRecognizer.normalize(newStrokes)
                    if (computed.size != GestureRecognizer.N) {
                        showShapeError = true
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.gesture_shape_too_small))
                        }
                        return
                    }
                    template = computed
                    preview = GestureRecognizer.normalizeStrokesForPreview(newStrokes)
                }
                onSave(
                    CustomGesture(
                        id = initial?.id ?: System.currentTimeMillis().toString(),
                        template = template,
                        previewStrokes = preview,
                        appIds = selectedApps
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial == null) stringResource(R.string.new_gesture)
                        else stringResource(R.string.edit_gesture)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            Box(modifier = Modifier.padding(bottom = 80.dp)) { SnackbarHost(snackbarHostState) }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
                    AnimatedContent(
                        targetState = showExpandedHeader,
                        transitionSpec = {
                            if (targetState) {
                                // compact → expanded: new content slides/scales up
                                (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.96f) +
                                    slideInVertically(tween(350)) { it / 5 }) togetherWith
                                (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f))
                            } else {
                                // expanded → compact: strokes shrink into preview
                                (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.96f)) togetherWith
                                (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.96f) +
                                    slideOutVertically(tween(220)) { it / 5 })
                            }
                        },
                        modifier = Modifier.animateContentSize(tween(350)),
                        label = "gestureHeader"
                    ) { isExpanded ->
                        if (isExpanded) {
                            ExpandedGestureHeader(
                                newStrokes = newStrokes,
                                existingPreview = initial?.previewStrokes.takeIf { useExistingTemplate },
                                showShapeError = showShapeError,
                                hintText = when {
                                    !hasShape -> stringResource(R.string.gesture_canvas_hint_empty)
                                    useExistingTemplate -> stringResource(R.string.gesture_canvas_hint_unchanged)
                                    else -> stringResource(R.string.gesture_canvas_hint_drawn)
                                },
                                onDrawingStarted = {
                                    isCanvasExpanded = true
                                    lastStrokeEndTime = 0L
                                    if (useExistingTemplate) useExistingTemplate = false
                                    if (showShapeError) showShapeError = false
                                },
                                onStrokeEnded = {
                                    lastStrokeEndTime = System.currentTimeMillis()
                                }
                            )
                        } else {
                            val previewStrokes = if (useExistingTemplate && initial != null) {
                                initial.previewStrokes
                            } else {
                                GestureRecognizer.normalizeStrokesForPreview(newStrokes)
                            }
                            CompactGestureHeader(
                                previewStrokes = previewStrokes,
                                onRedraw = ::resetToExpanded
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isSearchVisible) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (selectedApps.isEmpty()) stringResource(R.string.select_apps_to_show)
                                    else stringResource(R.string.apps_to_show_count, selectedApps.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { isSearchVisible = true }) {
                                    Icon(
                                        Icons.Default.Search,
                                        stringResource(R.string.search),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).border(
                                    width = if (isSearchFocused) 1.dp else 0.dp,
                                    color = if (isSearchFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(24.dp)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier.weight(1f)
                                            .focusRequester(searchFocusRequester),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                            onSearch = { keyboardController?.hide() }
                                        ),
                                        interactionSource = searchInteractionSource,
                                        decorationBox = { inner ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    stringResource(R.string.search_apps_placeholder),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        .copy(alpha = 0.7f)
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        stringResource(R.string.clear),
                                        modifier = Modifier.clickable {
                                            if (searchQuery.isNotEmpty()) searchQuery = ""
                                            else isSearchVisible = false
                                        },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val showTopFade by remember { derivedStateOf { scrollState.canScrollBackward } }
                        val showBottomFade by remember { derivedStateOf { scrollState.canScrollForward } }

                        LaunchedEffect(scrollState.isScrollInProgress) {
                            if (scrollState.isScrollInProgress) keyboardController?.hide()
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                state = scrollState,
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(filteredApps, key = { it.getIdentifier() }) { app ->
                                    val isSelected = app.getIdentifier() in selectedApps
                                    AppSelectionItem(
                                        app = app,
                                        isSelected = isSelected,
                                        onClick = {
                                            selectedApps = if (isSelected)
                                                selectedApps - app.getIdentifier()
                                            else selectedApps + app.getIdentifier()
                                        }
                                    )
                                }
                            }
                            if (showTopFade) FadeEdge(top = true)
                            if (showBottomFade) FadeEdge(top = false)
                        }
                    }
                }

                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth()
                ) {
                    GestureSaveButton(onClick = { trySave() })
                }
            }
        }
    }

}

@Composable
private fun ExpandedGestureHeader(
    newStrokes: SnapshotStateList<InkStroke>,
    existingPreview: List<List<NormalizedPoint>>?,
    showShapeError: Boolean,
    hintText: String,
    onDrawingStarted: () -> Unit,
    onStrokeEnded: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        GestureDrawingCanvas(
            newStrokes = newStrokes,
            existingPreview = existingPreview,
            isError = showShapeError,
            onDrawingStarted = onDrawingStarted,
            onStrokeEnded = onStrokeEnded
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = hintText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun CompactGestureHeader(
    previewStrokes: List<List<NormalizedPoint>>,
    onRedraw: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GesturePreview(
            strokes = previewStrokes,
            modifier = Modifier.size(72.dp).clickable(onClick = onRedraw)
        )
        Text(
            text = stringResource(R.string.gesture_canvas_hint_drawn),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRedraw) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = stringResource(R.string.redraw),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BoxScope.FadeEdge(top: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (top) listOf(
                        MaterialTheme.colorScheme.surface, Color.Transparent
                    ) else listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                )
            )
    )
}

@Composable
private fun GestureDrawingCanvas(
    newStrokes: SnapshotStateList<InkStroke>,
    existingPreview: List<List<NormalizedPoint>>?,
    isError: Boolean,
    onDrawingStarted: () -> Unit,
    onStrokeEnded: () -> Unit = {}
) {
    val currentPath = remember { mutableStateListOf<Offset>() }
    val sw = with(LocalDensity.current) { 6.dp.toPx() }
    val staticBorderColor = if (isError) Color(0xFFE53935)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    // ── Pastel gradient intro animation ──────────────────────────────────────
    // Phase 1 (0–2 s): animated gradient sweeps along the border, fades in/out.
    // Phase 2 (2–4 s): same gradient sweeps across the placeholder text.
    val borderAlpha = remember { Animatable(0f) }
    val textAlpha   = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Both animations play in parallel.
        // Fade in together (300 ms), stay fully on for 2700 ms (= 3 s total visible),
        // then fade out together over 2 s → total 5 s.
        launch { textAlpha.animateTo(1f, tween(300)) }
        borderAlpha.animateTo(1f, tween(300))
        kotlinx.coroutines.delay(2700)
        launch { textAlpha.animateTo(0f, tween(2000)) }
        borderAlpha.animateTo(0f, tween(2000))
    }

    // Continuously moving gradient offset (shared by both phases)
    val infiniteTransition = rememberInfiniteTransition(label = "pastelGradient")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation   = tween(1400, easing = LinearEasing),
            repeatMode  = RepeatMode.Restart
        ),
        label = "gradientShift"
    )

    val pastelColors = listOf(
        Color(0xFFFFB3C1), // rose
        Color(0xFFBDB2FF), // lavender
        Color(0xFFA0F0D0), // mint
        Color(0xFFFFD6A5), // peach
        Color(0xFFFFB3C1)  // rose again → seamless loop
    )

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E1E))
            .drawBehind {
                val cornerRadiusPx = 20.dp.toPx()
                val bAlpha = borderAlpha.value

                // Static border — fades out as gradient fades in
                if (bAlpha < 1f) {
                    drawRoundRect(
                        color = staticBorderColor,
                        style = Stroke(1.dp.toPx()),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        alpha = 1f - bAlpha
                    )
                }

                // Animated gradient border — sweeps diagonally
                if (bAlpha > 0f) {
                    val diag = mathSqrt((size.width * size.width + size.height * size.height).toDouble()).toFloat()
                    val sweep = gradientShift * diag * 2f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = pastelColors,
                            start  = Offset(sweep - diag, 0f),
                            end    = Offset(sweep, size.height)
                        ),
                        style  = Stroke(2.dp.toPx()),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        alpha  = bAlpha
                    )
                }
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(newStrokes.size) {
                if (newStrokes.isEmpty()) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onDrawingStarted()
                            currentPath.clear(); currentPath.add(offset)
                        },
                        onDrag = { change, _ -> currentPath.add(change.position) },
                        onDragEnd = {
                            if (currentPath.size > 1) {
                                val now = System.currentTimeMillis()
                                newStrokes.add(InkStroke(currentPath.mapIndexed { i, o ->
                                    InkPoint(o.x, o.y, now + i)
                                }))
                                onStrokeEnded()
                            }
                            currentPath.clear()
                        }
                    )
                }
            }
        ) {
            if (newStrokes.isEmpty() && existingPreview != null) {
                val s = kotlin.math.min(size.width, size.height) * 0.7f
                val ox = (size.width - s) / 2f; val oy = (size.height - s) / 2f
                existingPreview.forEach { stroke ->
                    if (stroke.size > 1) {
                        drawPath(
                            smoothPath(stroke.map { Offset(ox + it.x * s, oy + it.y * s) }),
                            color = Color.White.copy(alpha = 0.75f),
                            style = Stroke(width = sw, cap = StrokeCap.Round)
                        )
                    }
                }
            } else {
                newStrokes.forEach { stroke ->
                    if (stroke.points.size > 1) {
                        drawPath(
                            smoothPath(stroke.points.map { Offset(it.x, it.y) }),
                            color = Color.White,
                            style = Stroke(width = sw, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            if (currentPath.size > 1) {
                drawPath(
                    smoothPath(currentPath),
                    color = Color.White,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }

        // Placeholder text — static when idle, animated pastel gradient during Phase 2.
        // Two Texts are layered: the dim static one fades out as the gradient fades in.
        if (newStrokes.isEmpty() && existingPreview == null && currentPath.isEmpty()) {
            val tAlpha = textAlpha.value
            val estWidthPx = with(density) { 360.dp.toPx() }
            val sweep = gradientShift * estWidthPx * 2f
            val placeholder = stringResource(R.string.gesture_canvas_placeholder)

            Box(modifier = Modifier.align(Alignment.Center)) {
                // Static dim text — fades out as gradient fades in
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.35f * (1f - tAlpha))
                )
                // Gradient text — fades in then out; colour alpha drives the fade
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        brush = Brush.linearGradient(
                            colors = pastelColors.map { it.copy(alpha = tAlpha) },
                            start  = Offset(sweep - estWidthPx, 0f),
                            end    = Offset(sweep, 0f)
                        )
                    ),
                    color = Color.Unspecified
                )
            }
        }

        if (newStrokes.isNotEmpty()) {
            IconButton(
                onClick = { newStrokes.removeAt(newStrokes.size - 1) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.ui.graphics.vector.ImageVector
                        .vectorResource(R.drawable.ic_undo),
                    contentDescription = stringResource(R.string.undo),
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun GestureSaveButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(56.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.save_gesture),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun smoothPath(points: List<Offset>): Path {
    val p = Path()
    if (points.isEmpty()) return p
    if (points.size == 1) { p.moveTo(points[0].x, points[0].y); return p }
    p.moveTo(points[0].x, points[0].y)
    if (points.size == 2) { p.lineTo(points[1].x, points[1].y); return p }
    for (i in 1 until points.size) {
        val a = points[i - 1]; val b = points[i]
        val cx = (a.x + b.x) / 2f; val cy = (a.y + b.y) / 2f
        if (i == 1) p.lineTo(cx, cy) else p.quadraticTo(a.x, a.y, cx, cy)
        if (i == points.size - 1) p.lineTo(b.x, b.y)
    }
    return p
}
