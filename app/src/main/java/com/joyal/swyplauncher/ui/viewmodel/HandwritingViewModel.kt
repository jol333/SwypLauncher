package com.joyal.swyplauncher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joyal.swyplauncher.domain.model.InkStroke
import com.joyal.swyplauncher.domain.model.RecognitionResult
import com.joyal.swyplauncher.domain.usecase.RecognizeHandwritingUseCase
import com.joyal.swyplauncher.ui.state.HandwritingUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HandwritingViewModel @Inject constructor(
    private val recognizeHandwritingUseCase: RecognizeHandwritingUseCase,
    private val preferencesRepository: com.joyal.swyplauncher.domain.repository.PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HandwritingUiState(
            hasShownInitToast = preferencesRepository.hasShownHandwritingInitToast(),
            // If model was previously downloaded, start as initialized to avoid showing download UI
            isInitialized = preferencesRepository.hasDownloadedHandwritingModel()
        )
    )
    val uiState: StateFlow<HandwritingUiState> = _uiState.asStateFlow()

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        viewModelScope.launch {
            // Only show initializing state if model hasn't been downloaded before
            if (!preferencesRepository.hasDownloadedHandwritingModel()) {
                _uiState.update {
                    it.copy(
                        recognitionResult = RecognitionResult.Initializing
                    )
                }
            }
            
            val result = recognizeHandwritingUseCase.initialize()
            
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isInitialized = true,
                        recognitionResult = RecognitionResult.Loading
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isInitialized = false,
                        recognitionResult = RecognitionResult.Error(
                            message = result.exceptionOrNull()?.message ?: "Failed to download handwriting recognition model. Please try again.",
                            isInitializationError = true
                        )
                    )
                }
            }
        }
    }
    
    fun retryInitialization() {
        initializeRecognizer()
    }

    fun addStroke(stroke: InkStroke) {
        val updatedStrokes = _uiState.value.strokes + stroke
        _uiState.update { it.copy(strokes = updatedStrokes) }
        recognizeStrokes(updatedStrokes)
    }

    private fun recognizeStrokes(strokes: List<InkStroke>) {
        if (!_uiState.value.isInitialized) {
            // Don't attempt recognition if not initialized yet
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(recognitionResult = RecognitionResult.Loading) }
            
            val result = recognizeHandwritingUseCase(strokes)
            
            if (result.isSuccess) {
                val text = result.getOrNull() ?: ""
                _uiState.update {
                    it.copy(
                        recognizedText = text,
                        recognitionResult = RecognitionResult.Success(text)
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        recognitionResult = RecognitionResult.Error(
                            result.exceptionOrNull()?.message ?: "Recognition failed"
                        )
                    )
                }
            }
        }
    }

    fun removeLastStroke() {
        val currentStrokes = _uiState.value.strokes
        if (currentStrokes.isNotEmpty()) {
            val updatedStrokes = currentStrokes.dropLast(1)
            _uiState.update { it.copy(strokes = updatedStrokes) }
            
            if (updatedStrokes.isNotEmpty()) {
                recognizeStrokes(updatedStrokes)
            } else {
                // No strokes left, clear everything
                clearStrokes()
            }
        }
    }

    fun clearStrokes() {
        _uiState.update {
            it.copy(
                strokes = emptyList(),
                recognizedText = "",
                recognitionResult = RecognitionResult.Loading
            )
        }
    }

    fun onInitializationToastShown() {
        preferencesRepository.setHandwritingInitToastShown()
        _uiState.update { it.copy(hasShownInitToast = true) }
    }
}
