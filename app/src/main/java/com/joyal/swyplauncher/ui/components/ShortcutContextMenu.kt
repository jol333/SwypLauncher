package com.joyal.swyplauncher.ui.components

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.ShortcutSearchItem
import kotlinx.coroutines.launch

/**
 * Long-press menu for an app-shortcut search result. Mirrors [AppContextMenu]'s genie
 * animation and styling, but its actions are shortcut-specific: hide this shortcut, set a
 * search magic word, and — for the parent app — app info / store / uninstall. None of these
 * touch the shortcut binder, so the menu works even without the assistant role.
 */
@Composable
fun ShortcutContextMenu(
    shortcut: ShortcutSearchItem,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onSaveAlias: (String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var showAliasDialog by remember { mutableStateOf(false) }

    val slideOffset = remember { Animatable(60f) }
    val alpha = remember { Animatable(0f) }
    val scaleY = remember { Animatable(0.1f) }
    val scaleX = remember { Animatable(0.3f) }
    var isDismissing by remember { mutableStateOf(false) }

    val bouncySpring = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
    val smoothSpring = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)

    LaunchedEffect(Unit) {
        launch { slideOffset.animateTo(0f, bouncySpring) }
        launch { alpha.animateTo(1f, smoothSpring) }
        launch { scaleY.animateTo(1f, bouncySpring) }
        launch { scaleX.animateTo(1f, bouncySpring) }
    }

    fun dismissWithAnimation() {
        if (isDismissing) return
        isDismissing = true
        val exitSpring = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
        scope.launch {
            launch { slideOffset.animateTo(50f, exitSpring) }
            launch { alpha.animateTo(0f, smoothSpring) }
            launch { scaleY.animateTo(0.1f, exitSpring) }
            launch { scaleX.animateTo(0.3f, exitSpring) }
            kotlinx.coroutines.delay(150)
            onDismiss()
        }
    }

    // 4 options, ~56dp each + 24dp padding: mirrors AppContextMenu's height math
    val totalHeight = 4 * 56 + 16
    val offsetY = with(density) { (-totalHeight).dp.roundToPx() }

    Popup(
        onDismissRequest = { dismissWithAnimation() },
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(
            focusable = !isDismissing,
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
            // Set a search shortcut (magic word) for this app shortcut
            ShortcutMenuOption(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.set_a_shortcut)) {
                showAliasDialog = true
            }

            // Hide this shortcut
            ShortcutMenuOption(Icons.Outlined.VisibilityOff, stringResource(R.string.hide_shortcut)) {
                onHide()
                dismissWithAnimation()
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            // Parent app: app info
            ShortcutMenuOption(Icons.Outlined.Info, stringResource(R.string.app_info)) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", shortcut.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                dismissWithAnimation()
            }

            // Parent app: view in Play Store
            ShortcutMenuOption(Icons.Outlined.ShoppingCart, stringResource(R.string.view_in_app_store)) {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=${shortcut.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        intent.data =
                            Uri.parse("https://play.google.com/store/apps/details?id=${shortcut.packageName}")
                        context.startActivity(intent)
                    }
                }
                dismissWithAnimation()
            }

            // Parent app: uninstall (system apps fall back to app details)
            ShortcutMenuOption(Icons.Outlined.Delete, stringResource(R.string.uninstall)) {
                runCatching {
                    val appInfo = context.packageManager.getApplicationInfo(shortcut.packageName, 0)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSystemApp) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", shortcut.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } else {
                        context.startActivity(Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.fromParts("package", shortcut.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }
                dismissWithAnimation()
            }
        }
    }

    if (showAliasDialog) {
        var word by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text(stringResource(R.string.set_a_shortcut)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.shortcut_alias_hint, shortcut.label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(0.dp))
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.magic_word_placeholder)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = word.trim().isNotEmpty(),
                    onClick = {
                        onSaveAlias(word.trim())
                        showAliasDialog = false
                        dismissWithAnimation()
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ShortcutMenuOption(icon: ImageVector, text: String, onClick: () -> Unit) {
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
