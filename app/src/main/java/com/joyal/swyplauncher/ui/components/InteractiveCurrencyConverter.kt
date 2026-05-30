package com.joyal.swyplauncher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.ui.state.CurrencyResultState
import com.joyal.swyplauncher.util.CurrencyData
import com.joyal.swyplauncher.util.UnitUtil
import java.text.DecimalFormat

@Composable
fun InteractiveCurrencyConverter(
    state: CurrencyResultState,
    onAmountChanged: (isSource: Boolean, amount: Double) -> Unit,
    onCurrencyChanged: (isSource: Boolean, code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = remember { UnitUtil.detectRegion(context).locale }
    val decimalSep = remember(locale) { android.icu.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    val escapedDecimal = remember(decimalSep) { Regex.escape(decimalSep.toString()) }
    val numberRegex = remember(escapedDecimal) { Regex("^-?\\d*$escapedDecimal?\\d*$") }

    var sourceText by remember {
        mutableStateOf(formatForEditing(state.sourceAmount, state.fromCode))
    }
    var targetText by remember {
        mutableStateOf(state.targetAmount?.let { formatForEditing(it, state.toCode) } ?: "")
    }

    var isSourceFocused by remember { mutableStateOf(false) }
    var isTargetFocused by remember { mutableStateOf(false) }

    LaunchedEffect(state.sourceAmount, state.fromCode) {
        if (!isSourceFocused) {
            sourceText = formatForEditing(state.sourceAmount, state.fromCode)
        }
    }

    LaunchedEffect(state.targetAmount, state.toCode) {
        if (!isTargetFocused) {
            targetText = state.targetAmount?.let { formatForEditing(it, state.toCode) } ?: ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp)
        ) {
            CurrencyInputRow(
                valueText = sourceText,
                currencyCode = state.fromCode,
                onValueChange = { newValue ->
                    sourceText = newValue
                    val dotValue = newValue.replace(decimalSep, '.')
                    dotValue.toDoubleOrNull()?.let { amount -> onAmountChanged(true, amount) }
                },
                onCurrencyChange = { onCurrencyChanged(true, it) },
                onFocusChange = { isSourceFocused = it },
                isLoading = false,
                numberRegex = numberRegex,
                locale = locale
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 8.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
            )

            CurrencyInputRow(
                valueText = targetText,
                currencyCode = state.toCode,
                onValueChange = { newValue ->
                    targetText = newValue
                    val dotValue = newValue.replace(decimalSep, '.')
                    dotValue.toDoubleOrNull()?.let { amount -> onAmountChanged(false, amount) }
                },
                onCurrencyChange = { onCurrencyChanged(false, it) },
                onFocusChange = { isTargetFocused = it },
                isLoading = state.isLoading,
                numberRegex = numberRegex,
                locale = locale
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrencyInputRow(
    valueText: String,
    currencyCode: String,
    onValueChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    isLoading: Boolean,
    numberRegex: Regex,
    locale: java.util.Locale
) {
    var textFieldValue by remember(valueText) {
        mutableStateOf(TextFieldValue(text = valueText, selection = TextRange(valueText.length)))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            ShimmerValuePlaceholder(modifier = Modifier.weight(1f))
        } else {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    if (newValue.text.isEmpty() || newValue.text == "-" || newValue.text.matches(numberRegex)) {
                        textFieldValue = newValue
                        onValueChange(newValue.text)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = NumberCommaTransformation(locale),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (valueText.isEmpty()) {
                        Text(
                            text = "0",
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        CurrencyDropdown(
            selectedCode = currencyCode,
            onCodeSelected = onCurrencyChange
        )
    }
}

@Composable
private fun ShimmerValuePlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val barWidthPx = with(LocalDensity.current) { 160.dp.toPx() }

    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate * barWidthPx, 0f),
        end = Offset((translate + 1f) * barWidthPx, 0f)
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
    }
}

@Composable
private fun CurrencyDropdown(
    selectedCode: String,
    onCodeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sortedCurrencies = remember { CurrencyData.all.sortedBy { it.code } }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 48.dp.toPx() }

    LaunchedEffect(expanded) {
        if (expanded) {
            val index = sortedCurrencies.indexOfFirst { it.code == selectedCode }
            if (index >= 0) {
                scrollState.scrollTo((index * itemHeightPx).toInt())
            }
        }
    }

    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            ) {
                Text(
                    text = selectedCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.select_currency),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.height(300.dp),
            scrollState = scrollState
        ) {
            sortedCurrencies.forEach { spec ->
                DropdownMenuItem(
                    text = {
                        val displayText = if (spec.code.equals(spec.symbol, ignoreCase = true)) {
                            spec.code
                        } else {
                            "${spec.code} - ${spec.symbol}"
                        }
                        Text(displayText, style = MaterialTheme.typography.bodyLarge)
                    },
                    onClick = {
                        onCodeSelected(spec.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun formatForEditing(value: Double, currencyCode: String): String {
    val crypto = listOf("BTC", "ETH", "BNB")
    val maxDecimals = if (crypto.contains(currencyCode.uppercase())) 6 else 2

    val pattern = if (maxDecimals == 6) "0.######" else "0.##"
    val format = DecimalFormat(pattern)
    format.isGroupingUsed = false

    return format.format(value)
}
