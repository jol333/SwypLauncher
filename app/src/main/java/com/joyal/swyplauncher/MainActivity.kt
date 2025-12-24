package com.joyal.swyplauncher

import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.domain.usecase.GetInstalledAppsUseCase
import com.joyal.swyplauncher.ui.settings.BentoSettingsScreen
import com.joyal.swyplauncher.ui.theme.SwypLauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var getInstalledAppsUseCase: GetInstalledAppsUseCase

    private val isAssistantConfigured = mutableStateOf(false)
    private val installedApps = mutableStateOf<List<AppInfo>>(emptyList())

    // Timestamp to track when user enters settings screen - forces bottom sheet state reset
    private val screenEntryTimestamp = mutableStateOf(System.currentTimeMillis())

    // Separate state for shortcuts count to update on resume without affecting bottom sheet
    private val shortcutsCount = mutableStateOf(0)
    private val assistantRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkAssistantStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dynamic shortcuts based on enabled modes
        lifecycleScope.launch {
            val enabledModes = preferencesRepository.getEnabledModes()
            com.joyal.swyplauncher.util.AppShortcutManager.updateShortcuts(
                this@MainActivity,
                enabledModes
            )
        }

        // Load installed apps for preview
        lifecycleScope.launch {
            installedApps.value = getInstalledAppsUseCase()
        }

        // Update timestamp to trigger bottom sheet state reset on fresh activity creation
        screenEntryTimestamp.value = System.currentTimeMillis()

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
                    prefsFlow = createPrefsFlow(),
                    preferencesRepository = preferencesRepository,
                    installedApps = installedApps.value,
                    screenEntryTimestamp = screenEntryTimestamp.value,
                    shortcutsCount = shortcutsCount.value
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAssistantStatus()
        // Refresh shortcuts count when returning from ShortcutsActivity
        shortcutsCount.value = preferencesRepository.getAppShortcuts().size
    }

    private fun checkAssistantStatus() {
        // RoleManager.ROLE_ASSISTANT requires API 29, minSdk is 34, so always available
        val isAssistant =
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        lifecycleScope.launch {
            // Always update the preference to reflect the current role status
            preferencesRepository.setDefaultAssistantConfigured(isAssistant)
            isAssistantConfigured.value = isAssistant
        }
    }

    private fun createPrefsFlow(): MutableStateFlow<SharedPreferences> {
        val prefs = getSharedPreferences("swyplauncher_prefs", MODE_PRIVATE)
        val flow = MutableStateFlow(prefs)
        return flow
    }
}

@Composable
fun SortOrderDialog(
    currentOrder: com.joyal.swyplauncher.domain.repository.AppSortOrder,
    onDismiss: () -> Unit,
    onSelect: (com.joyal.swyplauncher.domain.repository.AppSortOrder) -> Unit,
    context: android.content.Context
) {
    var showPermissionDialog by androidx.compose.runtime.remember {
        mutableStateOf(
            false
        )
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.border(
                width = 1.dp,
                color = com.joyal.swyplauncher.ui.theme.BentoColors.BorderLight,
                shape = RoundedCornerShape(24.dp)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("App sort order", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                com.joyal.swyplauncher.domain.repository.AppSortOrder.entries.forEach { order ->
                    Surface(
                        onClick = {
                            // Check permission for USAGE sort order
                            if (order == com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE) {
                                if (!com.joyal.swyplauncher.util.UsageStatsHelper.hasUsageStatsPermission(
                                        context
                                    )
                                ) {
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
                                        com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME -> "By name (A-Z)"
                                        com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE -> "By usage"
                                        com.joyal.swyplauncher.domain.repository.AppSortOrder.CATEGORY -> "By category"
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = when (order) {
                                        com.joyal.swyplauncher.domain.repository.AppSortOrder.NAME -> "Alphabetically sorted"
                                        com.joyal.swyplauncher.domain.repository.AppSortOrder.USAGE -> "Most used apps appear first"
                                        com.joyal.swyplauncher.domain.repository.AppSortOrder.CATEGORY -> "Grouped by app category"
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
                        Text("Cancel")
                    }
                }
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

@Composable
fun UsageAccessPermissionDialog(
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Usage Access Required") },
        text = {
            Text(
                "To sort apps by usage, Swyp Launcher needs access to usage statistics. You'll be taken to the settings screen to grant this permission. \n" +
                        "(Select Swyp Launcher and permit usage access)"
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onGrantPermission) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeOrderDialog(
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    preferencesRepository: PreferencesRepository? = null,
    onSave: (Int) -> Unit = {}
) {
    val saved = prefs.getString("enabled_modes", null)
    val initialModes = if (saved != null) {
        saved.split(",").mapNotNull {
            try {
                LauncherMode.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }
    } else {
        LauncherMode.entries
    }

    var enabledModes by remember { mutableStateOf(initialModes) }
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

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.border(
                width = 1.dp,
                color = com.joyal.swyplauncher.ui.theme.BentoColors.BorderLight,
                shape = RoundedCornerShape(24.dp)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Select app launch modes", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Long press & drag handle to reorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.height(300.dp)
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
                                            contentDescription = "Drag to reorder",
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
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            preferencesRepository?.setEnabledModes(enabledModes)
                            // Update dynamic shortcuts
                            com.joyal.swyplauncher.util.AppShortcutManager.updateShortcuts(
                                context,
                                enabledModes
                            )
                            onSave(enabledModes.size)
                            onDismiss()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}