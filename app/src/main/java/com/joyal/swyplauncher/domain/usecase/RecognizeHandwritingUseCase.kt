package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.domain.model.InkStroke
import com.joyal.swyplauncher.domain.repository.MLKitRepository
import javax.inject.Inject

class RecognizeHandwritingUseCase @Inject constructor(
    private val mlKitRepository: MLKitRepository
) {
    suspend fun initialize() = mlKitRepository.initializeDigitalInkRecognizer()

    suspend operator fun invoke(strokes: List<InkStroke>) = 
        if (strokes.isEmpty()) Result.success("") 
        else mlKitRepository.recognizeHandwriting(strokes)
}
