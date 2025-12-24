package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.data.source.SpeechRecognitionResult
import com.joyal.swyplauncher.domain.repository.MLKitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RecognizeSpeechUseCase @Inject constructor(
    private val mlKitRepository: MLKitRepository
) {
    fun startRecognition(): Flow<SpeechRecognitionResult> = mlKitRepository.startSpeechRecognition()
    fun stopRecognition() {
        mlKitRepository.stopSpeechRecognition()
    }
}
