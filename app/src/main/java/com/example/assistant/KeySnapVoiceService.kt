package com.example.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession

class KeySnapVoiceService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // A shortcut needs neither screen contents nor a screenshot from the foreground app.
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        showSession(Bundle.EMPTY, 0)
    }
}
