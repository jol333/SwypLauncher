package com.joyal.swyplauncher.ui.state

import com.joyal.swyplauncher.domain.model.RecognitionResult

data class VoiceUiState(
    val isListening: Boolean = false,
    val transcription: String = "",
    val recognitionResult: RecognitionResult = RecognitionResult.Loading,
    // Deprecated in practice, but kept for compatibility
    val isInitializing: Boolean = false,
    // New: lets the UI show an immediate "Stopped / Ready" state instead of blank
    val isUserStopped: Boolean = false
)