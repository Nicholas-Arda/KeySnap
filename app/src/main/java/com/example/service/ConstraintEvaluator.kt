package com.example.service

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.example.bridge.ConstraintNegation
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.mode.ModeHooks
import java.time.DayOfWeek
import java.time.LocalTime

/** Answers whether a single [ScriptConstraint] currently holds, so a script only fires while every constraint is satisfied. */
interface ConstraintEvaluator {
    fun isSatisfied(constraint: ScriptConstraint): Boolean

    /** A short-lived view for one decision; never retain it across key presses. */
    fun forDecision(): ConstraintEvaluator = this
}

/**
 * Reads live device state via Android APIs. Semantics here must match
 * `com.example.bridge.ConstraintEvaluator` in `:bridge`, which answers the same
 * predicates via shell commands since the detached bridge process has no [Context].
 *
 * Negated predicates preserve unknown and fail closed; mode_is negation is unsupported.
 * Mode predicates fail closed on unreadable state, so an error cannot select the wrong shortcut.
 * For other device predicates, unknown never blocks: whenever the state cannot be read (missing permission, no service
 * instance, malformed parameter, API failure) the constraint reads as satisfied, so a script
 * degrades to "always" rather than silently dying.
 *
 * `no_app_open` requires positive evidence of home or keyguard; unknown must not eat volume keys.
 * The automation-only types (`ScriptConstraintCatalog.AUTOMATION_ONLY`) also read
 * unknown as false: they only ever drive an automation's false -> true edge, where "always true"
 * would mean "never fires", and firing on "unknown" would be a guess.
 */
class AndroidConstraintEvaluator(
    private val appContext: Context,
    private val decisionSnapshot: Boolean = false,
) : ConstraintEvaluator {
    private val decisionResults = mutableMapOf<ScriptConstraint, Boolean>()
    private val decisionForeground by lazy { readForegroundState() }
    private val decisionHome by lazy { defaultHomePackage() }

    override fun forDecision(): ConstraintEvaluator = AndroidConstraintEvaluator(appContext, decisionSnapshot = true)

    override fun isSatisfied(constraint: ScriptConstraint): Boolean {
        if (!decisionSnapshot) return evaluate(constraint)
        return decisionResults.getOrPut(constraint) { evaluate(constraint) }
    }

    private fun evaluate(constraint: ScriptConstraint): Boolean {
        val p = constraint.params
        if (ConstraintNegation.requested(p)) {
            if (!ConstraintNegation.supported(constraint.type)) return false
            if (constraint.type == ScriptConstraintCatalog.COOLDOWN) return true
            val state = runCatching { readKnownState(ConstraintNegation.positiveType(constraint.type), constraint) }.getOrNull()
            return ConstraintNegation.apply(ConstraintNegation.predicate(constraint.type, state))
        }
        return when (constraint.type) {
            ScriptConstraintCatalog.MODE_IS -> ModeHooks.matches(appContext, p["mode"])
            "screen_on" -> isScreenOn()
            "screen_off" -> !isScreenOn()
            "wifi_on" -> isWifiOn()
            "wifi_off" -> !isWifiOn()
            "bluetooth_on" -> isBluetoothOn()
            "bluetooth_off" -> !isBluetoothOn()
            "charging" -> isCharging()
            "discharging" -> !isCharging()
            "in_phone_call" -> isInPhoneCall()
            "not_in_phone_call" -> !isInPhoneCall()
            "flashlight_on" -> FlashlightController.isFlashlightOn.value
            "flashlight_off" -> !FlashlightController.isFlashlightOn.value
            "app_in_foreground" -> isAppInForeground(p["packageName"]) ?: true
            "app_not_in_foreground" -> isAppInForeground(p["packageName"])?.not() ?: true
            "media_is_playing" -> isMusicActive()
            "no_media_playing" -> !isMusicActive()
            "bluetooth_device_connected" -> isBluetoothDeviceConnected(p["address"], p["name"])
            "bluetooth_device_disconnected" -> !isBluetoothDeviceConnected(p["address"], p["name"])
            "screen_orientation" -> matchesOrientation(p["orientation"], appContext.resources.configuration.orientation)
            "dark_mode_on" -> isDarkMode()
            "dark_mode_off" -> !isDarkMode()
            "connected_to_wifi_network" -> isConnectedToWifi(p["ssid"]) ?: true
            "disconnected_from_wifi_network" -> isConnectedToWifi(p["ssid"])?.not() ?: true
            "keyboard_showing" -> KeyMapperAccessibilityService.isKeyboardShowing() ?: true
            "keyboard_not_showing" -> KeyMapperAccessibilityService.isKeyboardShowing()?.not() ?: true
            "device_locked" -> keyguard { it.isDeviceLocked } ?: true
            "device_unlocked" -> keyguard { !it.isDeviceLocked } ?: true
            "lock_screen_showing" -> keyguard { it.isKeyguardLocked } ?: true
            "lock_screen_not_showing" -> keyguard { !it.isKeyguardLocked } ?: true
            "no_app_open" -> isNoAppOpen()
            "phone_ringing" -> isPhoneRinging()
            "battery_saver_on" -> power { it.isPowerSaveMode } ?: false
            "battery_saver_off" -> power { !it.isPowerSaveMode } ?: true
            "charging_wired" -> pluggedKind(pluggedExtra()) == "wired"
            "charging_wireless" -> pluggedKind(pluggedExtra()) == "wireless"
            "battery_above" -> comparePercent(p["percent"], batteryPercent(), above = true)
            "battery_below" -> comparePercent(p["percent"], batteryPercent(), above = false)
            "location_on" -> isLocationEnabled() ?: true
            "location_off" -> isLocationEnabled()?.not() ?: true
            "time" -> isWithinTimeWindow(p["start"], p["end"], TimeSchedule.now().toLocalTime())
            "day_of_week" -> isDayInSet(p["days"], TimeSchedule.now().dayOfWeek)
            "mobile_data_on" -> isMobileDataOn()
            "mobile_data_off" -> !isMobileDataOn()
            "airplane_mode_on" -> isAirplaneModeOn()
            "airplane_mode_off" -> !isAirplaneModeOn()
            "ringer_normal" -> audio { it.ringerMode == AudioManager.RINGER_MODE_NORMAL } ?: true
            "ringer_vibrate" -> audio { it.ringerMode == AudioManager.RINGER_MODE_VIBRATE } ?: true
            "ringer_silent" -> audio { it.ringerMode == AudioManager.RINGER_MODE_SILENT } ?: true
            "dnd_on" -> isDndOn() ?: true
            "dnd_off" -> isDndOn()?.not() ?: true
            "headphones_connected" -> areHeadphonesConnected()
            "headphones_disconnected" -> !areHeadphonesConnected()
            // Needs the script's own run history, so HardwareKeyTriggerCoordinator gates it per
            // script; here it must never block, or it would join the automation edge.
            ScriptConstraintCatalog.COOLDOWN -> true
            ScriptConstraintCatalog.NOTIFICATION_RECEIVED ->
                NotificationMatches.isRecent(constraint, SystemClock.elapsedRealtime())
            ScriptConstraintCatalog.RSS_NEW_ITEM -> RssPolling.isPulsing(p["url"])
            ScriptConstraintCatalog.AT_TIME, ScriptConstraintCatalog.INTERVAL -> TimeSchedule.isPulsing(constraint)
            ScriptConstraintCatalog.CALENDAR_EVENT_ACTIVE -> CalendarCondition.isActive(appContext, p["calendar"], p["title"])
            // A constraint type that isn't AVAILABLE never blocks a script.
            else -> true
        }
    }

    /** Strict reads used by negation; never invert a legacy fallback value. */
    private fun readKnownState(type: String, constraint: ScriptConstraint): Boolean? {
        val p = constraint.params
        return when (type) {
            "screen_on" -> power { it.isInteractive }
            "wifi_on" -> read<WifiManager>(Context.WIFI_SERVICE, "WiFi") { it.isWifiEnabled }
            "bluetooth_on" -> service<BluetoothManager>(Context.BLUETOOTH_SERVICE)?.adapter?.isEnabled
            "charging" -> read<BatteryManager>(Context.BATTERY_SERVICE, "battery") { it.isCharging }
            "in_phone_call" -> audioMode()?.takeIf { it in 0..6 }?.let { isInPhoneCall(it) }
            "phone_ringing" -> audioMode()?.takeIf { it in 0..6 }?.let { isPhoneRinging(it) }
            "flashlight_on" -> FlashlightController.knownState()
            "app_in_foreground" -> isAppInForeground(p["packageName"])
            "media_is_playing" -> audio { it.isMusicActive }
            "bluetooth_device_connected" -> knownBluetoothDevice(p["address"], p["name"])
            "screen_orientation" -> appContext.resources.configuration.orientation.let {
                if (it !in setOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE) ||
                    p["orientation"]?.trim()?.lowercase() !in setOf("portrait", "landscape")) null
                else matchesOrientation(p["orientation"], it)
            }
            "dark_mode_on" -> (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK).let {
                if (it == Configuration.UI_MODE_NIGHT_UNDEFINED) null else it == Configuration.UI_MODE_NIGHT_YES
            }
            "connected_to_wifi_network" -> knownWifiNetwork(p["ssid"])
            "keyboard_showing" -> KeyMapperAccessibilityService.isKeyboardShowing()
            "device_locked" -> keyguard { it.isDeviceLocked }
            "lock_screen_showing" -> keyguard { it.isKeyguardLocked }
            "no_app_open" -> knownNoAppOpen()
            "battery_saver_on" -> power { it.isPowerSaveMode }
            "charging_wired", "charging_wireless" -> appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.takeIf { it.hasExtra(BatteryManager.EXTRA_PLUGGED) }
                ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)?.takeIf { it >= 0 }
                ?.let { pluggedKind(it) == if (type == "charging_wired") "wired" else "wireless" }
            "battery_above", "battery_below" -> p["percent"]?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }?.let { limit ->
                batteryPercent()?.let { if (type == "battery_above") it > limit else it < limit }
            }
            "location_on" -> isLocationEnabled()
            "time" -> if (knownTime(p["start"]) == null || knownTime(p["end"]) == null) null
                else isWithinTimeWindow(p["start"], p["end"], TimeSchedule.now().toLocalTime())
            "day_of_week" -> if (parseDaySet(p["days"]).isEmpty()) null else isDayInSet(p["days"], TimeSchedule.now().dayOfWeek)
            "mobile_data_on" -> knownSetting("mobile_data")
            "airplane_mode_on" -> knownSetting(Settings.Global.AIRPLANE_MODE_ON)
            "ringer_normal", "ringer_vibrate", "ringer_silent" -> service<AudioManager>(Context.AUDIO_SERVICE)?.ringerMode?.let {
                if (it !in 0..2) null else it == when (type) { "ringer_normal" -> 2; "ringer_vibrate" -> 1; else -> 0 }
            }
            "dnd_on" -> service<NotificationManager>(Context.NOTIFICATION_SERVICE)?.currentInterruptionFilter?.let {
                if (it !in NotificationManager.INTERRUPTION_FILTER_ALL..NotificationManager.INTERRUPTION_FILTER_ALARMS) null else it != NotificationManager.INTERRUPTION_FILTER_ALL
            }
            "headphones_connected" -> audio { manager -> manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES } }
            ScriptConstraintCatalog.NOTIFICATION_RECEIVED -> if (!NotificationEventListener.isConnected() || !NotificationEventListener.isAccessGranted(appContext) || p["packageName"].isNullOrBlank()) null
                else NotificationMatches.isRecent(constraint, SystemClock.elapsedRealtime())
            ScriptConstraintCatalog.RSS_NEW_ITEM -> RssPolling.knownPulse(p["url"])
            ScriptConstraintCatalog.AT_TIME -> if (knownTime(p["time"]) == null ||
                (!p["days"].isNullOrBlank() && parseDaySet(p["days"]).isEmpty())) null else TimeSchedule.isPulsing(constraint)
            ScriptConstraintCatalog.INTERVAL -> if (TimeRules.intervalSeconds(p["every"], p["unit"]) == null) null else TimeSchedule.isPulsing(constraint)
            ScriptConstraintCatalog.CALENDAR_EVENT_ACTIVE -> CalendarCondition.knownActive(appContext, p["calendar"], p["title"])
            else -> null
        }
    }

    private fun knownWifiNetwork(ssid: String?): Boolean? {
        val cm = service<ConnectivityManager>(Context.CONNECTIVITY_SERVICE) ?: return null
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
        if (ssid.isNullOrBlank()) return true
        if (!hasFineLocation()) return null
        return currentSsid(caps)?.let { it == ssid.trim() }
    }

    private fun knownTime(value: String?): LocalTime? =
        value?.trim()?.takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) }?.let(::parseHhMm)

    private fun knownSetting(key: String): Boolean? = Settings.Global.getInt(appContext.contentResolver, key)
        .takeIf { it in 0..1 }?.let { it == 1 }

    private fun knownBluetoothDevice(address: String?, name: String?): Boolean? {
        if (address.isNullOrBlank() && name.isNullOrBlank()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        val adapter = service<BluetoothManager>(Context.BLUETOOTH_SERVICE)?.adapter ?: return null
        if (!adapter.isEnabled) return false
        DeviceEventReceiver.seedConnectedDevices(appContext)
        return DeviceEventReceiver.knownBluetoothDeviceConnected(address, name)
    }

    private fun knownNoAppOpen(): Boolean? {
        if (!hasUsageAccess(appContext)) return null
        val mode = audioMode() ?: return null
        if (isPhoneRinging(mode) || isInPhoneCall(mode)) return false
        val foreground = foregroundState() ?: return null
        val locked = keyguard { it.isKeyguardLocked } ?: return null
        if (locked) return foreground.resumedPackage == null
        val home = (if (decisionSnapshot) decisionHome else defaultHomePackage()) ?: return null
        val resumed = foreground.resumedPackage ?: return null
        return isHomeInForeground(resumed, home)
    }

    private inline fun <reified T> service(name: String): T? = appContext.getSystemService(name) as? T

    /** Runs [read] against the service, logging and returning null (unknown) on failure. */
    private inline fun <reified T> read(name: String, what: String, read: (T) -> Boolean): Boolean? = try {
        service<T>(name)?.let(read)
    } catch (e: Exception) {
        Log.e(TAG, "Error reading $what state", e)
        null
    }

    private fun keyguard(block: (KeyguardManager) -> Boolean) = read<KeyguardManager>(Context.KEYGUARD_SERVICE, "keyguard", block)
    private fun audio(block: (AudioManager) -> Boolean) = read<AudioManager>(Context.AUDIO_SERVICE, "audio", block)
    private fun power(block: (PowerManager) -> Boolean) = read<PowerManager>(Context.POWER_SERVICE, "power", block)

    private fun isScreenOn(): Boolean = power { it.isInteractive } ?: true

    private fun isWifiOn(): Boolean = read<WifiManager>(Context.WIFI_SERVICE, "WiFi") { it.isWifiEnabled } ?: false

    private fun isBluetoothOn(): Boolean =
        read<BluetoothManager>(Context.BLUETOOTH_SERVICE, "Bluetooth") { it.adapter?.isEnabled ?: false } ?: false

    private fun isCharging(): Boolean = read<BatteryManager>(Context.BATTERY_SERVICE, "battery") { it.isCharging } ?: false

    private fun audioMode(): Int? = try {
        service<AudioManager>(Context.AUDIO_SERVICE)?.mode
    } catch (_: Exception) {
        null
    }

    private fun isInPhoneCall(mode: Int? = audioMode()): Boolean =
        mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION

    private fun isPhoneRinging(mode: Int? = audioMode()): Boolean = mode == AudioManager.MODE_RINGTONE

    private fun isNoAppOpen(): Boolean {
        if (!hasUsageAccess(appContext)) {
            clearForegroundState()
            return false
        }
        val mode = audioMode() ?: return false
        if (isPhoneRinging(mode) || isInPhoneCall(mode)) return false
        val foreground = foregroundState() ?: return false
        return if (keyguard { it.isKeyguardLocked } == true) {
            foreground.resumedPackage == null
        } else {
            isHomeInForeground(foreground.resumedPackage, if (decisionSnapshot) decisionHome else defaultHomePackage())
        }
    }

    private fun isMusicActive(): Boolean = audio { it.isMusicActive } ?: false

    private fun isDarkMode(): Boolean =
        (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun isBluetoothDeviceConnected(address: String?, name: String?): Boolean {
        DeviceEventReceiver.seedConnectedDevices(appContext)
        return DeviceEventReceiver.isBluetoothDeviceConnected(address, name)
    }

    /** Null while Usage Access is not granted or the event log is empty: unknown, never blocks. */
    private fun isAppInForeground(packageName: String?): Boolean? {
        if (packageName.isNullOrBlank()) return null
        return foregroundPackage()?.let { it == packageName.trim() }
    }

    private fun foregroundState(): ForegroundState? = if (decisionSnapshot) decisionForeground else readForegroundState()

    private fun foregroundPackage(): String? = foregroundState()?.lastPackage

    private fun defaultHomePackage(): String? = try {
        appContext.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName?.takeUnless { it == "android" }
    } catch (_: Exception) {
        null
    }

    private fun readForegroundState(): ForegroundState? = try {
        if (!hasUsageAccess(appContext)) {
            clearForegroundState()
            null
        } else {
            val usage = service<UsageStatsManager>(Context.USAGE_STATS_SERVICE) ?: return null
            val now = System.currentTimeMillis()
            synchronized(foregroundLock) {
                // Catch transitions between infrequent key presses too, with the same bounded
                // cold-start window. Ordinary repeated keys still query only the last minute.
                if (now < foregroundReadAt || now - foregroundReadAt > FOREGROUND_FALLBACK_LOOKBACK_MS) {
                    clearForegroundState()
                }
                val from = if (foregroundInitialized) minOf(now - FOREGROUND_LOOKBACK_MS, foregroundReadAt)
                else now - FOREGROUND_FALLBACK_LOOKBACK_MS
                val events = usage.queryEvents(from, now) ?: return null
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val packageName = event.packageName?.takeIf { it.isNotBlank() } ?: continue
                    // instanceId is hidden. Package plus class name is the public identity.
                    val activity = packageName to event.className?.takeIf { it.isNotBlank() }
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            lastKnownForegroundPackage = packageName
                            resumedActivities[activity] = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> resumedActivities.remove(activity)
                    }
                }
                foregroundInitialized = true
                foregroundReadAt = now
                ForegroundState(lastKnownForegroundPackage, resumedActivities.maxByOrNull { it.value }?.key?.first)
            }
        }
    } catch (_: Exception) {
        // Usage events contain private package/activity names; do not log exception payloads.
        Log.e(TAG, "Error reading foreground app state")
        null
    }

    private data class ForegroundState(val lastPackage: String?, val resumedPackage: String?)

    /**
     * Null when the answer needs the SSID and it cannot be read (no location permission, or the
     * platform redacted it): unknown. Blank [ssid] means "any Wi-Fi network".
     */
    private fun isConnectedToWifi(ssid: String?): Boolean? = try {
        val cm = service<ConnectivityManager>(Context.CONNECTIVITY_SERVICE)
        val caps = cm?.activeNetwork?.let(cm::getNetworkCapabilities)
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        when {
            ssid.isNullOrBlank() -> onWifi
            !onWifi -> false
            !hasFineLocation() -> null
            else -> currentSsid(caps)?.let { it == ssid.trim() }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading WiFi network state", e)
        null
    }

    private fun hasFineLocation(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun currentSsid(caps: NetworkCapabilities?): String? {
        val fromCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (caps?.transportInfo as? WifiInfo)?.ssid else null
        // API 30, and the API 31+ redaction path (transportInfo without FLAG_INCLUDE_LOCATION_INFO).
        val raw = normalizeSsid(fromCaps) ?: normalizeSsid(service<WifiManager>(Context.WIFI_SERVICE)?.connectionInfo?.ssid)
        return raw
    }

    private fun pluggedExtra(): Int = try {
        appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    } catch (e: Exception) {
        Log.e(TAG, "Error reading charger state", e)
        0
    }

    private fun batteryPercent(): Int? = try {
        service<BatteryManager>(Context.BATTERY_SERVICE)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading battery level", e)
        null
    }

    private fun isLocationEnabled(): Boolean? = read<LocationManager>(Context.LOCATION_SERVICE, "location") { it.isLocationEnabled }

    private fun isMobileDataOn(): Boolean = try {
        Settings.Global.getInt(appContext.contentResolver, "mobile_data", 1) == 1
    } catch (e: Exception) {
        Log.e(TAG, "Error reading mobile data state", e)
        true
    }

    private fun isAirplaneModeOn(): Boolean = try {
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) {
        Log.e(TAG, "Error reading airplane mode state", e)
        false
    }

    private fun isDndOn(): Boolean? = read<NotificationManager>(Context.NOTIFICATION_SERVICE, "DND") {
        it.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun areHeadphonesConnected(): Boolean = audio { manager ->
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }
    } ?: false

    companion object {
        private const val TAG = "ConstraintEvaluator"
        // The key path scans one minute. The remembered package covers a launcher resumed longer
        // ago; only a process with no memory yet falls back, and that scan stays a few hours.
        private const val FOREGROUND_LOOKBACK_MS = 60_000L
        private const val FOREGROUND_FALLBACK_LOOKBACK_MS = 3 * 60 * 60_000L

        @Volatile
        private var lastKnownForegroundPackage: String? = null
        private val foregroundLock = Any()
        private val resumedActivities = mutableMapOf<Pair<String, String?>, Long>()
        private var foregroundInitialized = false
        private var foregroundReadAt = 0L

        private fun clearForegroundState() = synchronized(foregroundLock) {
            lastKnownForegroundPackage = null
            resumedActivities.clear()
            foregroundInitialized = false
            foregroundReadAt = 0L
        }

        internal fun rememberForegroundPackageForTest(packageName: String?) {
            synchronized(foregroundLock) {
                lastKnownForegroundPackage = packageName
                resumedActivities.clear()
                if (packageName != null) resumedActivities[packageName to null] = 0L
                foregroundInitialized = packageName != null
                foregroundReadAt = System.currentTimeMillis()
            }
        }

        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        )
    }
}

// Pure predicate helpers, kept top-level so they are unit-testable without Robolectric shadows.
// The bridge's ConstraintEvaluator implements the same rules; keep them in step.

/** Unknown must not swallow a normal volume key; only a known default launcher counts. */
internal fun isHomeInForeground(foreground: String?, home: String?): Boolean =
    !home.isNullOrBlank() && foreground == home

/** `[start, end)` in local time; an end at or before the start wraps past midnight. Malformed input never blocks. */
internal fun isWithinTimeWindow(start: String?, end: String?, now: LocalTime): Boolean {
    val from = parseHhMm(start) ?: return true
    val to = parseHhMm(end) ?: return true
    return if (from < to) now >= from && now < to else now >= from || now < to
}

internal fun parseHhMm(text: String?): LocalTime? {
    val parts = text?.trim()?.split(':') ?: return null
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}

private val DAY_NAMES = DayOfWeek.entries.associateBy { it.name.take(3).lowercase() }

/** `mon,tue,...`; unknown tokens are ignored and an empty set never blocks. */
internal fun parseDaySet(days: String?): Set<DayOfWeek> =
    days.orEmpty().split(',').mapNotNull { DAY_NAMES[it.trim().lowercase()] }.toSet()

internal fun isDayInSet(days: String?, today: DayOfWeek): Boolean =
    parseDaySet(days).let { it.isEmpty() || today in it }

/** Strictly above / strictly below. An unparsable threshold or unreadable level never blocks. */
internal fun comparePercent(threshold: String?, level: Int?, above: Boolean): Boolean {
    val limit = threshold?.trim()?.toIntOrNull() ?: return true
    if (level == null) return true
    return if (above) level > limit else level < limit
}

/**
 * "At most once every [minutes] minutes". Mirrors `ConstraintEvaluator.cooldownAllows` in
 * `:bridge` exactly: minutes clamp to 1..1440, blank or malformed never blocks, a script that has
 * not run yet passes, and a clock earlier than the last run never blocks.
 */
internal fun cooldownAllows(lastRunMs: Long?, minutes: String?, nowMs: Long): Boolean {
    val limit = minutes?.trim()?.toLongOrNull()?.coerceIn(1L, 1440L) ?: return true
    if (lastRunMs == null || nowMs < lastRunMs) return true
    return nowMs - lastRunMs >= limit * 60_000L
}

/** Strips the quotes Android wraps around a UTF-8 SSID; null for a blank or redacted (`<unknown ssid>`) value. */
internal fun normalizeSsid(raw: String?): String? {
    val s = raw?.trim().orEmpty().removeSurrounding("\"")
    return s.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID }
}

/** Maps `EXTRA_PLUGGED` to `wired` (AC or USB), `wireless`, or null when unplugged. */
internal fun pluggedKind(plugged: Int): String? = when (plugged) {
    BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB -> "wired"
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
    else -> null
}

/** Blank or unrecognised `orientation` never blocks. */
internal fun matchesOrientation(wanted: String?, current: Int): Boolean = when (wanted?.trim()?.lowercase()) {
    "portrait" -> current == Configuration.ORIENTATION_PORTRAIT
    "landscape" -> current == Configuration.ORIENTATION_LANDSCAPE
    else -> true
}
