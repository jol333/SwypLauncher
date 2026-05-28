package com.joyal.swyplauncher.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.ui.state.CurrencyResultState
import com.joyal.swyplauncher.util.CurrencyData
import java.text.DecimalFormat

@Composable
fun InteractiveCurrencyConverter(
    state: CurrencyResultState,
    onAmountChanged: (isSource: Boolean, amount: Double) -> Unit,
    onCurrencyChanged: (isSource: Boolean, code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local text state for smooth editing
    var sourceText by remember {
        mutableStateOf(
            formatForEditing(
                state.sourceAmount,
                state.fromCode
            )
        )
    }
    var targetText by remember {
        mutableStateOf(state.targetAmount?.let {
            formatForEditing(
                it,
                state.toCode
            )
        } ?: "")
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
                onValueChange = {
                    sourceText = it
                    it.toDoubleOrNull()?.let { amount -> onAmountChanged(true, amount) }
                },
                onCurrencyChange = { onCurrencyChanged(true, it) },
                onFocusChange = { isSourceFocused = it },
                isLoading = false
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
                onValueChange = {
                    targetText = it
                    it.toDoubleOrNull()?.let { amount -> onAmountChanged(false, amount) }
                },
                onCurrencyChange = { onCurrencyChanged(false, it) },
                onFocusChange = { isTargetFocused = it },
                isLoading = state.isLoading
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
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = valueText,
            onValueChange = { newValue ->
                // Allow empty string, single minus, and valid numbers ending with optional dot
                if (newValue.isEmpty() || newValue == "-" || newValue.matches(Regex("^-?\\d*\\.?\\d*$"))) {
                    onValueChange(newValue)
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
            visualTransformation = NumberCommaTransformation(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                if (valueText.isEmpty() && !isLoading) {
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

        Spacer(modifier = Modifier.width(8.dp))

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        CurrencyDropdown(
            selectedCode = currencyCode,
            onCodeSelected = onCurrencyChange
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

    // Estimate item height for scrolling (48dp is standard for DropdownMenuItem)
    val itemHeightPx = with(density) { 48.dp.toPx() }

    LaunchedEffect(expanded) {
        if (expanded) {
            val index = sortedCurrencies.indexOfFirst { it.code == selectedCode }
            if (index >= 0) {
                // Scroll to item
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
                    contentDescription = "Select Currency",
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

private class NumberCommaTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty() || original == "-") return TransformedText(
            text,
            OffsetMapping.Identity
        )

        val parts = original.split(".")
        val intPart = parts[0]
        val decPart = if (parts.size > 1) "." + parts[1] else ""

        val isNegative = intPart.startsWith("-")
        val digits = if (isNegative) intPart.substring(1) else intPart

        val formattedIntBuilder = StringBuilder()
        var count = 0
        for (i in digits.indices.reversed()) {
            formattedIntBuilder.append(digits[i])
            count++
            if (count % 3 == 0 && i > 0) {
                formattedIntBuilder.append(",")
            }
        }
        val formattedInt = (if (isNegative) "-" else "") + formattedIntBuilder.reverse().toString()
        val formattedResult = formattedInt + decPart

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= (if (isNegative) 1 else 0)) return offset
                val intLen = intPart.length
                val commas = if (offset <= intLen) {
                    val charsFromEnd = intLen - offset
                    val totalCommas = (digits.length - 1) / 3
                    val commasAfter = charsFromEnd / 3
                    Math.max(0, totalCommas - commasAfter)
                } else {
                    Math.max(0, (digits.length - 1) / 3)
                }
                return offset + commas
            }

            override fun transformedToOriginal(offset: Int): Int {
                var originalOffset = 0
                for (i in 0 until offset) {
                    if (i < formattedResult.length && formattedResult[i] != ',') {
                        originalOffset++
                    }
                }
                return originalOffset
            }
        }

        return TransformedText(AnnotatedString(formattedResult), offsetMapping)
    }
}