package com.example.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Required by the voice interaction metadata. Never opens the microphone or records audio. */
class KeySnapRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback) = Unit
}
