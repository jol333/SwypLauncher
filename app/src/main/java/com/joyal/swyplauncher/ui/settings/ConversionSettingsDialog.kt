package com.joyal.swyplauncher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.ui.theme.BentoColors
import com.joyal.swyplauncher.util.UnitData

/**
 * All conversion category keys used for persistence.
 * "CURRENCY" is a special key; the rest match [UnitData.Category.name].
 */
val ALL_CONVERSION_CATEGORIES: List<String> = buildList {
    add("CURRENCY")
    add("TIMEZONE")
    addAll(UnitData.Category.entries.map { it.name })
}

/** Total number of conversion categories (Currency + all UnitData categories). */
val TOTAL_CONVERSION_CATEGORIES = ALL_CONVERSION_CATEGORIES.size

/**
 * Returns the string resource ID for a conversion category key.
 */
fun conversionCategoryLabelResId(key: String): Int = when (key) {
    "CURRENCY" -> R.string.conversion_category_currency
    "TIMEZONE" -> R.string.conversion_category_timezone
    "LENGTH" -> R.string.conversion_category_length
    "AREA" -> R.string.conversion_category_area
    "DENSITY" -> R.string.conversion_category_density
    "VOLUME" -> R.string.conversion_category_volume
    "TIME" -> R.string.conversion_category_time
    "TEMPERATURE" -> R.string.conversion_category_temperature
    "SPEED" -> R.string.conversion_category_speed
    "MASS" -> R.string.conversion_category_mass
    "PRESSURE" -> R.string.conversion_category_pressure
    "ENERGY" -> R.string.conversion_category_energy
    "ANGLE" -> R.string.conversion_category_angle
    "SHOE_SIZE" -> R.string.conversion_category_shoe_size
    "POWER" -> R.string.conversion_category_power
    "FORCE" -> R.string.conversion_category_force
    "TORQUE" -> R.string.conversion_category_torque
    "FUEL" -> R.string.conversion_category_fuel
    "NUMERAL" -> R.string.conversion_category_numeral
    "DATA" -> R.string.conversion_category_data
    else -> R.string.conversion_category_currency // fallback
}

/**
 * Returns two recognisable example unit names + "…" for a category key,
 * to help users understand what each category contains.
 */
fun conversionCategoryExamples(key: String): String = when (key) {
    "CURRENCY" -> "USD, EUR, …"
    "TIMEZONE" -> "EST, CET, London, …"
    "LENGTH" -> "km, mile, …"
    "AREA" -> "square metre, hectare, …"
    "DENSITY" -> "g/cm³, lb/ft³, …"
    "VOLUME" -> "liter, gallon, …"
    "TIME" -> "hour, minute, …"
    "TEMPERATURE" -> "°C, °F, …"
    "SPEED" -> "km/h, mph, …"
    "MASS" -> "kg, pound, …"
    "PRESSURE" -> "bar, psi, …"
    "ENERGY" -> "joule, calorie, …"
    "ANGLE" -> "degree, radian, …"
    "SHOE_SIZE" -> "EU, UK, …"
    "POWER" -> "watt, horsepower, …"
    "FORCE" -> "newton, lbf, …"
    "TORQUE" -> "N·m, lb·ft, …"
    "FUEL" -> "km/L, mpg, …"
    "NUMERAL" -> "hex, binary, …"
    "DATA" -> "mega bit, mega byte, …"
    else -> ""
}

/**
 * Popup content for the Conversion Categories container-transform dialog.
 * Similar to [com.joyal.swyplauncher.ModeOrderDialogContent] but without drag-to-reorder.
 */
@Composable
fun ConversionCategoriesDialogContent(
    initialEnabled: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var enabledCategories by remember { mutableStateOf(initialEnabled) }
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp

    Column(
        modifier = Modifier
            .heightIn(max = maxDialogHeight)
    ) {
        // Header (fixed, outside scroll)
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.select_conversion_categories),
                style = MaterialTheme.typography.titleLarge,
                color = BentoColors.TextPrimary
            )
            Spacer(Modifier.height(16.dp))
        }

        // Scrollable categories list
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 16.dp)
        ) {
            ALL_CONVERSION_CATEGORIES.forEach { key ->
                val isChecked = key in enabledCategories
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isChecked) {
                                if (enabledCategories.size > 1) {
                                    enabledCategories = enabledCategories - key
                                }
                            } else {
                                enabledCategories = enabledCategories + key
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(conversionCategoryLabelResId(key)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isChecked) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = conversionCategoryExamples(key),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                enabledCategories = enabledCategories + key
                            } else {
                                // Prevent unchecking the last category
                                if (enabledCategories.size > 1) {
                                    enabledCategories = enabledCategories - key
                                }
                            }
                        }
                    )
                }
            }
        }

        // Footer buttons (fixed, outside scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.TextButton(
                onClick = { onSave(enabledCategories) }
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}