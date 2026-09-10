package com.example.service.adb

import androidx.core.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.nicholasarda.keysnap.R
import android.util.Log

class PairingNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SUBMIT_PAIRING_CODE) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_PAIRING_INPUT)
            ?.toString()
            ?.trim()
        Log.d("PairingNotification", "Pairing reply received")

        if (replyText?.matches(Regex("\\d{6}")) != true) {
            WirelessAdbManager.initialize(context.applicationContext)
            WirelessAdbManager.showPairingNotification(context, context.getString(R.string.notif_pairing_bad_code))
            return
        }

        WirelessAdbManager.initialize(context.applicationContext)
        WirelessAdbManager.onCredentialsFromNotification(replyText, null)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_SUBMIT_PAIRING_CODE = "com.example.action.SUBMIT_PAIRING_CODE"
        const val KEY_PAIRING_INPUT = "pairing_code_input"
        const val NOTIFICATION_ID = 4567
    }
}
