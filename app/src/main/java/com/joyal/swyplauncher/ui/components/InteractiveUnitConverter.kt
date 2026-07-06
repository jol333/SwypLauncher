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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.ui.state.UnitResultState
import com.joyal.swyplauncher.util.UnitData
import com.joyal.swyplauncher.util.UnitUtil

@Composable
fun InteractiveUnitConverter(
    state: UnitResultState,
    onAmountChanged: (isSource: Boolean, amount: Double) -> Unit,
    onUnitChanged: (isSource: Boolean, id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = remember { UnitUtil.detectRegion(context).locale }
    val decimalSep = remember(locale) { android.icu.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    val escapedDecimal = remember(decimalSep) { Regex.escape(decimalSep.toString()) }
    val numberRegex = remember(escapedDecimal) { Regex("^-?\\d*$escapedDecimal?\\d*$") }

    var sourceText by remember { mutableStateOf(UnitUtil.formatValue(state.sourceAmount, state.fromId)) }
    var targetText by remember { mutableStateOf(state.targetAmount?.let { UnitUtil.formatValue(it, state.toId) } ?: "") }
    var isSourceFocused by remember { mutableStateOf(false) }
    var isTargetFocused by remember { mutableStateOf(false) }

    LaunchedEffect(state.sourceAmount, state.fromId) {
        if (!isSourceFocused) sourceText = UnitUtil.formatValue(state.sourceAmount, state.fromId)
    }
    LaunchedEffect(state.targetAmount, state.toId) {
        if (!isTargetFocused) targetText = state.targetAmount?.let { UnitUtil.formatValue(it, state.toId) } ?: ""
    }

    val isNumeral = remember(state.category) { UnitData.byId(state.fromId)?.radix != null }

    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
            UnitInputRow(
                valueText = sourceText,
                unitId = state.fromId,
                category = state.category,
                approx = state.sourceApprox,
                isNumeral = isNumeral,
                onValueChange = {
                    sourceText = it
                    if (isNumeral) {
                        parseValue(it, state.fromId)?.let { amt -> onAmountChanged(true, amt) }
                    } else {
                        val dotValue = it.replace(decimalSep, '.')
                        val d = dotValue.toDoubleOrNull()
                        if (d != null) onAmountChanged(true, d)
                        else if (it.isEmpty() || it == "-" || it == decimalSep.toString()) onAmountChanged(true, 0.0)
                    }
                },
                onUnitChange = { onUnitChanged(true, it) },
                onFocusChange = { isSourceFocused = it },
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

            UnitInputRow(
                valueText = targetText,
                unitId = state.toId,
                category = state.category,
                approx = state.targetApprox,
                isNumeral = isNumeral,
                onValueChange = {
                    targetText = it
                    if (isNumeral) {
                        parseValue(it, state.toId)?.let { amt -> onAmountChanged(false, amt) }
                    } else {
                        val dotValue = it.replace(decimalSep, '.')
                        val d = dotValue.toDoubleOrNull()
                        if (d != null) onAmountChanged(false, d)
                        else if (it.isEmpty() || it == "-" || it == decimalSep.toString()) onAmountChanged(false, 0.0)
                    }
                },
                onUnitChange = { onUnitChanged(false, it) },
                onFocusChange = { isTargetFocused = it },
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
private fun UnitInputRow(
    valueText: String,
    unitId: String,
    category: UnitData.Category,
    approx: String?,
    isNumeral: Boolean,
    onValueChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    numberRegex: Regex,
    locale: java.util.Locale
) {
    var textFieldValue by remember(valueText) {
        mutableStateOf(TextFieldValue(text = valueText, selection = TextRange(valueText.length)))
    }
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val ok = if (isNumeral) newValue.text.matches(Regex("^[0-9a-fA-F]*$"))
                    else newValue.text.isEmpty() || newValue.text == "-" || newValue.text.matches(numberRegex)
                    if (ok) {
                        textFieldValue = newValue
                        onValueChange(newValue.text)
                    }
                },
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isNumeral) KeyboardType.Text else KeyboardType.Decimal
                ),
                visualTransformation = if (!isNumeral) NumberCommaTransformation(locale) else VisualTransformation.None,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
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
                    inner()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            UnitDropdown(category = category, selectedId = unitId, onSelected = onUnitChange)
        }
        // Approximate human-readable subtext for very large/small values
        if (approx != null) {
            Text(
                text = approx,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun UnitDropdown(
    category: UnitData.Category,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    val units = remember(category) { UnitData.unitsIn(category) }
    val selectedIndex = remember(selectedId, units) { units.indexOfFirst { it.id == selectedId } }
    val selected = remember(selectedId) { UnitData.byId(selectedId) }

    ScrollToSelectedDropdown(
        selectedIndex = selectedIndex,
        menuModifier = Modifier.heightIn(max = 320.dp),  // wrap small lists, cap+scroll large ones
        trigger = { onClick ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clickable(onClick = onClick)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = selected?.symbol ?: selectedId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.select_unit), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    ) { dismiss ->
        units.forEach { spec ->
            DropdownMenuItem(
                text = { Text("${spec.symbol} — ${UnitData.label(spec.id)}", style = MaterialTheme.typography.bodyLarge) },
                onClick = {
                    onSelected(spec.id)
                    dismiss()
                }
            )
        }
    }
}

private fun parseValue(text: String, unitId: String): Double? {
    if (text.isEmpty() || text == "-") return null
    val radix = UnitData.byId(unitId)?.radix
    return if (radix != null) text.toLongOrNull(radix)?.toDouble() else text.toDoubleOrNull()
}

