package com.joyal.swyplauncher.service

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.util.Log

class AssistantRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        Log.d("AssistantRecognition", "onStartListening called")
        // This is a minimal implementation
        // You can implement actual speech recognition here if needed
        listener.error(android.speech.SpeechRecognizer.ERROR_NO_MATCH)
    }

    override fun onCancel(listener: Callback) {
        Log.d("AssistantRecognition", "onCancel called")
    }

    override fun onStopListening(listener: Callback) {
        Log.d("AssistantRecognition", "onStopListening called")
    }
}
