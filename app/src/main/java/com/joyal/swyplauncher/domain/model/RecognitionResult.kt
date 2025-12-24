package com.joyal.swyplauncher.domain.model

sealed class RecognitionResult {
    data class Success(val text: String) : RecognitionResult()
    data class Error(val message: String, val isInitializationError: Boolean = false) : RecognitionResult()
    data object Loading : RecognitionResult()
    data object Initializing : RecognitionResult()
}
