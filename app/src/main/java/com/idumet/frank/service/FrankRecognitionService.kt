package com.idumet.frank.service

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService

class FrankRecognitionService : RecognitionService() {

    override fun onStartListening(
        recognizerIntent: Intent?,
        listener: Callback?
    ) {
        listener?.readyForSpeech(Bundle())
    }

    override fun onStopListening(listener: Callback?) {
        // Implement actual recognition later.
    }

    override fun onCancel(listener: Callback?) {
        // Nothing to cancel yet.
    }
}