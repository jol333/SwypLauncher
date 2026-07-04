package com.joyal.swyplauncher.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joyal.swyplauncher.domain.model.CustomGesture
import com.joyal.swyplauncher.domain.model.InkStroke
import com.joyal.swyplauncher.R
import com.joyal.swyplauncher.domain.model.RecognitionResult
import com.joyal.swyplauncher.domain.usecase.RecognizeHandwritingUseCase
import com.joyal.swyplauncher.ui.state.HandwritingUiState
import com.joyal.swyplauncher.util.GestureRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HandwritingViewModel @Inject constructor(
    private val recognizeHandwritingUseCase: RecognizeHandwritingUseCase,
    private val preferencesRepository: com.joyal.swyplauncher.domain.repository.PreferencesRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HandwritingUiState(
            hasShownInitToast = preferencesRepository.hasShownHandwritingInitToast(),
            // If model was previously downloaded, start as initialized to avoid showing download UI
            isInitialized = preferencesRepository.hasDownloadedHandwritingModel()
        )
    )
    val uiState: StateFlow<HandwritingUiState> = _uiState.asStateFlow()

    private val _matchedGesture = MutableStateFlow<CustomGesture?>(null)
    val matchedGesture: StateFlow<CustomGesture?> = _matchedGesture.asStateFlow()

    private var recognitionJob: Job? = null

    private companion object {
        // Wait for a short pause in drawing before recognizing. Fast consecutive
        // strokes keep pushing this back, so recognition results (and the app-grid
        // recomposition they trigger) never land on the main thread mid-stroke,
        // which would delay pointer events for the next stroke.
        const val RECOGNITION_DEBOUNCE_MS = 250L
    }

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
                            message = result.exceptionOrNull()?.message ?: context.getString(R.string.handwriting_model_error_download_generic),
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

    /**
     * Called when the user puts their finger down for a new stroke. Cancels any
     * pending or in-flight recognition so its result can't recompose the results
     * grid while the stroke is being drawn.
     */
    fun onStrokeDrawingStarted() {
        recognitionJob?.cancel()
    }

    fun addStroke(stroke: InkStroke) {
        val updatedStrokes = _uiState.value.strokes + stroke
        _uiState.update { it.copy(strokes = updatedStrokes) }
        scheduleRecognition(updatedStrokes)
    }

    private fun scheduleRecognition(
        strokes: List<InkStroke>,
        debounceMillis: Long = RECOGNITION_DEBOUNCE_MS
    ) {
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            // Gesture matching runs concurrently with text recognition
            launch(Dispatchers.Default) {
                val gestures = preferencesRepository.getCustomGestures()
                _matchedGesture.value = if (gestures.isEmpty() || strokes.size != 1) {
                    null
                } else {
                    GestureRecognizer.findBestMatch(strokes, gestures)
                }
            }
            recognizeStrokes(strokes)
        }
    }

    private suspend fun recognizeStrokes(strokes: List<InkStroke>) {
        if (!_uiState.value.isInitialized) {
            // Don't attempt recognition if not initialized yet
            return
        }

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
                        result.exceptionOrNull()?.message ?: context.getString(R.string.handwriting_recognition_failed)
                    )
                )
            }
        }
    }

    fun removeLastStroke() {
        val currentStrokes = _uiState.value.strokes
        if (currentStrokes.isNotEmpty()) {
            val updatedStrokes = currentStrokes.dropLast(1)
            _uiState.update { it.copy(strokes = updatedStrokes) }

            if (updatedStrokes.isNotEmpty()) {
                // No debounce — undo is a deliberate action, update results immediately
                scheduleRecognition(updatedStrokes, debounceMillis = 0L)
            } else {
                // No strokes left, clear everything
                clearStrokes()
            }
        }
    }

    fun clearStrokes() {
        recognitionJob?.cancel()
        _uiState.update {
            it.copy(
                strokes = emptyList(),
                recognizedText = "",
                recognitionResult = RecognitionResult.Loading
            )
        }
        _matchedGesture.value = null
    }

    fun onInitializationToastShown() {
        preferencesRepository.setHandwritingInitToastShown()
        _uiState.update { it.copy(hasShownInitToast = true) }
    }
}
