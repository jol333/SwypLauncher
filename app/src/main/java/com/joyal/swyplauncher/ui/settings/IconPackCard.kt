package com.joyal.swyplauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.IconPackInfo
import com.joyal.swyplauncher.ui.theme.BentoColors

/**
 * Settings tile for the icon pack applied to all apps; opens the icon pack screen. Only shown
 * while at least one icon pack is installed. Behind the text, [showcaseIcons] (a mix of the
 * installed packs' icons, see [IconPackShowcaseLoader]) drift past in a tilted grid.
 */
@Composable
fun IconPackCard(
    packs: List<IconPackInfo>,
    selectedPackage: String?,
    pickedIconCount: Int,
    showcaseIcons: List<ImageBitmap>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = packs.firstOrNull { it.packageName == selectedPackage }

    Box(
        modifier = modifier
            .fillMaxWidth()
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
    ) {
        IconPackShowcase(icons = showcaseIcons, modifier = Modifier.matchParentSize())

        // The text keeps to the side the icons fade out on
        Column(modifier = Modifier.fillMaxWidth(0.72f).padding(24.dp)) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Category,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BentoColors.AccentGreen
                )
                Text(
                    text = stringResource(R.string.icon_pack_card_title),
                    color = BentoColors.TextLabel,
                    style = BentoTypography.labelLarge
                )
            }

            Spacer(Modifier.height(32.dp))

            // Pack in use
            Text(
                text = selected?.label ?: stringResource(R.string.icon_pack_default),
                color = BentoColors.AccentGreen,
                style = BentoTypography.displayLarge.copy(fontSize = 32.sp, lineHeight = 38.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (pickedIconCount > 0) {
                    pluralStringResource(R.plurals.icon_pack_picked_icons, pickedIconCount, pickedIconCount)
                } else {
                    pluralStringResource(R.plurals.icon_packs_installed, packs.size, packs.size)
                },
                color = BentoColors.TextSecondary,
                style = BentoTypography.bodyMedium
            )
        }
    }
}
