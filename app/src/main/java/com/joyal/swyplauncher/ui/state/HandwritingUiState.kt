package com.joyal.swyplauncher.ui.state

import com.joyal.swyplauncher.domain.model.InkStroke
import com.joyal.swyplauncher.domain.model.RecognitionResult

data class HandwritingUiState(
    val strokes: List<InkStroke> = emptyList(),
    val recognizedText: String = "",
    val recognitionResult: RecognitionResult = RecognitionResult.Initializing,
    val isInitialized: Boolean = false,
    val hasShownInitToast: Boolean = false
)
