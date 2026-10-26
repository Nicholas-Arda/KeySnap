package com.example.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.example.service.HardwareKeyTriggerCoordinator

class KeySnapSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = KeySnapVoiceSession(this)
}

class KeySnapVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onCreate() {
        super.onCreate()
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            HardwareKeyTriggerCoordinator.onAssistantGesture(context)
        } finally {
            finish()
        }
    }
}
