package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.SensitiveApp
import com.example.data.SensitiveAppsRepository
import com.example.data.SensitiveAppsStore
import com.example.data.activeApps
import com.example.service.bridge.BridgeController
import com.example.service.bridge.BridgePayload
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.flow.StateFlow

/**
 * The one door between Settings → Sensitive apps and whatever enforces it. The list, its settings
 * and the feedback live here and are complete on their own. The enforcer is the Advanced Mode
 * bridge (`SensitiveAppGuard`): [activeApps] travels in its config, and it reports back through
 * [onSteppedAside] / [onRestored]. Nothing here turns a switch itself.
 */
object SensitiveAppsGate {
    const val CHANNEL_ID = "sensitive_apps"
    private const val NOTIFICATION_ID = 3010

    /** Everything the enforcer needs, live: apps, their choices, the master switch. */
    fun store(context: Context): StateFlow<SensitiveAppsStore> = SensitiveAppsRepository.getInstance(context).store

    /** Apps to step aside for right now; empty while the master switch is off. */
    fun activeApps(context: Context): List<SensitiveApp> = store(context).value.activeApps

    /**
     * The enforcer turned [app]'s switches off. Notifies and buzzes only if the user asked. [leaving]:
     * it also turned wireless debugging off and ended, so Advanced Mode is off until set up again.
     */
    fun onSteppedAside(context: Context, app: SensitiveApp, leaving: Boolean = false) {
        val settings = store(context).value.settings
        if (settings.showNotification) postNotification(context, app, leaving)
        if (settings.vibrate) vibrate(context, settings.vibrateMs)
    }

    /** The enforcer put [app]'s switches back. */
    fun onRestored(context: Context, app: SensitiveApp) {
        context.getSystemService(NotificationManager::class.java)?.cancel(app.packageName, NOTIFICATION_ID)
    }

    /** Advanced Mode is running again: an "Advanced Mode is off" notice left by a wireless-debugging app is stale, and so are its Fix cards. */
    fun onBridgeUp(context: Context) {
        AdvancedModeRecovery.clear(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        store(context).value.apps.filter { it.hideWirelessDebugging }.forEach { manager.cancel(it.packageName, NOTIFICATION_ID) }
    }

    /**
     * "Restore now" in the notification: asks the bridge, through its config, to put the switches
     * back and leave the app in front alone. The notice stays until the bridge reports [onRestored],
     * so it never claims a restore that did not happen. The bridge restores whatever it stepped
     * aside for, which is the app the notice names.
     */
    @Suppress("UNUSED_PARAMETER")
    fun restoreNow(context: Context, packageName: String) {
        BridgePayload(context).requestSensitiveRestore()
        BridgeController.publishConfigIfInstalled(context)
    }

    private fun postNotification(context: Context, app: SensitiveApp, leaving: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Blocked, or Android 13+ without POST_NOTIFICATIONS: nothing to show.
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.sensitive_title), NotificationManager.IMPORTANCE_LOW),
        )
        // Accessibility off pauses automations (half gone); developer options off loses nothing.
        val textRes = when {
            leaving -> R.string.sensitive_notif_text_off
            app.hideAccessibility -> R.string.sensitive_notif_text
            else -> R.string.sensitive_notif_text_dev
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.sensitive_notif_title, app.label))
            .setContentText(context.getString(textRes))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
        if (leaving) {
            // No bridge is left to ask for a restore: the tap opens KeySnap to set Advanced Mode up again.
            val open = PendingIntent.getActivity(
                context,
                app.packageName.hashCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentIntent(open).setAutoCancel(true)
        } else {
            val restore = PendingIntent.getBroadcast(
                context,
                app.packageName.hashCode(),
                Intent(context, SensitiveAppsRestoreReceiver::class.java).putExtra(SensitiveAppsRestoreReceiver.EXTRA_PACKAGE, app.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.sensitive_notif_restore), restore)
        }
        manager.notify(app.packageName, NOTIFICATION_ID, builder.build())
    }

    private fun vibrate(context: Context, durationMs: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}

/** The notification's "Restore now". Unexported: only KeySnap's own PendingIntent reaches it. */
class SensitiveAppsRestoreReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_PACKAGE = "package"
    }

    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(EXTRA_PACKAGE)?.let { SensitiveAppsGate.restoreNow(context, it) }
    }
}
