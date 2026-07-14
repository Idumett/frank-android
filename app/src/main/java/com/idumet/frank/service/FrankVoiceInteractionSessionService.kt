package com.idumet.frank.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class FrankVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return FrankVoiceInteractionSession(this)
    }
}

class FrankVoiceInteractionSession(
    context: android.content.Context
) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        // Temporary proof that Android launched Frank.
        android.widget.Toast.makeText(
            context,
            "Frank activated",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}