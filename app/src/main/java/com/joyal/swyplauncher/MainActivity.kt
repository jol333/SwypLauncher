package com.joyal.swyplauncher

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.IconPackInfo
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.domain.repository.AppRepository
import com.joyal.swyplauncher.domain.repository.IconPackRepository
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.ui.settings.BentoSettingsScreen
import com.joyal.swyplauncher.ui.theme.BentoColors
import com.joyal.swyplauncher.ui.theme.SwypLauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.joyal.swyplauncher.ui.settings.IconPackShowcaseLoader
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var iconPackRepository: IconPackRepository

    private val isAssistantConfigured = mutableStateOf(false)
    private val installedApps = mutableStateOf<List<AppInfo>>(emptyList())

    // Timestamp to track when user enters settings screen - forces bottom sheet state reset
    private val screenEntryTimestamp = mutableLongStateOf(System.currentTimeMillis())

    // Separate state for shortcuts count to update on resume without affecting bottom sheet
    private val shortcutsCount = mutableIntStateOf(0)
    private val gesturesCount = mutableIntStateOf(0)

    // Icon pack tile: installed packs (none = no tile), the one applied, individually picked icons
    private val iconPacks = mutableStateOf<List<IconPackInfo>>(emptyList())
    private val selectedIconPack = mutableStateOf<String?>(null)
    private val pickedIconCount = mutableIntStateOf(0)
    private val iconPackShowcase = mutableStateOf<List<ImageBitmap>>(emptyList())
    private val assistantRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkAssistantStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dynamic shortcuts based on enabled modes (off the main thread: reads
        // prefs, rasterizes icons and performs a ShortcutManager binder call).
        lifecycleScope.launch(Dispatchers.IO) {
            val enabledModes = preferencesRepository.getEnabledModes()
            com.joyal.swyplauncher.util.AppShortcutManager.updateShortcuts(
                this@MainActivity,
                enabledModes
            )
        }

        // Load installed apps for preview off the main thread (PackageManager scan),
        // then publish to Compose state.
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { getInstalledAppsUseCase() }
            installedApps.value = apps
        }

        // Update timestamp to trigger bottom sheet state reset on fresh activity creation
        screenEntryTimestamp.longValue = System.currentTimeMillis()

        setContent {
            SwypLauncherTheme {
                BentoSettingsScreen(
                    isAssistantConfigured = isAssistantConfigured.value,
                    onSetupAssistant = { assistantRoleLauncher.launch(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) },
                    onOpenLauncher = {
                        startActivity(
                            Intent(
                                this,
                                com.joyal.swyplauncher.ui.AssistActivity::class.java
                            )
                        )
                    },
                    preferencesRepository = preferencesRepository,
                    installedApps = installedApps.value,
                    screenEntryTimestamp = screenEntryTimestamp.longValue,
                    shortcutsCount = shortcutsCount.intValue,
                    gesturesCount = gesturesCount.intValue,
                    iconPacks = iconPacks.value,
                    selectedIconPack = selectedIconPack.value,
                    pickedIconCount = pickedIconCount.intValue,
                    iconPackShowcase = iconPackShowcase.value,
                    onLanguageChanged = {
                        // Invalidate app cache. The activity recreation will trigger a reload with the correct locale.
                        appRepository.invalidateCache()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAssistantStatus()
        // Refresh shortcut/gesture counts when returning from ShortcutsActivity. The reads
        // parse JSON and deserialize gestures, so do that off the main thread and publish
        // the counts back to Compose state.
        lifecycleScope.launch {
            val (shortcuts, gestures) = withContext(Dispatchers.Default) {
                val shortcutCount = (preferencesRepository.getAppShortcuts().keys +
                    preferencesRepository.getShortcutSearchAliases().keys).size
                val gestureCount = preferencesRepository.getCustomGestures().size
                shortcutCount to gestureCount
            }
            shortcutsCount.intValue = shortcuts
            gesturesCount.intValue = gestures
        }
        // Icon packs can be installed, removed or applied while this screen is in the background
        lifecycleScope.launch {
            val packs = try {
                iconPackRepository.getIconPacks()
            } catch (e: Exception) {
                emptyList()
            }
            val picked = withContext(Dispatchers.Default) { iconPackRepository.getIconChoices().size }
            iconPacks.value = packs
            selectedIconPack.value = iconPackRepository.getSelectedIconPack()
            pickedIconCount.intValue = picked
            // The tile's backdrop: rendered in the background once the app list is in
            if (packs.isNotEmpty()) {
                val apps = snapshotFlow { installedApps.value }.first { it.isNotEmpty() }
                iconPackShowcase.value = IconPackShowcaseLoader.load(
                    this@MainActivity, iconPackRepository, packs, selectedIconPack.value, apps
                )
            }
        }
    }

    private fun checkAssistantStatus() {
        // RoleManager.ROLE_ASSISTANT requires API 29, minSdk is 34, so always available
        val isAssistant =
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        // Shortcut search only works while holding the assistant role — turn it off
        // automatically when the role is revoked
        if (!isAssistant) {
            preferencesRepository.setShortcutSearchEnabled(false)
        }
        isAssistantConfigured.value = isAssistant
    }
}

@Composable
fun UsageAccessPermissionDialog(
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usage_access_required)) },
        text = {
            Text(stringResource(R.string.usage_access_message))
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onGrantPermission) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}



// Content composable for container transform (no Dialog wrapper)
@Composable
fun SortOrderDialogContent(
    currentOrder: com.joyal.swyplauncher.domain.repository.AppSortOrder,
    onDismiss: () -> Unit,
    onSelect: (com.joyal.swyplauncher.domain.repository.AppSortOrder) -> Unit,
    context: android.content.Context
) {
    var showPermissionDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(stringResource(R.string.app_sort_order), style = MaterialTheme.typography.titleLarge, color = BentoColors.TextPrimary)
        Spacer(Modifier.height(16.dp))

        com.joyal.swyplauncher.domain.repository.AppSortOrder.entries.forEach { order ->
            Surface(
                onClick = {
                    if (order == com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE) {
                        if (!com.joyal.swyplauncher.util.UsageStatsHelper.hasUsageStatsPermission(context)) {
                            showPermissionDialog = true
                        } else {
                            onSelect(order)
                        }
                    } else {
                        onSelect(order)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = if (order == currentOrder)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = order == currentOrder,
                        onClick = null
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (order) {
                                com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME -> stringResource(R.string.by_name_az)
                                com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE -> stringResource(R.string.by_usage)
                                com.joyal.swyplauncher.domain.repository.AppSortOrder.CATEGORY -> stringResource(R.string.by_category)
                                com.joyal.swyplauncher.domain.repository.AppSortOrder.INSTALL_DATE -> stringResource(R.string.by_install_date)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = when (order) {
                                com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME -> stringResource(R.string.sort_name_desc)
                                com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE -> stringResource(R.string.sort_usage_desc)
                                com.joyal.swyplauncher.domain.repository.AppSortOrder.CATEGORY -> stringResource(R.string.grouped_by_app_category)
                                com.joyal.swyplauncher.domain.repository.AppSortOrder.INSTALL_DATE -> stringResource(R.string.sort_install_date_desc)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    }

    if (showPermissionDialog) {
        UsageAccessPermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onGrantPermission = {
                com.joyal.swyplauncher.util.UsageStatsHelper.openUsageAccessSettings(context)
                showPermissionDialog = false
                onDismiss()
            }
        )
    }
}

// Content composable for container transform (no Dialog wrapper)
@Composable
fun ModeOrderDialogContent(
    preferencesRepository: PreferencesRepository,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit = {}
) {
    var enabledModes by remember { mutableStateOf(preferencesRepository.getEnabledModes()) }
    val hapticFeedback = LocalHapticFeedback.current
    val allModes = LauncherMode.entries
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        enabledModes = enabledModes.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val context = LocalContext.current

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(stringResource(R.string.select_app_launch_modes), style = MaterialTheme.typography.titleLarge, color = BentoColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.long_press_drag_reorder),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.height((allModes.size * 72).dp.coerceAtMost(600.dp))
        ) {
            items(enabledModes.size, key = { enabledModes[it] }) { index ->
                ReorderableItem(reorderableState, key = enabledModes[index]) { isDragging ->
                    val mode = enabledModes[index]
                    val elevation by animateDpAsState(
                        if (isDragging) 8.dp else 0.dp,
                        label = "elevation"
                    )

                    Surface(
                        shadowElevation = elevation,
                        tonalElevation = if (isDragging) 4.dp else 0.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.IconButton(
                                onClick = {},
                                modifier = Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove
                                        )
                                    }
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.DragHandle,
                                    contentDescription = stringResource(R.string.drag_to_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = true,
                                onCheckedChange = { checked ->
                                    if (!checked && enabledModes.size > 1) {
                                        enabledModes = enabledModes - mode
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Disabled modes
            allModes.forEach { mode ->
                if (mode !in enabledModes) {
                    item(key = "disabled_$mode") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.DragHandle,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.3f
                                    )
                                )
                                Text(
                                    mode.name.lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            enabledModes = enabledModes + mode
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.TextButton(
                onClick = {
                    val modesToSave = enabledModes
                    preferencesRepository.setEnabledModes(modesToSave)
                    // Rebuild dynamic shortcuts off the main thread (icon raster + a
                    // ShortcutManager binder call). Uses the Activity lifecycle scope so it
                    // survives the dialog dismissal below; if unavailable, shortcuts
                    // self-correct on the next launch via MainActivity.onCreate.
                    (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
                        com.joyal.swyplauncher.util.AppShortcutManager.updateShortcuts(
                            context,
                            modesToSave
                        )
                    }
                    onSave(enabledModes.size)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}