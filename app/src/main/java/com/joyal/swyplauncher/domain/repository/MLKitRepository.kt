package com.joyal.swyplauncher.domain.repository

import com.joyal.swyplauncher.data.source.SpeechRecognitionResult
import com.joyal.swyplauncher.domain.model.InkStroke
import kotlinx.coroutines.flow.Flow

interface MLKitRepository {
    suspend fun initializeDigitalInkRecognizer(): Result<Unit>
    suspend fun recognizeHandwriting(strokes: List<InkStroke>): Result<String>
    fun startSpeechRecognition(): Flow<SpeechRecognitionResult>
    fun stopSpeechRecognition()
}
