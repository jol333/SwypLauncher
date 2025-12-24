package com.joyal.swyplauncher.ui.components

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.AppShortcut
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppContextMenu(
    app: AppInfo,
    onDismiss: () -> Unit,
    onHide: () -> Unit = {},
    onUnhide: () -> Unit = {},
    showHideOption: Boolean = true,
    onAddShortcut: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dynamicShortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    var manifestShortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    var pinnedShortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    
    // Animation states for genie effect
    val slideOffset = remember { Animatable(60f) }
    val alpha = remember { Animatable(0f) }
    val scaleY = remember { Animatable(0.1f) }
    val scaleX = remember { Animatable(0.3f) }
    var isDismissing by remember { mutableStateOf(false) }
    
    val bouncySpring = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
    val smoothSpring = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
    
    // Genie entrance animation - emerging from icon
    LaunchedEffect(Unit) {
        launch { slideOffset.animateTo(0f, bouncySpring) }
        launch { alpha.animateTo(1f, smoothSpring) }
        launch { scaleY.animateTo(1f, bouncySpring) }
        launch { scaleX.animateTo(1f, bouncySpring) }
    }
    
    // Handle dismissal with exit animation
    fun dismissWithAnimation() {
        if (isDismissing) return // Prevent multiple dismissals
        isDismissing = true
        
        val exitSpring = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
        
        // Start exit animation and dismiss shortly after it begins
        scope.launch {
            launch { slideOffset.animateTo(50f, exitSpring) }
            launch { alpha.animateTo(0f, smoothSpring) }
            launch { scaleY.animateTo(0.1f, exitSpring) }
            launch { scaleX.animateTo(0.3f, exitSpring) }
            kotlinx.coroutines.delay(150)
            onDismiss()
        }
    }

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

    // Helper function to launch shortcuts
    fun launchShortcut(shortcut: AppShortcut, flag: Int) {
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
        dismissWithAnimation()
    }

    Popup(
        onDismissRequest = { dismissWithAnimation() },
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(
            focusable = !isDismissing, // Release focus when dismissing
            dismissOnBackPress = !isDismissing,
            dismissOnClickOutside = !isDismissing
        )
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    translationY = slideOffset.value
                    this.alpha = alpha.value
                    this.scaleX = scaleX.value
                    this.scaleY = scaleY.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
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
                    ShortcutOption(shortcut) { launchShortcut(shortcut, flag) }
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
                MenuOption(Icons.AutoMirrored.Outlined.Label, "Set a shortcut") {
                    onAddShortcut()
                    dismissWithAnimation()
                }
            }

            // App info
            MenuOption(Icons.Outlined.Info, "App info") {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", app.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                dismissWithAnimation()
            }

            // 5. View in Play Store
            MenuOption(Icons.Outlined.ShoppingCart, "View in app store") {
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
                dismissWithAnimation()
            }

            // 6. Hide/Unhide app
            MenuOption(
                if (showHideOption) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (showHideOption) "Hide" else "Unhide"
            ) {
                if (showHideOption) onHide() else onUnhide()
                dismissWithAnimation()
            }

            // 7. Uninstall option
            MenuOption(Icons.Outlined.Delete, "Uninstall") {
                runCatching {
                    val appInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    when {
                        isSystemApp && !showHideOption -> {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", app.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            dismissWithAnimation()
                        }
                        isSystemApp -> {
                            AlertDialog.Builder(ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Dialog_Alert))
                                .setTitle("Cannot Uninstall")
                                .setMessage("This app cannot be uninstalled. Would you like to hide it instead?")
                                .setPositiveButton("Hide") { dialog, _ ->
                                    onHide()
                                    dialog.dismiss()
                                    dismissWithAnimation()
                                }
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    dialog.dismiss()
                                    dismissWithAnimation()
                                }
                                .show()
                        }
                        else -> {
                            context.startActivity(Intent(Intent.ACTION_DELETE).apply {
                                data = Uri.fromParts("package", app.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            dismissWithAnimation()
                        }
                    }
                }.onFailure { dismissWithAnimation() }
            }
        }
    }
}

@Composable
private fun ShortcutOption(shortcut: AppShortcut, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shortcut.icon?.let {
            Image(
                bitmap = it.toBitmap().asImageBitmap(),
                contentDescription = shortcut.shortLabel.toString(),
                modifier = Modifier.size(24.dp)
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

@Composable
private fun MenuOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}