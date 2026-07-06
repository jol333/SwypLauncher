package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.domain.repository.MLKitRepository
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import javax.inject.Inject

class DownloadHandwritingModelUseCase @Inject constructor(
    private val mlKitRepository: MLKitRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke() {
        // Download the handwriting model on first launch only; later launches skip it.
        // Initializing the recognizer warms it as a side effect; failures are non-fatal
        // (the user is prompted to download when they first use handwriting mode).
        if (!preferencesRepository.hasDownloadedHandwritingModel()) {
            mlKitRepository.initializeDigitalInkRecognizer()
        }
    }
}
