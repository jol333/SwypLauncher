package com.joyal.swyplauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.joyal.swyplauncher.domain.model.AppInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconItem(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showContextMenu: Boolean = false,
    onDismissMenu: () -> Unit = {},
    onHide: () -> Unit = {},
    onAddShortcut: (() -> Unit)? = null,
    cornerRadiusPercent: Float = 0.75f
) {
    val context = LocalContext.current
    // Calculate corner radius: 0% = 0dp (square), 100% = 28dp (circle, half of icon size)
    val maxRadius = 28.dp
    val cornerRadius = maxRadius * cornerRadiusPercent

    // Icon scale animation state
    val iconScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    
    // Animate icon scale when menu appears/disappears
    LaunchedEffect(showContextMenu) {
        if (showContextMenu) {
            // Dramatic bounce back with slower, more expressive spring
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow // Slower, more dramatic
                )
            )
        } else {
            // Reset to normal when menu is dismissed
            iconScale.snapTo(1f)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .pointerInput(Unit) {
                    var wasLongPress = false
                    
                    detectTapGestures(
                        onPress = {
                            wasLongPress = false
                            
                            // Shrink icon on press
                            scope.launch {
                                iconScale.animateTo(
                                    targetValue = 0.85f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                            
                            val released = tryAwaitRelease()
                            if (released && !wasLongPress) {
                                // Quick tap - restore scale and trigger onClick
                                scope.launch {
                                    iconScale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                                onClick()
                            }
                        },
                        onLongPress = {
                            wasLongPress = true
                            // Long press triggers context menu
                            // Icon will bounce back when showContextMenu becomes true
                            onLongClick?.invoke()
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon with optional badge
            BadgedBox(
                badge = {
                    if (showBadge) {
                        Badge(
                            containerColor = Color.Red,
                            contentColor = Color.Red
                        )
                    }
                }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(app) // Pass the AppInfo object directly
                        .size(168) // 3x size for high-DPI screens (56dp * 3)
                        .memoryCacheKey("app_icon_${app.getIdentifier()}") // Stable cache key
                        .diskCacheKey("app_icon_${app.getIdentifier()}") // Persistent cache key
                        .build(),
                    contentDescription = app.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                        }
                        .clip(RoundedCornerShape(cornerRadius))
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // App label
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp)
            )
        }

        if (showContextMenu) {
            AppContextMenu(
                app = app,
                onDismiss = onDismissMenu,
                onHide = onHide,
                onAddShortcut = onAddShortcut
            )
        }
    }
}