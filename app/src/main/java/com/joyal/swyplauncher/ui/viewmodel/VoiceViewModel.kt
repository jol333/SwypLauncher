package com.joyal.swyplauncher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joyal.swyplauncher.data.source.SpeechRecognitionResult
import com.joyal.swyplauncher.domain.model.RecognitionResult
import com.joyal.swyplauncher.domain.usecase.RecognizeSpeechUseCase
import com.joyal.swyplauncher.ui.state.VoiceUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val recognizeSpeechUseCase: RecognizeSpeechUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var recognitionJob: Job? = null
    private var baseTranscription = "" // Store accumulated text across recognition sessions
    private var shouldContinueListening = false

    fun startListening(clearTranscription: Boolean = true) {
        if (clearTranscription) {
            baseTranscription = ""
        }

        shouldContinueListening = true
        
        // If already listening, don't restart
        if (_uiState.value.isListening && recognitionJob?.isActive == true) return

        recognitionJob?.cancel()
        _uiState.update {
            it.copy(
                isListening = true,
                isInitializing = false,
                isUserStopped = false,
                transcription = if (clearTranscription) "" else it.transcription,
                recognitionResult = RecognitionResult.Loading
            )
        }

        recognitionJob = viewModelScope.launch {
            while (shouldContinueListening) {
                try {
                    recognizeSpeechUseCase.startRecognition().collect { result ->
                        when (result) {
                            is SpeechRecognitionResult.Ready,
                            is SpeechRecognitionResult.Speaking -> {
                                _uiState.update { it.copy(isListening = true, isUserStopped = false) }
                            }

                            is SpeechRecognitionResult.PartialResult -> {
                                // Show partial result appended to base transcription
                                val displayText = if (baseTranscription.isNotEmpty()) {
                                    "$baseTranscription ${result.text}"
                                } else {
                                    result.text
                                }
                                _uiState.update {
                                    it.copy(
                                        transcription = displayText,
                                        recognitionResult = RecognitionResult.Loading,
                                        isListening = true,
                                        isUserStopped = false
                                    )
                                }
                            }

                            is SpeechRecognitionResult.Success -> {
                                // Append new text to base transcription
                                baseTranscription = if (baseTranscription.isNotEmpty()) {
                                    "$baseTranscription ${result.text}"
                                } else {
                                    result.text
                                }
                                
                                _uiState.update {
                                    it.copy(
                                        transcription = baseTranscription,
                                        recognitionResult = RecognitionResult.Success(baseTranscription),
                                        isListening = true,
                                        isUserStopped = false
                                    )
                                }
                                
                                // Break out of collect to restart recognition
                                return@collect
                            }

                            is SpeechRecognitionResult.Error -> {
                                shouldContinueListening = false
                                val userFriendlyMessage = getUserFriendlyErrorMessage(result.message)
                                _uiState.update {
                                    it.copy(
                                        isListening = false,
                                        recognitionResult = RecognitionResult.Error(userFriendlyMessage),
                                        isUserStopped = false
                                    )
                                }
                                return@collect
                            }

                            is SpeechRecognitionResult.EndOfSpeech -> {
                                // Break out of collect to restart recognition
                                return@collect
                            }
                        }
                    }
                    
                    // Small delay before restarting
                    if (shouldContinueListening) {
                        kotlinx.coroutines.delay(200)
                    }
                } catch (e: Exception) {
                    // Handle any exceptions and stop listening
                    shouldContinueListening = false
                    val userFriendlyMessage = getUserFriendlyErrorMessage(e.message)
                    _uiState.update {
                        it.copy(
                            isListening = false,
                            recognitionResult = RecognitionResult.Error(userFriendlyMessage),
                            isUserStopped = false
                        )
                    }
                }
            }
        }
    }
    
    private fun getUserFriendlyErrorMessage(technicalMessage: String?): String {
        return when {
            technicalMessage == null -> "Voice recognition stopped"
            technicalMessage.contains("cancelled", ignoreCase = true) -> "Voice recognition stopped"
            technicalMessage.contains("timeout", ignoreCase = true) -> "No speech detected. Please try again"
            technicalMessage.contains("network", ignoreCase = true) -> "Network issue. Please check your connection"
            technicalMessage.contains("permission", ignoreCase = true) -> "Microphone permission needed"
            technicalMessage.contains("busy", ignoreCase = true) -> "Microphone is busy. Please try again"
            technicalMessage.contains("not available", ignoreCase = true) -> "Voice recognition not available"
            technicalMessage.contains("no match", ignoreCase = true) -> "Couldn't understand. Please try again"
            else -> "Voice recognition stopped"
        }
    }

    fun stopListening() {
        shouldContinueListening = false
        recognitionJob?.cancel()
        recognitionJob = null
        recognizeSpeechUseCase.stopRecognition()
        _uiState.update { it.copy(isListening = false, isUserStopped = true) }
    }

    fun clearTranscription() {
        baseTranscription = ""
        _uiState.update {
            it.copy(
                transcription = "",
                recognitionResult = RecognitionResult.Loading
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        shouldContinueListening = false
        recognitionJob?.cancel()
        recognitionJob = null
        stopListening()
    }
}