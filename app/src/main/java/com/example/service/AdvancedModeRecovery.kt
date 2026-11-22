package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.example.MainActivity
import com.example.data.SensitiveAppsRepository
import com.example.service.adb.WirelessAdbManager
import com.example.service.bridge.BridgeController
import com.example.service.bridge.BridgeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A piece Advanced Mode needs back, in the order the Fix cards show them. */
enum class RecoveryStep { DeveloperOptions, WirelessDebugging, Accessibility, TurnOn }

data class RecoverySwitches(
    val developerOptions: Boolean = true,
    val wirelessDebugging: Boolean = true,
    val accessibility: Boolean = true,
) {
    val bridgeCanStart: Boolean get() = developerOptions && wirelessDebugging
}

/** The Fix cards: none unless a Sensitive app ended Advanced Mode and no bridge is running; then one per missing piece. */
fun recoverySteps(ended: Boolean, bridgeRunning: Boolean, switches: RecoverySwitches): List<RecoveryStep> =
    if (!ended || bridgeRunning) emptyList() else buildList {
        if (!switches.developerOptions) add(RecoveryStep.DeveloperOptions)
        if (!switches.wirelessDebugging) add(RecoveryStep.WirelessDebugging)
        if (!switches.accessibility) add(RecoveryStep.Accessibility)
        if (switches.bridgeCanStart) add(RecoveryStep.TurnOn)
    }

/**
 * Getting Advanced Mode back after a wireless-debugging Sensitive app ended it. The ADB pairing stays
 * saved, so once developer options and wireless debugging are on again a spawn needs no code. This only
 * reads settings and opens Settings pages; it never writes a setting.
 *
 * While the flag is set, one [ContentObserver] watches those two switches; the moment both turn on it
 * starts the bridge once and brings KeySnap to the front. No observer exists while the flag is clear.
 */
object AdvancedModeRecovery {
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private const val WIRELESS_DEBUGGING_SETTINGS = "android.settings.WIRELESS_DEBUGGING_SETTINGS"

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _switches = MutableStateFlow(RecoverySwitches())
    val switches: StateFlow<RecoverySwitches> = _switches.asStateFlow()
    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    // Main thread only.
    private var observer: ContentObserver? = null
    private var bothOn = false

    private fun repository(context: Context) = SensitiveAppsRepository.getInstance(context)
    private fun ended(context: Context) = repository(context).store.value.advancedModeEnded

    /** Process start: a flag saved before the process died starts watching again. */
    fun initialize(context: Context) {
        if (ended(context)) scope.launch { watch(context.applicationContext) }
    }

    /** The bridge said `leaving`: a Sensitive app is ending Advanced Mode. */
    fun onEnded(context: Context) {
        repository(context).setAdvancedModeEnded(true)
        scope.launch { watch(context.applicationContext) }
    }

    /** A bridge is up again, or the user no longer wants Advanced Mode. */
    fun clear(context: Context) {
        repository(context).setAdvancedModeEnded(false)
        scope.launch { unwatch(context.applicationContext) }
    }

    /** On resume and on every switch change: values move while KeySnap is in the background. */
    fun refresh(context: Context) {
        if (!ended(context)) return
        // Turned off on purpose meanwhile: nothing left to get back.
        if (!WirelessAdbManager.isAdvancedModeWanted(context)) {
            clear(context)
            return
        }
        _switches.value = read(context)
    }

    /**
     * "Turn on" and the automatic continue share this. It does nothing while any start is in flight or a
     * bridge already runs, so a tap can never race the observer into a second spawn.
     */
    fun turnOn(context: Context, bringToFront: Boolean = false, onFailed: () -> Unit = {}) {
        if (BridgeController.state.value != BridgeState.STOPPED || !_connecting.compareAndSet(expect = false, update = true)) return
        val app = context.applicationContext
        scope.launch {
            val ok = try {
                WirelessAdbManager.connectWithExistingPairing()
            } finally {
                _connecting.value = false
            }
            when {
                // A refused background start is fine: coming back shows Advanced Mode on.
                ok && bringToFront -> runCatching {
                    app.startActivity(
                        Intent(app, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    )
                }
                !ok -> onFailed()
            }
        }
    }

    /** About phone, where Build number is; Settings itself when the phone has no such page. */
    fun openDeveloperOptionsFix(context: Context) = open(context, Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS)

    /** Straight to Wireless debugging. No pairing search and no code notice: the pairing is still saved. */
    fun openWirelessDebuggingFix(context: Context) =
        open(context, WIRELESS_DEBUGGING_SETTINGS, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    private fun open(context: Context, vararg actions: String) {
        actions.firstOrNull { action ->
            runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    private fun watch(context: Context) {
        refresh(context) // may clear the flag
        if (observer != null || !ended(context)) return
        bothOn = _switches.value.bridgeCanStart
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh(context)
                val now = _switches.value.bridgeCanStart
                // Only on the turn to "both on", so a failed start is not retried on every change.
                if (now && !bothOn && ended(context)) turnOn(context, bringToFront = true)
                bothOn = now
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), false, watcher)
        resolver.registerContentObserver(Settings.Global.getUriFor(ADB_WIFI_ENABLED), false, watcher)
        observer = watcher
    }

    private fun unwatch(context: Context) {
        observer?.let(context.contentResolver::unregisterContentObserver)
        observer = null
    }

    private fun read(context: Context): RecoverySwitches {
        val resolver = context.contentResolver
        val self = ComponentName(context, KeyMapperAccessibilityService::class.java)
        return RecoverySwitches(
            developerOptions = Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
            wirelessDebugging = Settings.Global.getInt(resolver, ADB_WIFI_ENABLED, 0) == 1,
            accessibility = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == self } == true,
        )
    }
}
