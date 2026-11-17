package com.joyal.swyplauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.repository.IconPackRepository

/**
 * Lets an app's long-press menu open the icon picker ("Change icon") that the assistant draws
 * over itself. The assistant owns one instance and hands it down through [LocalAppIconPicker], so
 * the menu reaches it from any mode's grid without a callback threaded through every screen.
 */
@Stable
class AppIconPicker internal constructor(private val packsInstalled: State<Boolean>) {

    /** False hides "Change icon": without an installed icon pack there is nothing to pick from. */
    val isAvailable: Boolean get() = packsInstalled.value

    /** The app whose picker is on screen. Stays set while the picker animates away. */
    var app: AppInfo? by mutableStateOf(null)
        private set

    var isOpen: Boolean by mutableStateOf(false)
        private set

    fun open(app: AppInfo) {
        this.app = app
        isOpen = true
    }

    fun close() {
        isOpen = false
    }

    /** The closing animation has finished; unless it was reopened meanwhile, let go of the app. */
    fun onClosed() {
        if (!isOpen) app = null
    }
}

/** Null where no icon picker is hosted; the menu then offers no "Change icon". */
val LocalAppIconPicker = staticCompositionLocalOf<AppIconPicker?> { null }

@Composable
fun rememberAppIconPicker(iconPackRepository: IconPackRepository): AppIconPicker {
    // Starts from what this process already knows and is confirmed off the main thread, so the
    // assistant's first frame never waits on a PackageManager query.
    val packsInstalled = produceState(
        initialValue = iconPackRepository.peekIconPacks()?.isNotEmpty() == true
    ) {
        value = runCatching { iconPackRepository.getIconPacks().isNotEmpty() }.getOrDefault(false)
    }
    return remember { AppIconPicker(packsInstalled) }
}
