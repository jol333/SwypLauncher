package com.joyal.swyplauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared picker for the currency / unit / time-zone converters: a tappable [trigger] that opens
 * a [DropdownMenu] auto-scrolled to the currently-selected row ([selectedIndex]). Each converter
 * supplies its own trigger and item rendering (so its exact look is preserved); only the
 * expand-state and scroll-to-selection mechanics are shared. [menuModifier] sets the menu's
 * height policy (e.g. `Modifier.height(300.dp)` vs. `Modifier.heightIn(max = 320.dp)`).
 */
@Composable
internal fun ScrollToSelectedDropdown(
    selectedIndex: Int,
    menuModifier: Modifier,
    trigger: @Composable (onClick: () -> Unit) -> Unit,
    itemHeight: Dp = 48.dp,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    // Open the menu scrolled to the current selection.
    LaunchedEffect(expanded) {
        if (expanded && selectedIndex >= 0) {
            scrollState.scrollTo((selectedIndex * itemHeightPx).toInt())
        }
    }

    Box {
        trigger { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = menuModifier,
            scrollState = scrollState
        ) {
            menuContent { expanded = false }
        }
    }
}
