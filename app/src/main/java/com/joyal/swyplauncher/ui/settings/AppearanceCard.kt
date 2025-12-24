package com.joyal.swyplauncher.ui.settings

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.ui.theme.BentoColors
import kotlin.math.roundToInt

@Composable
fun AppearanceCard(
    modifier: Modifier = Modifier,
    gridSize: Int,
    cornerRadius: Float,
    onGridSizeChange: (Int) -> Unit,
    onCornerRadiusChange: (Float) -> Unit,
    previewApps: List<AppInfo>
) {
    var sliderGridSize by remember(gridSize) { mutableStateOf(gridSize.toFloat()) }
    var sliderCornerRadius by remember(cornerRadius) { mutableStateOf(cornerRadius) }
    var lastGridHaptic by remember { mutableStateOf(gridSize.toFloat()) }
    var lastRadiusHaptic by remember { mutableStateOf(cornerRadius) }
    val view = LocalView.current
    val context = LocalContext.current
    
    // Calculate corner radius for preview: 0% = 0dp (square), 100% = 28dp (circle)
    val maxRadius = 28.dp
    val previewCornerRadius = maxRadius * sliderCornerRadius

    Box(
        modifier = modifier
            .fillMaxWidth()
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
            .padding(24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BentoColors.AccentGreen
                )
                Text(
                    text = "APPEARANCE",
                    color = BentoColors.TextLabel,
                    style = BentoTypography.labelLarge
                )
            }
            
            // Live Preview of Apps
            if (previewApps.isNotEmpty()) {
                val itemCount = sliderGridSize.roundToInt()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    previewApps.take(itemCount).forEach { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(app)
                                    .size(168)
                                    .memoryCacheKey("app_icon_${app.getIdentifier()}")
                                    .build(),
                                contentDescription = app.label,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(previewCornerRadius * 0.85f))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = app.label,
                                color = BentoColors.TextPrimary,
                                style = BentoTypography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Placeholder circles when no apps available
                val itemCount = sliderGridSize.roundToInt()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(itemCount) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(previewCornerRadius * 0.85f))
                                    .background(BentoColors.CardBackgroundLight)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "App",
                                color = BentoColors.TextPrimary,
                                style = BentoTypography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Apps per row slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Apps per row",
                        color = BentoColors.TextPrimary,
                        style = BentoTypography.bodyMedium
                    )
                    Text(
                        text = sliderGridSize.roundToInt().toString(),
                        color = BentoColors.TextSecondary,
                        style = BentoTypography.bodyMedium
                    )
                }
                
                Spacer(Modifier.height(2.dp))
                
                BentoSlider(
                    value = sliderGridSize,
                    onValueChange = { newValue ->
                        if (kotlin.math.abs(newValue - lastGridHaptic) >= 0.5f) {
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            lastGridHaptic = newValue
                        }
                        sliderGridSize = newValue
                    },
                    onValueChangeFinished = { onGridSizeChange(sliderGridSize.roundToInt()) },
                    valueRange = 3f..6f,
                    steps = 2
                )
            }
            
            // Corner Radius slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Corner radius",
                        color = BentoColors.TextPrimary,
                        style = BentoTypography.bodyMedium
                    )
                    Text(
                        text = "${(sliderCornerRadius * 100).roundToInt()}%",
                        color = BentoColors.TextSecondary,
                        style = BentoTypography.bodyMedium
                    )
                }
                
                Spacer(Modifier.height(2.dp))
                
                BentoSlider(
                    value = sliderCornerRadius,
                    onValueChange = { newValue ->
                        // Snap to 75% (squircle default)
                        val snappedValue = if (kotlin.math.abs(newValue - 0.75f) < 0.05f) 0.75f else newValue
                        
                        if (kotlin.math.abs(snappedValue - lastRadiusHaptic) >= 0.05f) {
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            lastRadiusHaptic = snappedValue
                        }
                        sliderCornerRadius = snappedValue
                    },
                    onValueChangeFinished = { onCornerRadiusChange(sliderCornerRadius) },
                    valueRange = 0f..1f,
                    steps = 19
                )
            }
        }
    }
}

@Composable
fun BentoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = BentoColors.AccentGreen,
            inactiveTrackColor = BentoColors.SliderTrack
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
