package com.joyal.swyplauncher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joyal.swyplauncher.domain.model.LauncherMode
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(preferencesRepository.getSelectedMode())
    val selectedMode: StateFlow<LauncherMode> = _selectedMode.asStateFlow()
    
    private val _enabledModes = MutableStateFlow(preferencesRepository.getEnabledModes())
    val enabledModes: StateFlow<List<LauncherMode>> = _enabledModes.asStateFlow()

    fun setMode(mode: LauncherMode) {
        preferencesRepository.setSelectedMode(mode)
        _selectedMode.value = mode
    }
    
    fun refreshEnabledModes() {
        _enabledModes.value = preferencesRepository.getEnabledModes()
    }
}
