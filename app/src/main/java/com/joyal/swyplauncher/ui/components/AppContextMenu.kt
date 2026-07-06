package com.joyal.swyplauncher.ui.components

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.AppShortcut
import androidx.compose.ui.res.stringResource

@Composable
fun AppContextMenu(
    app: AppInfo,
    onDismiss: () -> Unit,
    onHide: () -> Unit = {},
    onUnhide: () -> Unit = {},
    showHideOption: Boolean = true,
    onAddShortcut: (() -> Unit)? = null,
    cornerRadiusPercent: Float = 0.85f
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var dynamicShortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    var manifestShortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    var pinnedShortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }

    val genie = rememberGenieMenuAnimation(onDismiss)

    // Fetch shortcuts
    LaunchedEffect(app.packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return@LaunchedEffect

        try {
            val launcherApps = context.getSystemService(LauncherApps::class.java)

            // Helper function to fetch shortcuts by type
            fun fetchShortcuts(flag: Int) = runCatching {
                val query = LauncherApps.ShortcutQuery().apply {
                    setQueryFlags(flag)
                    setPackage(app.packageName)
                }
                launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                    ?.mapNotNull { shortcutInfo ->
                        runCatching {
                            AppShortcut(
                                id = shortcutInfo.id,
                                shortLabel = shortcutInfo.shortLabel ?: "",
                                longLabel = shortcutInfo.longLabel,
                                icon = launcherApps.getShortcutIconDrawable(shortcutInfo, 0),
                                packageName = shortcutInfo.`package`
                            )
                        }.getOrNull()
                    } ?: emptyList()
            }.getOrElse { emptyList() }

            dynamicShortcuts = fetchShortcuts(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
            manifestShortcuts = fetchShortcuts(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            pinnedShortcuts = fetchShortcuts(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        } catch (e: Exception) {
            // Silently ignore - app doesn't have permission to access shortcuts
        }
    }

    // Limit shortcuts to max 12, prioritizing MANIFEST and PINNED
    val availableForDynamic = (12 - manifestShortcuts.size - pinnedShortcuts.size).coerceAtLeast(0)
    val limitedDynamicShortcuts = dynamicShortcuts.take(availableForDynamic)
    val totalShortcuts = limitedDynamicShortcuts.size + manifestShortcuts.size + pinnedShortcuts.size

    // Calculate menu height dynamically based on shortcuts
    val menuItemCount = if (onAddShortcut != null) 5 else 4
    val totalHeight = (menuItemCount + totalShortcuts) * 56 + (if (totalShortcuts > 0) 24 else 0) + 16
    val offsetY = with(density) { (-totalHeight).dp.roundToPx() }

    Popup(
        onDismissRequest = { genie.dismiss() },
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(
            focusable = !genie.isDismissing, // Release focus when dismissing
            dismissOnBackPress = !genie.isDismissing,
            dismissOnClickOutside = !genie.isDismissing
        )
    ) {
        Column(
            modifier = genie.graphicsLayerModifier
                .width(IntrinsicSize.Min)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            // Render shortcuts: DYNAMIC, MANIFEST, PINNED
            listOf(
                limitedDynamicShortcuts to LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC,
                manifestShortcuts to LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
                pinnedShortcuts to LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            ).forEach { (shortcuts, flag) ->
                shortcuts.forEach { shortcut ->
                    ShortcutOption(shortcut, cornerRadiusPercent) {
                        runCatching {
                            val launcherApps = context.getSystemService(LauncherApps::class.java)
                            val query = LauncherApps.ShortcutQuery().apply {
                                setQueryFlags(flag)
                                setPackage(app.packageName)
                                setShortcutIds(listOf(shortcut.id))
                            }
                            launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                                ?.firstOrNull()?.let { launcherApps.startShortcut(it, null, null) }
                        }
                        genie.dismiss()
                    }
                }
            }

            // Divider after all shortcuts if any exist
            if (totalShortcuts > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
            }

            // Add shortcut option
            if (onAddShortcut != null) {
                MenuOption(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.set_a_shortcut)) {
                    onAddShortcut()
                    genie.dismiss()
                }
            }

            // App info
            MenuOption(Icons.Outlined.Info, stringResource(R.string.app_info)) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                genie.dismiss()
            }

            // View in Play Store
            MenuOption(Icons.Outlined.ShoppingCart, stringResource(R.string.view_in_app_store)) {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=${app.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        // If no app store is found, fallback to web browser
                        intent.data = Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")
                        context.startActivity(intent)
                    }
                }
                genie.dismiss()
            }

            // Hide/Unhide app
            MenuOption(
                if (showHideOption) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (showHideOption) stringResource(R.string.hide_app) else stringResource(R.string.unhide_app)
            ) {
                if (showHideOption) onHide() else onUnhide()
                genie.dismiss()
            }

            // Uninstall option
            // Store string resources for use in AlertDialog (which can't use stringResource)
            val cannotUninstallTitle = stringResource(R.string.cannot_uninstall)
            val cannotUninstallMessage = stringResource(R.string.cannot_uninstall_message)
            val hideLabel = stringResource(R.string.hide)
            val cancelLabel = stringResource(R.string.cancel)
            MenuOption(Icons.Outlined.Delete, stringResource(R.string.uninstall)) {
                runCatching {
                    val appInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    when {
                        isSystemApp && !showHideOption -> {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", app.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            genie.dismiss()
                        }
                        isSystemApp -> {
                            AlertDialog.Builder(ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog_Alert))
                                .setTitle(cannotUninstallTitle)
                                .setMessage(cannotUninstallMessage)
                                .setPositiveButton(hideLabel) { dialog, _ ->
                                    onHide()
                                    dialog.dismiss()
                                    genie.dismiss()
                                }
                                .setNegativeButton(cancelLabel) { dialog, _ ->
                                    dialog.dismiss()
                                    genie.dismiss()
                                }
                                .show()
                        }
                        else -> {
                            context.startActivity(Intent(Intent.ACTION_DELETE).apply {
                                data = Uri.fromParts("package", app.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            genie.dismiss()
                        }
                    }
                }.onFailure { genie.dismiss() }
            }
        }
    }
}

@Composable
private fun ShortcutOption(
    shortcut: AppShortcut,
    cornerRadiusPercent: Float,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shortcut.icon?.let { drawable ->
            // Convert once, then reuse for both the dark-check and rendering
            val bitmap = remember(drawable) { drawable.toBitmap() }
            val isDark = remember(bitmap) {
                com.joyal.swyplauncher.util.isBitmapDark(bitmap)
            }
            // 24dp icon (max corner radius 12dp = half); dark icons get a larger white backing.
            ShortcutIconGlyph(
                bitmap = bitmap.asImageBitmap(),
                isDark = isDark,
                contentDescription = shortcut.shortLabel.toString(),
                shape = RoundedCornerShape(12.dp * cornerRadiusPercent),
                size = 24.dp,
                darkBackingSize = 28.dp,
                darkInsetSize = 22.dp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = shortcut.shortLabel.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1
        )
    }
}
