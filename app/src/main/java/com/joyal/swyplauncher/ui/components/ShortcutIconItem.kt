package com.joyal.swyplauncher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.domain.model.ShortcutIcon
import com.joyal.swyplauncher.domain.model.ShortcutSearchItem

/**
 * A single app-shortcut search result. Mirrors [AppIconItem]'s dimensions so shortcuts sit
 * naturally next to apps in the result grid, with the parent app's name as a tiny one-line
 * subtext. Dark icons get a white backing (respecting the user's corner-radius setting) so
 * they stay visible on the assistant's black sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutIconItem(
    shortcut: ShortcutSearchItem,
    onClick: () -> Unit,
    loadIcon: suspend (ShortcutSearchItem) -> ShortcutIcon?,
    modifier: Modifier = Modifier,
    cornerRadiusPercent: Float = 0.85f,
    onLongClick: (() -> Unit)? = null,
    showContextMenu: Boolean = false,
    onDismissMenu: () -> Unit = {},
    onHide: () -> Unit = {},
    onSaveAlias: (String) -> Unit = {},
    onUnhide: () -> Unit = {},
    showHideOption: Boolean = true
) {
    // Same radius math as AppIconItem: 0% = square, 100% = circle for a 56dp icon
    val cornerRadius = 28.dp * cornerRadiusPercent
    val iconShape = RoundedCornerShape(cornerRadius)

    val icon by produceState<ShortcutIcon?>(initialValue = null, key1 = shortcut) {
        value = loadIcon(shortcut)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val currentIcon = icon
            when {
                currentIcon == null -> Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(iconShape)
                        .background(Color.White.copy(alpha = 0.06f))
                )

                currentIcon.isDark -> Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(iconShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    // Inset so the white backing stays visible even for full-bleed icons
                    Image(
                        bitmap = currentIcon.bitmap.asImageBitmap(),
                        contentDescription = shortcut.label,
                        modifier = Modifier.size(44.dp)
                    )
                }

                else -> Image(
                    bitmap = currentIcon.bitmap.asImageBitmap(),
                    contentDescription = shortcut.label,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(iconShape)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = shortcut.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )

            // Parent app name: very small, single line, ellipsized
            Text(
                text = shortcut.appLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )
        }

        if (showContextMenu) {
            ShortcutContextMenu(
                shortcut = shortcut,
                onDismiss = onDismissMenu,
                onHide = onHide,
                onSaveAlias = onSaveAlias,
                onUnhide = onUnhide,
                showHideOption = showHideOption
            )
        }
    }
}
