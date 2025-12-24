package com.joyal.swyplauncher.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.joyal.swyplauncher.ui.state.IndexUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class IndexViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(IndexUiState())
    val uiState: StateFlow<IndexUiState> = _uiState.asStateFlow()

    fun setSelectedLetter(letter: Char?) {
        _uiState.update { it.copy(selectedLetter = letter) }
    }

    fun setExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isExpanded = expanded) }
    }

    fun reset() {
        _uiState.value = IndexUiState()
    }
}
