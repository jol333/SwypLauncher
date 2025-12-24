package com.joyal.swyplauncher.data.repository

import com.joyal.swyplauncher.data.source.MLKitDataSource
import com.joyal.swyplauncher.data.source.SpeechRecognitionResult
import com.joyal.swyplauncher.domain.model.InkStroke
import com.joyal.swyplauncher.domain.repository.MLKitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MLKitRepositoryImpl @Inject constructor(
    private val mlKitDataSource: MLKitDataSource
) : MLKitRepository {
    override suspend fun initializeDigitalInkRecognizer(): Result<Unit> = mlKitDataSource.initializeDigitalInkRecognizer()
    override suspend fun recognizeHandwriting(strokes: List<InkStroke>): Result<String> = mlKitDataSource.recognizeHandwriting(strokes)
    override fun startSpeechRecognition(): Flow<SpeechRecognitionResult> = mlKitDataSource.startSpeechRecognition()
    override fun stopSpeechRecognition() = mlKitDataSource.stopSpeechRecognition()
}
