package com.joyal.swyplauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.domain.repository.AppSortOrder
import com.joyal.swyplauncher.ui.theme.BentoColors

@Composable
fun AutoOpenCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
            .clickable { onCheckedChange(!checked) }
            .padding(24.dp)
    ) {
        Column {
            // Custom styled switch
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
            .clickable { onClick() }
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