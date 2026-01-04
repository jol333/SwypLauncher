package com.joyal.swyplauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.ui.theme.BentoColors

@Composable
fun TrySwypLauncherCard(
    onOpenLauncher: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF111113)) // Base color
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x0D4AD7A3), // rgba(74, 215, 163, 0.05)
                        Color(0x8018181B)  // rgba(24, 24, 27, 0.5)
                    )
                )
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x0DF0B100), // rgba(240, 177, 0, 0.05)
                        Color(0x0DFF6900)  // rgba(255, 105, 0, 0.05)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color(0x1AFFFFFF), // rgba(255, 255, 255, 0.1)
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Try Swyp Launcher",
                        color = BentoColors.TextPrimary,
                        style = BentoTypography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Test the UI and features",
                        color = BentoColors.TextSecondary,
                        style = BentoTypography.bodyMedium
                    )
                }
                
                // Rocket icon container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x33000000)), // Black with 20% opacity
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Rocket,
                        contentDescription = "Launch",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
            
            // Launch now button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .clickable { onOpenLauncher() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Launch now",
                    color = Color.Black,
                    style = BentoTypography.labelButton
                )
            }
        }
    }
}

@Composable
fun LaunchModesCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    offsetX: Float = 0f
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX
            }
            .height(192.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
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
            .clickable { onClick() }
            .padding(24.dp)
    ) {
        Column {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BentoColors.AccentGreen
                )
                Text(
                    text = "LAUNCH MODES",
                    color = BentoColors.TextLabel,
                    style = BentoTypography.labelLarge
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            // Large count
            Text(
                text = count.toString(),
                color = BentoColors.AccentGreen,
                style = BentoTypography.displayLarge
            )
            
            Text(
                text = "Modes selected",
                color = BentoColors.TextSecondary,
                style = BentoTypography.bodyMedium
            )
        }
    }
}

@Composable
fun AppShortcutsCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    offsetX: Float = 0f
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX
            }
            .height(192.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
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
            .clickable { onClick() }
            .padding(24.dp)
    ) {
        Column {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Label,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BentoColors.AccentGreen
                )
                Text(
                    text = "APP SHORTCUTS",
                    color = BentoColors.TextLabel,
                    style = BentoTypography.labelLarge
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            // Large count
            Text(
                text = count.toString(),
                color = BentoColors.AccentGreen,
                style = BentoTypography.displayLarge
            )
            
            Text(
                text = if (count == 1) "Active shortcut" else "Active shortcuts",
                color = BentoColors.TextSecondary,
                style = BentoTypography.bodyMedium
            )
        }
    }
}
