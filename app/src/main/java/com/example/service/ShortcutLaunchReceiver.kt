package com.example.service

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.IntentCompat

/**
 * "Launch app shortcut": the bridge (shell UID) fetches another app's shortcut as a PendingIntent
 * and sends it here, because only this process can start it from the background (its bound
 * Accessibility service exempts it). The manifest guards the receiver with
 * START_ACTIVITIES_FROM_BACKGROUND, which the shell and system hold and ordinary apps cannot, so no
 * other app can use it to launch anything.
 */
class ShortcutLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = IntentCompat.getParcelableExtra(intent, EXTRA_PENDING, PendingIntent::class.java) ?: return
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        runCatching { pending.send(context, 0, null, null, null, null, options.toBundle()) }
    }

    private companion object {
        // Mirrors AppShortcuts.EXTRA_PENDING in :bridge.
        const val EXTRA_PENDING = "pending"
    }
}
