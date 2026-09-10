package com.example.service

import android.Manifest
import android.app.AppOpsManager
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
import android.provider.Settings
import android.util.Log
import com.example.data.ScriptConstraint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Answers whether a single [ScriptConstraint] currently holds, so a script only fires while every constraint is satisfied. */
interface ConstraintEvaluator {
    fun isSatisfied(constraint: ScriptConstraint): Boolean
}

/**
 * Reads live device state via Android APIs. Semantics here must match
 * `com.example.bridge.ConstraintEvaluator` in `:bridge`, which answers the same
 * predicates via shell commands since the detached bridge process has no [Context].
 *
 * Unknown never blocks: whenever the state cannot be read (missing permission, no service
 * instance, malformed parameter, API failure) the constraint reads as satisfied, so a script
 * degrades to "always" rather than silently dying.
 */
class AndroidConstraintEvaluator(private val appContext: Context) : ConstraintEvaluator {

    override fun isSatisfied(constraint: ScriptConstraint): Boolean {
        val p = constraint.params
        return when (constraint.type) {
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
            "phone_ringing" -> audio { it.mode == AudioManager.MODE_RINGTONE } ?: false
            "battery_saver_on" -> power { it.isPowerSaveMode } ?: false
            "battery_saver_off" -> power { !it.isPowerSaveMode } ?: true
            "charging_wired" -> pluggedKind(pluggedExtra()) == "wired"
            "charging_wireless" -> pluggedKind(pluggedExtra()) == "wireless"
            "battery_above" -> comparePercent(p["percent"], batteryPercent(), above = true)
            "battery_below" -> comparePercent(p["percent"], batteryPercent(), above = false)
            "location_on" -> isLocationEnabled() ?: true
            "location_off" -> isLocationEnabled()?.not() ?: true
            "time" -> isWithinTimeWindow(p["start"], p["end"], LocalTime.now())
            "day_of_week" -> isDayInSet(p["days"], LocalDate.now().dayOfWeek)
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
            // A constraint type that isn't AVAILABLE never blocks a script.
            else -> true
        }
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

    private fun isInPhoneCall(): Boolean = audio { it.mode == AudioManager.MODE_IN_CALL } ?: false

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

    private fun foregroundPackage(): String? = try {
        val appOps = service<AppOpsManager>(Context.APP_OPS_SERVICE)
        val mode = appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), appContext.packageName)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            null
        } else {
            val usage = service<UsageStatsManager>(Context.USAGE_STATS_SERVICE)
            val now = System.currentTimeMillis()
            val events = usage?.queryEvents(now - FOREGROUND_LOOKBACK_MS, now)
            val event = UsageEvents.Event()
            var last: String? = null
            while (events?.hasNextEvent() == true) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = event.packageName
            }
            last
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error reading foreground app state", e)
        null
    }

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
        private const val FOREGROUND_LOOKBACK_MS = 60_000L
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
