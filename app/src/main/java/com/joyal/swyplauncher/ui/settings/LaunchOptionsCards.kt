package com.joyal.swyplauncher.ui.settings

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.app.role.RoleManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.ui.theme.BentoColors
import com.joyal.swyplauncher.widget.SwypLauncherWidgetReceiver

@Composable
fun LaunchOptionsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    offsetX: Float = 0f
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX
                transformOrigin = TransformOrigin(1f, 0.5f)
                cameraDistance = 12f * density
            }
            .height(192.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        BentoColors.CardBackground.copy(alpha = 0.6f),
                        BentoColors.CardBackground
                    )
                )
            )
            .border(
                width = 1.dp,
                color = BentoColors.BorderLight,
                shape = RoundedCornerShape(24.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            }
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RocketLaunch,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BentoColors.AccentGreen
                )
                Text(
                    text = stringResource(R.string.launch_options_card),
                    color = BentoColors.TextLabel,
                    style = BentoTypography.labelLarge
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(R.string.access_assistant),
                color = BentoColors.TextMuted,
                style = BentoTypography.bodyMedium
            )
        }
    }
}

@Composable
fun LaunchOptionsDialogContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isDefaultAssistant by remember { mutableStateOf(checkIsDefaultAssistant(context)) }
    var isQsTileAdded by remember {
        mutableStateOf(
            context.getSharedPreferences("swyplauncher_prefs", Context.MODE_PRIVATE)
                .getBoolean("qs_tile_added", false)
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultAssistant = checkIsDefaultAssistant(context)
                isQsTileAdded = context.getSharedPreferences("swyplauncher_prefs", Context.MODE_PRIVATE)
                    .getBoolean("qs_tile_added", false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            stringResource(R.string.launch_options_popup_title),
            style = MaterialTheme.typography.titleLarge,
            color = BentoColors.TextPrimary
        )
        Spacer(Modifier.height(16.dp))

        // 1. Default digital assistant
        LaunchOptionRow(
            iconVector = Icons.Outlined.Assistant,
            label = stringResource(R.string.default_digital_assistant),
            isDone = isDefaultAssistant,
            showChevron = true,
            onClick = {
                val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )

        Spacer(Modifier.height(8.dp))

        // 2. Add home screen widget
        LaunchOptionRow(
            iconVector = Icons.Outlined.Widgets,
            label = stringResource(R.string.add_home_screen_widget),
            isDone = false,
            showChevron = false,
            onClick = { requestPinWidget(context) }
        )

        Spacer(Modifier.height(8.dp))

        // 3. Add shortcut
        LaunchOptionRow(
            iconVector = Icons.Rounded.AppShortcut,
            label = stringResource(R.string.add_shortcut_home),
            isDone = false,
            showChevron = false,
            onClick = { requestPinShortcut(context) }
        )

        Spacer(Modifier.height(8.dp))

        // 4. Quick settings tile
        LaunchOptionRow(
            iconVector = Icons.Outlined.DashboardCustomize,
            label = stringResource(R.string.quick_settings_tile),
            isDone = isQsTileAdded,
            showChevron = false,
            onClick = { requestAddTile(context) }
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun LaunchOptionRow(
    iconVector: ImageVector,
    label: String,
    isDone: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BentoColors.CardBackground)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(BentoColors.AccentGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = BentoColors.AccentGreen
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = BentoColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )

        if (isDone) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = BentoColors.AccentGreen
            )
        } else if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = BentoColors.TextMuted
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = BentoColors.TextMuted
            )
        }
    }
}

private fun checkIsDefaultAssistant(context: Context): Boolean {
    return try {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
    } catch (e: Exception) {
        false
    }
}

private fun requestPinWidget(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val widgetProvider = ComponentName(context, SwypLauncherWidgetReceiver::class.java)
    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        appWidgetManager.requestPinAppWidget(widgetProvider, null, null)
    }
}

private fun requestPinShortcut(context: Context) {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
    if (!shortcutManager.isRequestPinShortcutSupported) return

    val intent = Intent(context, com.joyal.swyplauncher.ui.AssistActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    val shortcutInfo = ShortcutInfo.Builder(context, "swyp_launcher_pinned")
        .setShortLabel(context.getString(R.string.app_name))
        .setLongLabel(context.getString(R.string.app_name))
        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()

    shortcutManager.requestPinShortcut(shortcutInfo, null)
}

private fun requestAddTile(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        statusBarManager?.requestAddTileService(
            ComponentName(context, com.joyal.swyplauncher.service.SwypLauncherTileService::class.java),
            context.getString(R.string.swyp_launcher_tile_label),
            android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_quick_tile),
            context.mainExecutor
        ) { /* result callback - ignored */ }
    }
}
