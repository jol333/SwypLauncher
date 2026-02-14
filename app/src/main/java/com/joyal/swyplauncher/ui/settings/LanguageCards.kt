package com.joyal.swyplauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.AppLanguage
import com.joyal.swyplauncher.ui.theme.BentoColors

@Composable
fun LanguageCard(
    currentLanguage: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    offsetX: Float = 0f
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX
                // Transform from left edge for connected feel
                transformOrigin = TransformOrigin(0f, 0.5f)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BentoColors.AccentGreen
                )
                Text(
                    text = stringResource(R.string.language),
                    color = BentoColors.TextLabel,
                    style = BentoTypography.labelLarge
                )
            }
            
            Spacer(Modifier.weight(1f))
            
            // Show native language name prominently
            Text(
                text = currentLanguage.nativeName,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = BentoColors.AccentGreen,
                // modifier = Modifier.offset(x = (-4).dp),
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Visible
            )
            
            // Show English name as secondary text (except for System Default)
            Text(
                text = if (currentLanguage == AppLanguage.SYSTEM) {
                    stringResource(R.string.system_default)
                } else {
                    currentLanguage.displayName
                },
                color = BentoColors.TextMuted,
                style = BentoTypography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDialogContent(
    currentLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            stringResource(R.string.select_language),
            style = MaterialTheme.typography.titleLarge,
            color = BentoColors.TextPrimary
        )
        Spacer(Modifier.height(16.dp))

        AppLanguage.entries.forEach { language ->
            Surface(
                onClick = { onSelect(language) },
                shape = RoundedCornerShape(12.dp),
                color = if (language == currentLanguage)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = language == currentLanguage,
                        onClick = null
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        // Show native name prominently
                        Text(
                            text = language.nativeName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        // Show English name as subtitle (except for languages that are the same)
                        if (language.nativeName != language.displayName) {
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
