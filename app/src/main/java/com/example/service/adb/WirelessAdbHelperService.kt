package com.example.service.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import com.example.data.AppLocale
import io.github.nicholasarda.keysnap.R
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Short-lived foreground service covering the wireless ADB pairing/spawn window only.
 *
 * It does not host key reading. Advanced Mode runs in a detached shell-UID process spawned over
 * ADB, which keeps detecting keys and executing actions after this app is killed, so this service
 * must never be treated as a keep-alive for it: [WirelessAdbManager] starts it just before
 * spawning the bridge and stops it as soon as the spawn completes. Accessibility- and
 * Activity-sourced key dispatch have no dependency on this service either; they run through
 * [com.example.service.HardwareKeyTriggerCoordinator], a process-wide singleton.
 *
 * The notification text is a fixed string rather than the live status feed, because that feed
 * carries the device's LAN address and ADB port and this notification is visible on the lock
 * screen.
 */
class WirelessAdbHelperService : Service() {
    // Services get their own resources from the system, so the in-app language picker has to be
    // applied here too or their notifications and overlays stay in the system language.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))


    companion object {
        const val CHANNEL_ID = "arda_mapper_fg_channel"
        const val NOTIFICATION_ID = 9091

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, WirelessAdbHelperService::class.java))
            } catch (e: Exception) {
                android.util.Log.w("WirelessAdbHelper", "Failed to startForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WirelessAdbHelperService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_setup), NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = getString(R.string.notif_channel_setup_description)
                    setShowBadge(false)
                },
        )
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Nothing here is worth surviving recents removal. Advanced Mode lives in a detached
        // shell-UID process that is unaffected by this app being killed, so this service exists
        // only to protect the short pairing/spawn window.
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notif_setup_title))
            .setContentText(getString(R.string.notif_setup_text))
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
