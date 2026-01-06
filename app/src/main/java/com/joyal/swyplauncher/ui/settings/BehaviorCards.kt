package com.joyal.swyplauncher.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.domain.repository.AppSortOrder
import com.joyal.swyplauncher.ui.theme.BentoColors
import kotlinx.coroutines.launch

/**
 * M3 Expressive Spatial Spring configuration for jelly wobble effect.
 * Low damping (0.5) creates the bouncy overshoot, medium stiffness (350) 
 * ensures responsive feel. These values follow M3E guidelines for spatial springs.
 */
private fun spatialWobbleSpring() = spring<Float>(
    dampingRatio = 0.5f,  // Lower = more bounce
    stiffness = 350f       // Medium stiffness for responsive wobble
)

@Composable
fun AutoOpenCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    offsetX: Float = 0f
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Track card size for calculating relative tap position
    val cardSize = remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    
    // M3E Position-Aware Jelly Wobble Animation States
    // RotationX: tilt forward/backward (positive = top tilts away)
    // RotationY: tilt left/right (positive = right side tilts away)
    val rotationX = remember { Animatable(0f) }
    val rotationY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    
    // Dynamic transform origin based on tap position
    val transformOriginX = remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
    val transformOriginY = remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
    
    Box(
        modifier = modifier
            .onSizeChanged { cardSize.value = it }
            .pointerInput(checked) {  // Moved up: Gesture handler on stable coordinates (before graphicsLayer)
                awaitPointerEventScope {
                    while (true) {
                        // Wait for press
                        val down = awaitFirstDown(requireUnconsumed = false)
                        
                        // Calculate initial press position
                        fun calculateTiltFromPosition(position: Offset): Triple<Float, Float, Float> {
                            val relativeX = if (cardSize.value.width > 0) {
                                (position.x / cardSize.value.width).coerceIn(0f, 1f)
                            } else 0.5f
                            val relativeY = if (cardSize.value.height > 0) {
                                (position.y / cardSize.value.height).coerceIn(0f, 1f)
                            } else 0.5f
                            
                            // Update transform origin to opposite side of press to anchor it
                            transformOriginX.floatValue = 1f - relativeX
                            transformOriginY.floatValue = 1f - relativeY
                            
                            // Calculate tilt based on position
                            val isNearCenter = relativeX in 0.3f..0.7f && relativeY in 0.3f..0.7f
                            val offsetFromCenterX = relativeX - 0.5f
                            val offsetFromCenterY = relativeY - 0.5f
                            
                            val targetRotationY = if (isNearCenter) 0f else offsetFromCenterX * 5f
                            val targetRotationX = if (isNearCenter) 0f else -offsetFromCenterY * 4f
                            val targetScale = if (isNearCenter) 0.95f else 0.98f
                            
                            return Triple(targetRotationX, targetRotationY, targetScale)
                        }
                        
                        // Apply initial press tilt (immediate, no animation for responsiveness)
                        var (currentRotX, currentRotY, currentScale) = calculateTiltFromPosition(down.position)
                        
                        // Animate to pressed state
                        coroutineScope.launch {
                            launch { rotationX.animateTo(currentRotX, spring(dampingRatio = 0.8f, stiffness = 800f)) }
                            launch { rotationY.animateTo(currentRotY, spring(dampingRatio = 0.8f, stiffness = 800f)) }
                            launch { scale.animateTo(currentScale, spring(dampingRatio = 0.8f, stiffness = 800f)) }
                        }
                        
                        // Track drag movements until release
                        var wasDragged = false
                        var isScrollGesture = false
                        val touchSlop = 20f // Threshold for scroll detection
                        
                        do {
                            val event = awaitPointerEvent()
                            val currentPosition = event.changes.firstOrNull()?.position ?: down.position
                            
                            // Calculate drag delta from initial touch
                            val deltaX = currentPosition.x - down.position.x
                            val deltaY = currentPosition.y - down.position.y
                            val dragDistance = (currentPosition - down.position).getDistance()
                            
                            // Detect if this is a vertical scroll gesture (let parent handle it)
                            if (!isScrollGesture && dragDistance > touchSlop) {
                                // If vertical movement is dominant, it's a scroll gesture
                                if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.5f) {
                                    isScrollGesture = true
                                    // Reset card animation and let scroll through
                                    coroutineScope.launch {
                                        launch { rotationX.animateTo(0f, spatialWobbleSpring()) }
                                        launch { rotationY.animateTo(0f, spatialWobbleSpring()) }
                                        launch { scale.animateTo(1f, spatialWobbleSpring()) }
                                    }
                                    // Don't consume - let parent scroll handle it
                                    break
                                } else {
                                    wasDragged = true
                                }
                            }
                            
                            // Only process tilt effect if not scrolling
                            if (!isScrollGesture) {
                                // Update tilt in real-time based on finger position
                                val (newRotX, newRotY, newScale) = calculateTiltFromPosition(currentPosition)
                                
                                // Only update if values changed significantly (avoid jitter)
                                if (kotlin.math.abs(newRotX - currentRotX) > 0.5f || 
                                    kotlin.math.abs(newRotY - currentRotY) > 0.5f) {
                                    currentRotX = newRotX
                                    currentRotY = newRotY
                                    currentScale = newScale
                                    
                                    // Smooth real-time update
                                    coroutineScope.launch {
                                        launch { rotationX.animateTo(currentRotX, spring(dampingRatio = 0.9f, stiffness = 1000f)) }
                                        launch { rotationY.animateTo(currentRotY, spring(dampingRatio = 0.9f, stiffness = 1000f)) }
                                        launch { scale.animateTo(currentScale, spring(dampingRatio = 0.9f, stiffness = 1000f)) }
                                    }
                                }
                                
                                // Consume the event only when we're handling the gesture
                                event.changes.forEach { it.consume() }
                            }
                            
                        } while (event.changes.any { it.pressed })
                        
                        // Skip toggle and bounce-back if it was a scroll gesture
                        if (isScrollGesture) continue
                        
                        // Finger released - bounce back with jelly effect
                        coroutineScope.launch {
                            launch {
                                rotationX.animateTo(0f, spatialWobbleSpring())
                            }
                            launch {
                                rotationY.animateTo(0f, spatialWobbleSpring())
                            }
                            launch {
                                scale.animateTo(1f, spatialWobbleSpring())
                            }
                        }
                        
                        // Toggle switch only on tap (not drag)
                        if (!wasDragged) {
                            onCheckedChange(!checked)
                        }
                    }
                }
            }
            .graphicsLayer {
                translationX = offsetX
                // Apply position-aware 3D wobble transformation
                this.rotationX = rotationX.value
                this.rotationY = rotationY.value
                scaleX = scale.value
                scaleY = scale.value
                // Transform from the tap point for natural feel
                transformOrigin = TransformOrigin(
                    transformOriginX.floatValue,
                    transformOriginY.floatValue
                )
                // Add perspective for 3D depth
                cameraDistance = 12f * density
            }
            .heightIn(min = 180.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x0DFFFFFF), // 5% white
                        Color.Transparent
                    )
                )
            )
            .background(
                brush = Brush.linearGradient(
                    colors = if (checked) {
                        listOf(
                            BentoColors.AccentGreen.copy(alpha = 0.1f),
                            BentoColors.AccentGreenTeal.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            BentoColors.CardBackground,
                            BentoColors.CardBackground
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                color = BentoColors.BorderLight,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            // Custom styled switch - display only, card handles the tap
            Switch(
                checked = checked,
                onCheckedChange = null,  // Disable switch's own click handler - card tap handles it
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BentoColors.AccentGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = BentoColors.CardBackgroundLight,
                    uncheckedBorderColor = Color.Transparent
                )
            )
            
            Spacer(Modifier.weight(1f))
            
            Text(
                text = "Auto-open app",
                color = BentoColors.TextPrimary,
                style = BentoTypography.titleMedium
            )
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = "Open app automatically if single search result",
                color = BentoColors.TextMuted,
                style = BentoTypography.bodyMedium
            )
        }
    }
}

@Composable
fun SortAppsByCard(
    sortOrder: AppSortOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    offsetX: Float = 0f
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX
                // Transform from left edge (toward the adjacent card) for connected feel
                transformOrigin = TransformOrigin(0f, 0.5f)
                // Add perspective for 3D depth
                cameraDistance = 12f * density
            }
            .heightIn(min = 180.dp)
            .fillMaxHeight()
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
        Column {
            Text(
                text = "SORT APPS BY",
                color = BentoColors.TextLabel,
                style = BentoTypography.labelLarge
            )
            
            Spacer(Modifier.weight(1f))
            
            // Large gradient text for sort type
            Text(
                text = when (sortOrder) {
                    AppSortOrder.NAME -> "Name"
                    AppSortOrder.USAGE -> "Usage"
                    AppSortOrder.CATEGORY -> "Category"
                },
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = BentoColors.AccentGreen,
                modifier = Modifier.offset(x = (-4).dp),
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Visible
            )
            
            Text(
                text = when (sortOrder) {
                    AppSortOrder.NAME -> "Alphabetically sorted"
                    AppSortOrder.USAGE -> "Most used apps appear first"
                    AppSortOrder.CATEGORY -> "Grouped by category"
                },
                color = BentoColors.TextMuted,
                style = BentoTypography.bodyMedium
            )
        }
    }
}