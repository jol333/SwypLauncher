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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.ui.state.TimeZoneResultState
import com.joyal.swyplauncher.util.TimeZoneData
import com.joyal.swyplauncher.util.TimeZoneUtil

/**
 * Interactive time-zone converter. Reuses the visual language of the currency /
 * unit converters: a rounded surface holding editable value rows, each paired
 * with a picker. Here a "row" is one zone's clock, and the picker switches the
 * whole country for that side. A swap button exchanges the two sides.
 */
@Composable
fun InteractiveTimeZoneConverter(
    state: TimeZoneResultState,
    onEditTime: (zoneId: String, hour: Int, minute: Int, pref: TimeZoneUtil.FormatPref?) -> Unit,
    onChangeCountry: (isPrimary: Boolean, iso: String) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ZoneSection(
                title = sectionTitle(state.primaryRef),
                selectedIso = refIso(state.primaryRef),
                rows = state.primaryRows,
                onEditTime = onEditTime,
                onChangeCountry = { iso -> onChangeCountry(true, iso) }
            )

            // Swap divider
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp).clickable { onSwap() }
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = stringResource(R.string.timezone_swap),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(6.dp).size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                )
            }

            ZoneSection(
                title = sectionTitle(state.secondaryRef),
                selectedIso = refIso(state.secondaryRef),
                rows = state.secondaryRows,
                onEditTime = onEditTime,
                onChangeCountry = { iso -> onChangeCountry(false, iso) }
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
private fun ZoneSection(
    title: String,
    selectedIso: String?,
    rows: List<TimeZoneUtil.Row>,
    onEditTime: (String, Int, Int, TimeZoneUtil.FormatPref?) -> Unit,
    onChangeCountry: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        CountryHeader(title = title, selectedIso = selectedIso, onChangeCountry = onChangeCountry)
        rows.forEach { row ->
            ZoneRow(row = row, onEditTime = onEditTime)
        }
    }
}

@Composable
private fun CountryHeader(title: String, selectedIso: String?, onChangeCountry: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val countries = remember { TimeZoneData.countries.sortedBy { it.name } }
    val scrollState = rememberScrollState()
    val itemHeightPx = with(LocalDensity.current) { 48.dp.toPx() }

    // Open the menu scrolled to the current selection, matching the currency/unit pickers.
    LaunchedEffect(expanded) {
        if (expanded) {
            val index = countries.indexOfFirst { it.iso == selectedIso }
            if (index >= 0) scrollState.scrollTo((index * itemHeightPx).toInt())
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
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.timezone_select_country),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
            scrollState = scrollState
        ) {
            countries.forEach { c ->
                val isSelected = c.iso == selectedIso
                DropdownMenuItem(
                    text = {
                        Text(
                            c.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onChangeCountry(c.iso)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ZoneRow(
    row: TimeZoneUtil.Row,
    onEditTime: (String, Int, Int, TimeZoneUtil.FormatPref?) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    // Single source of truth for the editable text, preserved across model rebuilds while
    // focused. NOT keyed on row.timeText — keying on the reformatted model value would
    // discard in-progress typing on every keystroke (cursor jump / can't reach "pm").
    var fieldValue by remember { mutableStateOf(TextFieldValue(row.timeText, TextRange(row.timeText.length))) }
    // Resync from state only when not actively editing (after a swap or edit elsewhere).
    LaunchedEffect(row.timeText) {
        if (!isFocused) fieldValue = TextFieldValue(row.timeText, TextRange(row.timeText.length))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { nv ->
                if (nv.text.matches(timeInputRegex)) {
                    fieldValue = nv
                    // Don't commit a half-typed meridiem ("9:30 p") as AM — wait for "pm".
                    if (!endsWithDanglingMeridiem(nv.text)) {
                        TimeZoneUtil.parseEditedTime(nv.text)?.let { e ->
                            onEditTime(row.zoneId, e.hour, e.minute, e.pref)
                        }
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "${row.city} ${row.timeText}" }
                .onFocusChanged { f ->
                    isFocused = f.isFocused
                    // On blur, snap the field to the canonical formatted value.
                    if (!f.isFocused) fieldValue = TextFieldValue(row.timeText, TextRange(row.timeText.length))
                },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        )

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = row.city,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildSubtext(row),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private val timeInputRegex = Regex("^[0-9 :.aApPmM]*$")

// "9:30 a" / "9:30 p" — meridiem started but not finished; don't commit yet.
private fun endsWithDanglingMeridiem(text: String): Boolean {
    val t = text.trim().lowercase()
    return t.endsWith("a") || t.endsWith("p")
}

private fun refIso(ref: TimeZoneUtil.Ref): String? = when (ref) {
    is TimeZoneUtil.Ref.CountryRef -> ref.iso
    is TimeZoneUtil.Ref.ZoneRef -> ref.countryIso
}

private fun buildSubtext(row: TimeZoneUtil.Row): String {
    val zone = if (row.abbrev.isNotEmpty()) "${row.abbrev} · ${row.offsetLabel}" else row.offsetLabel
    val day = when {
        row.dayDelta > 0 -> "  +${row.dayDelta}d"
        row.dayDelta < 0 -> "  ${row.dayDelta}d"
        else -> ""
    }
    return zone + day
}

private fun sectionTitle(ref: TimeZoneUtil.Ref): String = when (ref) {
    is TimeZoneUtil.Ref.CountryRef -> TimeZoneData.country(ref.iso)?.name ?: ref.iso
    is TimeZoneUtil.Ref.ZoneRef ->
        ref.countryIso?.let { TimeZoneData.country(it)?.name } ?: TimeZoneData.cityLabel(ref.zoneId)
}
