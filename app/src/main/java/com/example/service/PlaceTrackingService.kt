package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.Place
import io.github.nicholasarda.keysnap.R

/**
 * "Fast (GPS)" places: while any is in use, a location foreground service asks for a fix every
 * 30 s near a circle (minutes when far from all) and decides in/out itself. Android only grants that rate to a closed app with a
 * visible notification, which is the price the user picks this mode for. Fixes go straight to
 * [PlaceCondition.onFix] and are never kept.
 */
class PlaceTrackingService : Service() {
    private val listeners = mutableListOf<LocationListener>()
    private var interval = 0L
    private var lastFixNanos = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: RuntimeException) {
            // No location permission, or a background start Android refused: the next sync retries.
            stopSelf()
            return START_NOT_STICKY
        }
        // Android restarts a sticky service on its own after the process dies; by then the places
        // (or the global switch) may have changed, so it checks again instead of trusting the last sync.
        if (!PlaceCondition.fastWatchNeeded(applicationContext)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Every start is a sync with possibly new places: back to the near rate until the next fix says otherwise.
        startUpdates()
        return START_STICKY
    }

    private fun startUpdates(intervalMs: Long = MIN_INTERVAL_MS) {
        val locations = getSystemService(LocationManager::class.java) ?: return stopSelf()
        stopUpdates(locations)
        val updates = LocationListener { fix ->
            // A new request is handed each provider's last fix again; counted twice it would confirm itself.
            if (fix.elapsedRealtimeNanos <= lastFixNanos) return@LocationListener
            lastFixNanos = fix.elapsedRealtimeNanos
            PlaceCondition.onFix(applicationContext, fix)
            // Slow when every circle is far away, back to every 30 s as one comes near.
            // A change still waiting for its second fix gets that fix at the fast rate.
            val next = PlaceCondition.nearestFastEdge(applicationContext, fix)?.takeUnless { PlaceCondition.awaitingConfirmation() }
                ?.let(PlaceCondition::fastIntervalMs) ?: MIN_INTERVAL_MS
            if (next != interval) startUpdates(next)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in locations.allProviders) {
                // The legacy call below asks only for BALANCED (mostly network); this mode promised GPS.
                val request = LocationRequest.Builder(intervalMs).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build()
                locations.requestLocationUpdates(LocationManager.FUSED_PROVIDER, request, mainExecutor, updates)
                listeners += updates
            } else {
                // No fused provider (Android 11): GPS outdoors, and the network estimate so an arrival
                // that ends indoors still gets its second fix. One listener per provider.
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in locations.allProviders }.forEach { provider ->
                    val each = LocationListener { updates.onLocationChanged(it) }
                    locations.requestLocationUpdates(provider, intervalMs, 0f, each, Looper.getMainLooper())
                    listeners += each
                }
            }
            interval = intervalMs
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun stopUpdates(locations: LocationManager?) {
        listeners.forEach { locations?.removeUpdates(it) }
        listeners.clear()
    }

    override fun onDestroy() {
        stopUpdates(getSystemService(LocationManager::class.java))
        PlaceCondition.forgetPendingFixes()
        super.onDestroy()
    }

    /** A Fast place's own words (the first that set them), else Settings → Notifications, else built-in. Re-read on every sync. */
    private fun notification() = run {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_place_fast), NotificationManager.IMPORTANCE_LOW),
        )
        val fast = PlaceCondition.fastParams(applicationContext)
        fun words(key: String, global: String, default: Int) = fast.firstNotNullOfOrNull { it[key]?.trim()?.takeIf(String::isNotEmpty) }
            ?: NotificationPrefs.text(this, global).takeIf(String::isNotEmpty) ?: getString(default)
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(words(Place.NOTICE_TITLE, NotificationPrefs.PLACE_TITLE, R.string.place_fast_notification_title))
            .setContentText(words(Place.NOTICE_TEXT, NotificationPrefs.PLACE_TEXT, R.string.place_fast_notification_text))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "place_fast"
        const val NOTIFICATION_ID = 7301
        const val MIN_INTERVAL_MS = 30_000L
    }
}
