package com.example.service.adb

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.ScriptAction
import com.example.service.RunFailureNotifier
import com.example.service.bridge.BridgeController
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal enum class AutostartGate { OFF, NOT_WANTED, NO_PERMISSION, DEV_OPTIONS_OFF, GO }

internal fun autostartGate(switchOn: Boolean, advancedWanted: Boolean, hasWss: Boolean, devOptionsOn: Boolean) = when {
    !switchOn -> AutostartGate.OFF
    !advancedWanted -> AutostartGate.NOT_WANTED
    !hasWss -> AutostartGate.NO_PERMISSION
    !devOptionsOn -> AutostartGate.DEV_OPTIONS_OFF
    else -> AutostartGate.GO
}

internal fun secureSettingsCommand(packageName: String, grant: Boolean): String {
    require(packageName.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")))
    return "pm ${if (grant) "grant" else "revoke"} $packageName android.permission.WRITE_SECURE_SETTINGS"
}

internal fun grantChange(wanted: Boolean, held: Boolean): Boolean? = if (wanted == held) null else wanted
internal fun isOwnAddress(host: String?, own: Set<String>) = host != null && host in own
internal fun keepAliveDue(lastTouchMillis: Long, nowMillis: Long) =
    lastTouchMillis == 0L || nowMillis - lastTouchMillis >= 20 * 60 * 60_000L

internal enum class WifiDebugging { ON, NOT_ALLOWED }

internal suspend fun enableWifiDebugging(
    read: () -> Int,
    write: (Int) -> Unit,
    keyguardLocked: () -> Boolean,
    settleMs: Long = 3_000,
    promptWaitMs: Long = 60_000,
    pollMs: Long = 500,
): WifiDebugging {
    write(1)
    // AOSP can briefly show 1 before rejecting an untrusted network. Let that decision settle.
    delay(settleMs)
    if (read() == 1) return WifiDebugging.ON
    while (keyguardLocked()) delay(pollMs)
    write(1)
    repeat((promptWaitMs / pollMs).toInt()) {
        delay(pollMs)
        if (read() == 1) return WifiDebugging.ON
    }
    return WifiDebugging.NOT_ALLOWED
}

/** Only this opt-in feature may change adb_wifi_enabled; no other debugging settings are written. */
object AdvancedAutostart {
    const val PREFS_NAME = "arda_mapper_adb_prefs"
    const val PREF_ENABLED = "advanced_autostart_enabled"
    private const val PREF_WIFI_OWNED = "advanced_autostart_enabled_wifi_debugging"
    private const val PREF_LAST_TOUCH = "advanced_autostart_last_touch"
    const val JOB_ID = 0x4B535202
    const val KEEP_ALIVE_JOB_ID = 0x4B535203
    private const val NOTIFICATION_ID = 3002
    private val grantLock = Mutex()
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean(PREF_ENABLED, false)
    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun gate(context: Context) = autostartGate(
        enabled(context), WirelessAdbManager.isAdvancedModeWanted(), hasPermission(context),
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
    )

    fun afterBoot(context: Context) {
        prefs(context).edit().putBoolean(PREF_WIFI_OWNED, false).apply()
        if (enabled(context) && WirelessAdbManager.isAdvancedModeWanted(context)) scheduleBoot(context)
        else cancelJobs(context)
    }

    private fun job(context: Context, id: Int) = JobInfo.Builder(
        id, ComponentName(context, AdvancedAutostartJobService::class.java),
    ).setRequiredNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build())

    private fun scheduleBoot(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.schedule(job(context, JOB_ID).build())
    }

    fun scheduleKeepAlive(context: Context) {
        if (!enabled(context) || !WirelessAdbManager.isAdvancedModeWanted(context)) return
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        // Adopting the bridge on each app launch must not postpone the daily job indefinitely.
        if (scheduler.getPendingJob(KEEP_ALIVE_JOB_ID) != null) return
        scheduler.schedule(
            job(context, KEEP_ALIVE_JOB_ID).setPeriodic(24 * 60 * 60_000L).setPersisted(true).build(),
        )
    }

    fun cancelJobs(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.apply { cancel(JOB_ID); cancel(KEEP_ALIVE_JOB_ID) }
    }

    fun recordTouch(context: Context) {
        prefs(context).edit().putLong(PREF_LAST_TOUCH, System.currentTimeMillis()).apply()
    }

    suspend fun run(context: Context, keepAlive: Boolean) {
        WirelessAdbManager.initialize(context)
        val gate = gate(context)
        if (keepAlive) {
            if (gate != AutostartGate.GO || !keepAliveDue(prefs(context).getLong(PREF_LAST_TOUCH, 0), System.currentTimeMillis())) return
            if (!BridgeController.detectExistingBridge()) return
            val touched = WirelessAdbManager.touchPairing()
            if (touched) recordTouch(context)
            Log.d("AdvancedAutostart", if (touched) "keepAlive=ok" else "keepAlive=failed")
            return
        }
        when (gate) {
            AutostartGate.OFF, AutostartGate.NOT_WANTED -> return
            AutostartGate.NO_PERMISSION -> { fail(context, R.string.notif_autostart_no_permission); return }
            AutostartGate.DEV_OPTIONS_OFF -> { fail(context, R.string.notif_autostart_dev_options_off); return }
            AutostartGate.GO -> Unit
        }
        if (BridgeController.detectExistingBridge()) {
            scheduleKeepAlive(context)
            clearFailure(context)
            return
        }
        val wasOn = Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        if (!wasOn) {
            // Record ownership before the write, so cancellation cannot leave an untracked setting.
            prefs(context).edit().putBoolean(PREF_WIFI_OWNED, true).commit()
        }
        val wifi = enableWifiDebugging(
            read = { Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) },
            write = { value ->
                if (gate(context) != AutostartGate.GO) throw CancellationException("Autostart disabled")
                check(Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", value))
            },
            keyguardLocked = { context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true },
        )
        if (wifi != WifiDebugging.ON) { fail(context, R.string.notif_autostart_wifi_not_allowed); return }
        if (gate(context) != AutostartGate.GO) return
        if (WirelessAdbManager.restartAfterBoot()) {
            scheduleKeepAlive(context)
            clearFailure(context)
        } else fail(context, R.string.notif_autostart_connect_failed)
    }

    suspend fun syncSecureSettingsGrant(context: Context): Boolean = grantLock.withLock {
        val wanted = enabled(context) && WirelessAdbManager.isAdvancedModeWanted(context)
        val change = grantChange(wanted, hasPermission(context))
        if (change != null) {
            BridgeController.runActions(listOf(ScriptAction("shell_command", mapOf(
                "command" to secureSettingsCommand(context.packageName, change),
            )))) ?: return@withLock false
        }
        val ok = hasPermission(context) == wanted
        if (ok && wanted) scheduleKeepAlive(context) else cancelJobs(context)
        WirelessAdbManager.refreshAutostartPermission()
        ok
    }

    fun ownsWifiDebugging(context: Context) = prefs(context).getBoolean(PREF_WIFI_OWNED, false)

    /** Before SHUTDOWN, revoke with the live bridge. Delay its own setting cleanup until it exits. */
    suspend fun prepareStop(context: Context): Boolean = grantLock.withLock {
        cancelJobs(context)
        val owned = ownsWifiDebugging(context)
        if (!hasPermission(context) && !owned) return@withLock true
        val pid = BridgeController.bridgeInfo.value?.pid
        if (owned && (pid == null || pid <= 0)) return@withLock false
        val command = secureSettingsCommand(context.packageName, false) + if (owned) {
            // Do not kill adbd before the bridge has cleaned up. A refused SHUTDOWN leaves its PID
            // alive, so this bounded child exits without touching wireless debugging in that case.
            " || exit 1; (i=0; while kill -0 $pid 2>/dev/null && [ \"\$i\" -lt 30 ]; " +
                "do sleep 1; i=\$((i+1)); done; if ! kill -0 $pid 2>/dev/null; then " +
                "settings put global adb_wifi_enabled 0; fi) >/dev/null 2>&1 &"
        } else ""
        val result = BridgeController.runActions(listOf(ScriptAction("shell_command", mapOf("command" to command))))
        if (result?.ran != 1 || hasPermission(context)) return@withLock false
        WirelessAdbManager.refreshAutostartPermission()
        true
    }

    suspend fun finishStop(context: Context): Boolean {
        if (!ownsWifiDebugging(context)) return true
        repeat(20) {
            if (Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 0) {
                prefs(context).edit().putBoolean(PREF_WIFI_OWNED, false).commit()
                return true
            }
            delay(250)
        }
        return false
    }

    fun clearFailure(context: Context) { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }

    internal fun fail(context: Context, reason: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(
            RunFailureNotifier.CHANNEL_ID, context.getString(R.string.notif_channel_run_failures), NotificationManager.IMPORTANCE_DEFAULT,
        ))
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = context.getString(reason)
        val notification = NotificationCompat.Builder(context, RunFailureNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notif_autostart_title))
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()
        try { manager.notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) { /* Notifications denied. */ }
    }
}

class AdvancedAutostartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AdvancedAutostart.afterBoot(context)
    }
}

class AdvancedAutostartJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        jobs[params.jobId] = scope.launch {
            try {
                val complete = withTimeoutOrNull(5 * 60_000L) {
                    AdvancedAutostart.run(applicationContext, params.jobId == AdvancedAutostart.KEEP_ALIVE_JOB_ID)
                    true
                }
                if (complete == null && params.jobId == AdvancedAutostart.JOB_ID) {
                    AdvancedAutostart.fail(applicationContext, R.string.notif_autostart_connect_failed)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                if (params.jobId == AdvancedAutostart.JOB_ID) {
                    AdvancedAutostart.fail(applicationContext, if (AdvancedAutostart.hasPermission(applicationContext))
                        R.string.notif_autostart_wifi_not_allowed else R.string.notif_autostart_no_permission)
                }
            } catch (_: Exception) {
                // Exception messages can contain network identifiers. Show a fixed, local reason.
                if (params.jobId == AdvancedAutostart.JOB_ID) AdvancedAutostart.fail(applicationContext, R.string.notif_autostart_connect_failed)
            }
            jobFinished(params, false)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        // Wi-Fi disappearing pauses the boot attempt; it may run again when Wi-Fi returns.
        return params.jobId == AdvancedAutostart.JOB_ID && AdvancedAutostart.enabled(this) && WirelessAdbManager.isAdvancedModeWanted(this)
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
