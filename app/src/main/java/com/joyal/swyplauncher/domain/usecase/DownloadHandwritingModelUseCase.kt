package com.joyal.swyplauncher.domain.usecase

import com.joyal.swyplauncher.domain.repository.MLKitRepository
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import javax.inject.Inject

class DownloadHandwritingModelUseCase @Inject constructor(
    private val mlKitRepository: MLKitRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke() {
        // Only download if not already downloaded
        if (!preferencesRepository.hasDownloadedHandwritingModel()) {
            android.util.Log.d("DownloadHandwritingModel", "Starting background model download...")
            val result = mlKitRepository.initializeDigitalInkRecognizer()
            if (result.isSuccess) {
                android.util.Log.d("DownloadHandwritingModel", "Model download completed successfully")
            } else {
                android.util.Log.e("DownloadHandwritingModel", "Model download failed: ${result.exceptionOrNull()?.message}")
            }
        } else {
            android.util.Log.d("DownloadHandwritingModel", "Model already downloaded, skipping")
        }
    }
}
