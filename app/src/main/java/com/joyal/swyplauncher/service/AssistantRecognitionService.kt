package com.joyal.swyplauncher.service

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent

class AssistantRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        listener.error(android.speech.SpeechRecognizer.ERROR_NO_MATCH)
    }

    override fun onCancel(listener: Callback) {
    }

    override fun onStopListening(listener: Callback) {
    }
}
