package com.joyal.swyplauncher.data.source

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.joyal.swyplauncher.domain.model.InkStroke
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class MLKitDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: com.joyal.swyplauncher.domain.repository.PreferencesRepository
) : com.joyal.swyplauncher.domain.repository.MLKitRepository {
    private var digitalInkRecognizer: DigitalInkRecognizer? = null
    private var speechRecognizer: SpeechRecognizer? = null

    override suspend fun initializeDigitalInkRecognizer(): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            try {
                // If already initialized, return success immediately
                if (digitalInkRecognizer != null) {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                    return@suspendCancellableCoroutine
                }

                val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
                if (modelIdentifier == null) {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Failed to create model identifier")))
                    }
                    return@suspendCancellableCoroutine
                }

                val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()

                // Check if model was previously downloaded using SharedPreferences
                if (preferencesRepository.hasDownloadedHandwritingModel()) {
                    // Model was downloaded before, directly initialize the recognizer
                    val options = DigitalInkRecognizerOptions.builder(model).build()
                    digitalInkRecognizer = DigitalInkRecognition.getClient(options)
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                    return@suspendCancellableCoroutine
                }

                // First time initialization - check and download if needed
                val modelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()

                // Check if model is already downloaded
                modelManager.isModelDownloaded(model)
                    .addOnSuccessListener { isDownloaded ->
                        if (isDownloaded) {
                            // Model already downloaded, just initialize the recognizer
                            val options = DigitalInkRecognizerOptions.builder(model).build()
                            digitalInkRecognizer = DigitalInkRecognition.getClient(options)
                            preferencesRepository.setHandwritingModelDownloaded()
                            if (continuation.isActive) {
                                continuation.resume(Result.success(Unit))
                            }
                        } else {
                            // Download model if needed
                            modelManager.download(
                                model,
                                com.google.mlkit.common.model.DownloadConditions.Builder().build()
                            )
                                .addOnSuccessListener {
                                    val options = DigitalInkRecognizerOptions.builder(model).build()
                                    digitalInkRecognizer = DigitalInkRecognition.getClient(options)
                                    preferencesRepository.setHandwritingModelDownloaded()
                                    if (continuation.isActive) {
                                        continuation.resume(Result.success(Unit))
                                    }
                                }
                                .addOnFailureListener { e ->
                                    val errorMessage = when {
                                        e.message?.contains("network", ignoreCase = true) == true ||
                                                e.message?.contains(
                                                    "internet",
                                                    ignoreCase = true
                                                ) == true ->
                                            "No internet connection. Please check your network and try again."

                                        e.message?.contains(
                                            "download",
                                            ignoreCase = true
                                        ) == true ->
                                            "Download failed. Please try again."

                                        else -> "Failed to download handwriting recognition model. Please try again."
                                    }
                                    if (continuation.isActive) {
                                        continuation.resume(Result.failure(Exception(errorMessage)))
                                    }
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("Failed to check model status. Please try again.")))
                        }
                    }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(Exception("Failed to initialize handwriting recognition. Please try again.")))
                }
            }
        }

    override suspend fun recognizeHandwriting(strokes: List<InkStroke>): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val recognizer = digitalInkRecognizer
            if (recognizer == null) {
                continuation.resume(Result.failure(Exception("Please wait while we set up handwriting recognition...")))
                return@suspendCancellableCoroutine
            }

            if (strokes.isEmpty()) {
                continuation.resume(Result.success(""))
                return@suspendCancellableCoroutine
            }

            try {
                val inkBuilder = Ink.builder()

                strokes.forEach { stroke ->
                    val strokeBuilder = Ink.Stroke.builder()
                    stroke.points.forEach { point ->
                        strokeBuilder.addPoint(
                            Ink.Point.create(point.x, point.y, point.timestamp)
                        )
                    }
                    inkBuilder.addStroke(strokeBuilder.build())
                }

                val ink = inkBuilder.build()

                recognizer.recognize(ink)
                    .addOnSuccessListener { result ->
                        val recognizedText = result.candidates.firstOrNull()?.text ?: ""
                        continuation.resume(Result.success(recognizedText))
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }

    override fun startSpeechRecognition(): Flow<SpeechRecognitionResult> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechRecognitionResult.Error("Speech recognition not available"))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechRecognitionResult.Ready)
            }

            override fun onBeginningOfSpeech() {
                trySend(SpeechRecognitionResult.Speaking)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Can be used for volume visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                trySend(SpeechRecognitionResult.EndOfSpeech)
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                trySend(SpeechRecognitionResult.Error(errorMessage))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(SpeechRecognitionResult.Success(matches[0]))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    trySend(SpeechRecognitionResult.PartialResult(matches[0]))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Set longer silence timeouts to keep listening until user speaks or stops manually
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                10000
            )
        }

        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
            speechRecognizer = null
        }
    }

    override fun stopSpeechRecognition() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore cleanup errors
        } finally {
            speechRecognizer = null
        }
    }

    fun cleanup() {
        try {
            digitalInkRecognizer?.close()
        } catch (e: Exception) {
            // Ignore cleanup errors
        } finally {
            digitalInkRecognizer = null
        }
        stopSpeechRecognition()
    }
}

sealed class SpeechRecognitionResult {
    data object Ready : SpeechRecognitionResult()
    data object Speaking : SpeechRecognitionResult()
    data object EndOfSpeech : SpeechRecognitionResult()
    data class PartialResult(val text: String) : SpeechRecognitionResult()
    data class Success(val text: String) : SpeechRecognitionResult()
    data class Error(val message: String) : SpeechRecognitionResult()
}