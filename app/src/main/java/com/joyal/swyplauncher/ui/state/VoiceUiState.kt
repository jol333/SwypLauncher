package com.joyal.swyplauncher.ui.state

import com.joyal.swyplauncher.domain.model.RecognitionResult

data class VoiceUiState(
    val isListening: Boolean = false,
    val transcription: String = "",
    val recognitionResult: RecognitionResult = RecognitionResult.Loading
)