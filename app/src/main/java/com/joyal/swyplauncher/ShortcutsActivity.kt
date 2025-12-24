package com.joyal.swyplauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.ui.components.ShortcutEditorScreen
import com.joyal.swyplauncher.ui.theme.SwypLauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class ShortcutsActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwypLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    ShortcutsOrchestrator(
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
fun ShortcutsOrchestrator(
    onBack: () -> Unit,
    preferencesRepository: PreferencesRepository,
    getInstalledAppsUseCase: GetInstalledAppsUseCase
) {
    var shortcuts by remember { mutableStateOf(preferencesRepository.getAppShortcuts()) }
    var isEditing by remember { mutableStateOf(false) }
    var editingShortcutData by remember { mutableStateOf<Pair<String, Set<String>>?>(null) }

    // Activity Exit Animation State
    var isFinishing by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditing) {
        isEditing = false
        editingShortcutData = null
    }

    // Intercept back for main screen to trigger exit animation
    BackHandler(enabled = !isEditing && !isFinishing) {
        isFinishing = true
    }

    // Remove uninstalled apps from shortcuts
    LaunchedEffect(Unit) {
        val installedAppIds = withContext(Dispatchers.IO) {
            getInstalledAppsUseCase().map { it.getIdentifier() }.toSet()
        }
        val cleanShortcuts = shortcuts.mapValues { (_, appIds) ->
            appIds.filter { it in installedAppIds }.toSet()
        }.filterValues { it.isNotEmpty() }

        if (cleanShortcuts != shortcuts) {
            shortcuts = cleanShortcuts
            preferencesRepository.setAppShortcuts(cleanShortcuts)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SwipeDismissContainer(
            orientation = Orientation.Horizontal,
            onDismiss = {
                if (isFinishing) {
                    onBack()
                } else {
                    isFinishing = true
                }
            },
            startFromEdge = true,
            manualDismissTrigger = isFinishing
        ) {
            ShortcutsListScreen(
                shortcuts = shortcuts,
                onBack = { isFinishing = true },
                onAddClick = {
                    editingShortcutData = null
                    isEditing = true
                },
                onEditClick = { name, apps ->
                    editingShortcutData = name to apps
                    isEditing = true
                },
                onDeleteClick = { name ->
                    shortcuts =
                        (shortcuts - name).also { preferencesRepository.setAppShortcuts(it) }
                },
                getInstalledAppsUseCase = getInstalledAppsUseCase
            )
        }

        if (isEditing) {
            val editorScrollState = rememberLazyListState()
            SwipeDismissContainer(
                orientation = Orientation.Vertical,
                onDismiss = {
                    isEditing = false
                    editingShortcutData = null
                },
                lazyListState = editorScrollState,
                startFromEdge = true
            ) {
                ShortcutEditorScreen(
                    initialShortcut = editingShortcutData?.first ?: "",
                    initialSelectedApps = editingShortcutData?.second ?: emptySet(),
                    existingShortcutNames = shortcuts.keys,
                    getInstalledAppsUseCase = getInstalledAppsUseCase,
                    onSave = { name, apps ->
                        val newShortcuts = (editingShortcutData?.first?.let { shortcuts - it }
                            ?: shortcuts) + (name to apps)
                        shortcuts = newShortcuts
                        preferencesRepository.setAppShortcuts(newShortcuts)
                        isEditing = false
                        editingShortcutData = null
                    },
                    onCancel = {
                        isEditing = false
                        editingShortcutData = null
                    },
                    scrollState = editorScrollState
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsListScreen(
    shortcuts: Map<String, Set<String>>,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (String, Set<String>) -> Unit,
    onDeleteClick: (String) -> Unit,
    getInstalledAppsUseCase: GetInstalledAppsUseCase
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App shortcuts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                icon = { Icon(Icons.Default.Add, "Create") },
                text = { Text("New shortcut") }
            )
        }
    ) { padding ->
        if (shortcuts.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shortcuts.entries.toList()) { (name, appIds) ->
                    ShortcutCard(
                        name = name,
                        appIds = appIds,
                        onClick = { onEditClick(name, appIds) },
                        onDelete = { onDeleteClick(name) },
                        getInstalledAppsUseCase = getInstalledAppsUseCase
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.AppShortcut,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Text(
            "No shortcuts yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Create custom commands to show specific apps in search results",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ShortcutCard(
    name: String,
    appIds: Set<String>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    getInstalledAppsUseCase: GetInstalledAppsUseCase
) {
    var allApps by remember { mutableStateOf(listOf<AppInfo>()) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { getInstalledAppsUseCase() }
    }

    val selectedApps = remember(allApps, appIds) {
        allApps.filter { it.getIdentifier() in appIds }
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier
                .weight(1f)
                .animateContentSize()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                AnimatedVisibility(
                    visible = selectedApps.isNotEmpty(),
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        OverlappingAppIcons(apps = selectedApps)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun OverlappingAppIcons(apps: List<AppInfo>) {
    val context = LocalContext.current
    val iconSize = 32.dp
    val overlap = 12.dp
    val maxIcons = 4

    // Show first 3 icons + counter, or all if <= 4
    val displayApps = apps.take(if (apps.size > maxIcons) maxIcons - 1 else maxIcons)
    val remainingCount = (apps.size - displayApps.size).takeIf { it > 0 } ?: 0

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            displayApps.forEachIndexed { index, app ->
                Box(
                    modifier = Modifier
                        .padding(start = (iconSize - overlap) * index)
                        .size(iconSize)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .zIndex(index.toFloat())
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(app)
                            .size(96)
                            .memoryCacheKey("app_icon_${app.getIdentifier()}")
                            .diskCacheKey("app_icon_${app.getIdentifier()}")
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            if (remainingCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(start = (iconSize - overlap) * (maxIcons - 1))
                        .size(iconSize)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .zIndex(maxIcons.toFloat()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$remainingCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun SwipeDismissContainer(
    orientation: Orientation,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState? = null,
    startFromEdge: Boolean = false,
    manualDismissTrigger: Boolean = false,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val limit = if (orientation == Orientation.Horizontal) screenWidthPx else screenHeightPx
    val threshold =
        if (orientation == Orientation.Horizontal) screenWidthPx * 0.3f else screenHeightPx * 0.2f

    val initialOffset = if (startFromEdge) limit else 0f
    val offset = remember { Animatable(initialOffset) }

    // Entry Animation
    LaunchedEffect(Unit) {
        if (startFromEdge) {
            offset.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
        }
    }

    // Manual Dismiss Trigger (Exit Animation)
    LaunchedEffect(manualDismissTrigger) {
        if (manualDismissTrigger) {
            offset.animateTo(limit, tween(300))
            onDismiss()
        }
    }

    // State to track if dismiss is allowed for the current gesture
    var allowDismiss by remember { mutableStateOf(true) }

    val isAtTop by remember {
        derivedStateOf {
            lazyListState == null || (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0)
        }
    }

    val nestedScrollConnection = remember(orientation) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (orientation == Orientation.Vertical) {
                    val delta = available.y
                    // Only allow dragging if we are allowed to dismiss and pulling down
                    if (delta > 0 && offset.value > 0 && allowDismiss) {
                        val newOffset = (offset.value + delta).coerceAtMost(limit)
                        scope.launch { offset.snapTo(newOffset) }
                        return Offset(0f, delta)
                    }
                    // If pushing up and we have an offset, consume it
                    if (delta < 0 && offset.value > 0) {
                        val newOffset = (offset.value + delta).coerceAtLeast(0f)
                        scope.launch { offset.snapTo(newOffset) }
                        return Offset(0f, delta)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (orientation == Orientation.Vertical) {
                    val delta = available.y
                    // Only allow dismiss if we are allowed to dismiss (started at top)
                    if (delta > 0 && allowDismiss) {
                        val newOffset = (offset.value + delta).coerceAtMost(limit)
                        scope.launch { offset.snapTo(newOffset) }
                        return Offset(0f, delta)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (orientation == Orientation.Vertical) {
                    if (offset.value > threshold) {
                        offset.animateTo(limit, tween(300))
                        onDismiss()
                    } else {
                        offset.animateTo(0f, spring())
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    val draggableModifier = if (orientation == Orientation.Horizontal) {
        Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                val newOffset = (offset.value + delta).coerceAtLeast(0f)
                scope.launch { offset.snapTo(newOffset) }
            },
            onDragStopped = { velocity ->
                if (offset.value > threshold || velocity > 1000f) {
                    offset.animateTo(limit, tween(300))
                    onDismiss()
                } else {
                    offset.animateTo(0f, spring())
                }
            }
        )
    } else {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.changedToDown() }) {
                        allowDismiss = isAtTop
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .offset {
                if (orientation == Orientation.Horizontal) {
                    IntOffset(offset.value.roundToInt(), 0)
                } else {
                    IntOffset(0, offset.value.roundToInt())
                }
            }
            .then(draggableModifier)
            .nestedScroll(nestedScrollConnection)
    ) {
        content()
    }
}