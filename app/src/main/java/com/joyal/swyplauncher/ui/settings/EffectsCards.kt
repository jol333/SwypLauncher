package com.joyal.swyplauncher.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.ui.theme.BentoColors
import kotlin.math.roundToInt

@Composable
fun VisualEffectsCard(
    modifier: Modifier = Modifier,
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    blurLevel: Int,
    onBlurLevelChange: (Int) -> Unit
) {
    var sliderBlurLevel by remember(blurLevel) { mutableStateOf(blurLevel.toFloat()) }
    var lastHapticValue by remember { mutableStateOf(blurLevel.toFloat()) }
    val view = LocalView.current

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
        Column {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BentoColors.AccentGreen
                )
                Text(
                    text = "VISUAL EFFECTS",
                    color = BentoColors.TextLabel,
                    style = BentoTypography.labelLarge
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Blur background toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBlurEnabledChange(!blurEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Blur background",
                        color = BentoColors.TextPrimary,
                        style = BentoTypography.titleMedium
                    )
                    Text(
                        text = "Semi-transparent frosted glass look",
                        color = BentoColors.TextMuted,
                        style = BentoTypography.bodyMedium
                    )
                }
                
                Switch(
                    checked = blurEnabled,
                    onCheckedChange = onBlurEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BentoColors.AccentGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = BentoColors.CardBackgroundLight,
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
            
            // Blur intensity slider (only visible when enabled)
            // The 16dp spacing is INSIDE AnimatedVisibility so it animates out smoothly
            AnimatedVisibility(
                visible = blurEnabled,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                Column {
                    Spacer(Modifier.height(24.dp)) // 16dp gap + 8dp extra spacing
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Blur intensity",
                            color = BentoColors.TextPrimary,
                            style = BentoTypography.bodyMedium
                        )
                        Text(
                            text = sliderBlurLevel.roundToInt().toString(),
                            color = BentoColors.TextSecondary,
                            style = BentoTypography.bodyMedium
                        )
                    }
                    
                    Spacer(Modifier.height(2.dp))
                    
                    BentoSlider(
                        value = sliderBlurLevel,
                        onValueChange = { newValue ->
                            if (kotlin.math.abs(newValue - lastHapticValue) >= 5f) {
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                                lastHapticValue = newValue
                            }
                            sliderBlurLevel = newValue
                        },
                        onValueChangeFinished = { onBlurLevelChange(sliderBlurLevel.roundToInt()) },
                        valueRange = 10f..150f,
                        steps = 29
                    )
                }
            }
        }
    }
}

@Composable
fun DonateSection(
    scrollProgress: Float,
    context: Context
) {
    // Adjust progress to start earlier and finish before end of scroll
    val adjustedProgress = (scrollProgress * 2.5f).coerceIn(0f, 1f)

    // Animate scale based on scroll progress with spring animation
    val scale by animateFloatAsState(
        targetValue = 0.7f + (adjustedProgress * 0.3f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    // Animate rotation for playful effect
    val rotation by animateFloatAsState(
        targetValue = (1f - adjustedProgress) * -10f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rotation"
    )
    
    // Icon scale with extra bounce
    val iconScale by animateFloatAsState(
        targetValue = if (adjustedProgress > 0.8f) 1.1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                alpha = (adjustedProgress * 2f).coerceIn(0f, 1f)
                translationY = (1f - adjustedProgress) * 50f
            }
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
                    colors = listOf(
                        BentoColors.DonateYellow.copy(alpha = 0.1f),
                        BentoColors.DonateOrange.copy(alpha = 0.2f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = BentoColors.DonateYellow.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/joyel"))
                context.startActivity(intent)
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Coffee icon with custom styling
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            ) {
                // Yellow background circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BentoColors.DonateYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_buy_me_coffee),
                        contentDescription = "Buy me a coffee",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Unspecified
                    )
                }
                
                // Heart badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFADA7)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "❤",
                        fontSize = 12.sp
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Buy me a coffee",
                    color = BentoColors.TextPrimary,
                    style = BentoTypography.titleLarge
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = "If this app saves you time, consider buying me a coffee. Your support helps fuel my free side projects.",
                    color = BentoColors.TextSecondary,
                    style = BentoTypography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantBottomSheet(
    onDismiss: () -> Unit,
    onSetupClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BentoColors.CardBackground,
        dragHandle = null // Remove default handle to allow gradient to cover top
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BentoColors.AccentGreen.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        endY = 400f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Custom Drag Handle
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BentoColors.TextLabel.copy(alpha = 0.5f))
                )

                Spacer(Modifier.height(8.dp))

                Icon(
                    imageVector = Icons.Outlined.Rocket,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = BentoColors.AccentGreen
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "Set as default assistant",
                    color = BentoColors.TextPrimary,
                    style = BentoTypography.titleLarge
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = "To launch using navigation gesture and power button",
                    color = BentoColors.TextSecondary,
                    style = BentoTypography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(24.dp))

                // Instruction Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = BentoColors.CardBackgroundLight.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "How to enable:",
                            color = BentoColors.AccentGreen,
                            style = BentoTypography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Click on 'Default digital assistant app' in the next screen and choose 'Swyp Launcher'",
                            color = BentoColors.TextSecondary,
                            style = BentoTypography.bodyMedium
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Setup button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(BentoColors.AccentGreen)
                        .clickable { onSetupClick() },
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Set up now",
                        color = Color.White,
                        style = BentoTypography.labelButton
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Skip button
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Skip for now",
                        color = BentoColors.TextMuted,
                        style = BentoTypography.bodyMedium
                    )
                }
            }
        }
    }
}