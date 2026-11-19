package com.joyal.swyplauncher.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ModeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _enabledModes = MutableStateFlow(readEnabledModes())
    val enabledModes: StateFlow<List<LauncherMode>> = _enabledModes.asStateFlow()

    // Always one of enabledModes: the pager and the mode bar have no page/tab for any other mode,
    // so a disabled mode (the saved one, or one asked for by a stale widget or launcher shortcut)
    // would open the sheet with nothing selected.
    private val _selectedMode = MutableStateFlow(
        preferencesRepository.getSelectedMode().takeIf { it in _enabledModes.value }
            ?: _enabledModes.value.first()
    )
    val selectedMode: StateFlow<LauncherMode> = _selectedMode.asStateFlow()

    /** Selects [mode] if it is enabled; otherwise keeps the current mode. */
    fun setMode(mode: LauncherMode) {
        if (mode !in _enabledModes.value) return
        preferencesRepository.setSelectedMode(mode)
        _selectedMode.value = mode
    }

    fun refreshEnabledModes() {
        val modes = readEnabledModes()
        _enabledModes.value = modes
        if (_selectedMode.value !in modes) setMode(modes.first())
    }

    private fun readEnabledModes(): List<LauncherMode> =
        preferencesRepository.getEnabledModes().ifEmpty { LauncherMode.entries }
}
