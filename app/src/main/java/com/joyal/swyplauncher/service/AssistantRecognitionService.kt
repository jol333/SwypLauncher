package com.joyal.swyplauncher.service

import android.content.Intent
import android.speech.RecognitionService

class AssistantRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        listener.error(android.speech.SpeechRecognizer.ERROR_NO_MATCH)
    }

    override fun onCancel(listener: Callback) {
    }

    override fun onStopListening(listener: Callback) {
    }
}
