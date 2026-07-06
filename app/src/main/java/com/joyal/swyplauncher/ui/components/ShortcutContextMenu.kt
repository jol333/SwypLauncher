package com.joyal.swyplauncher.ui.components

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * Long-press menu for an app-shortcut search result. Mirrors [AppContextMenu]'s genie
 * animation and styling (via [rememberGenieMenuAnimation]/[MenuOption]), but its actions are
 * shortcut-specific: hide this shortcut, set a search magic word, and — for the parent app —
 * app info / store / uninstall. None of these touch the shortcut binder, so the menu works even
 * without the assistant role.
 */
@Composable
fun ShortcutContextMenu(
    shortcut: ShortcutSearchItem,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onSaveAlias: (String) -> Unit,
    onUnhide: () -> Unit = {},
    showHideOption: Boolean = true
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var showAliasDialog by remember { mutableStateOf(false) }

    val genie = rememberGenieMenuAnimation(onDismiss)

    // 4 options, ~56dp each + 24dp padding: mirrors AppContextMenu's height math
    val totalHeight = 4 * 56 + 16
    val offsetY = with(density) { (-totalHeight).dp.roundToPx() }

    Popup(
        onDismissRequest = { genie.dismiss() },
        offset = IntOffset(0, offsetY),
        properties = PopupProperties(
            focusable = !genie.isDismissing,
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
            // Set a search shortcut (magic word) for this app shortcut
            MenuOption(
                Icons.AutoMirrored.Outlined.Label,
                stringResource(R.string.set_a_shortcut)
            ) {
                showAliasDialog = true
            }

            // Hide / un-hide this shortcut
            MenuOption(
                if (showHideOption) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (showHideOption) stringResource(R.string.hide_shortcut)
                else stringResource(R.string.unhide_shortcut)
            ) {
                if (showHideOption) onHide() else onUnhide()
                genie.dismiss()
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            // Parent app: app info
            MenuOption(Icons.Outlined.Info, stringResource(R.string.app_info)) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", shortcut.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                genie.dismiss()
            }

            // Parent app: view in Play Store
            MenuOption(
                Icons.Outlined.ShoppingCart,
                stringResource(R.string.view_in_app_store)
            ) {
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
                genie.dismiss()
            }

            // Parent app: uninstall (system apps fall back to app details)
            MenuOption(Icons.Outlined.Delete, stringResource(R.string.uninstall)) {
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
                genie.dismiss()
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
                    Spacer(Modifier.height(8.dp))
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
                        genie.dismiss()
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
