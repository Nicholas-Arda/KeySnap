package com.example.service

import android.app.KeyguardManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScriptConstraint
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBluetoothAdapter
import org.robolectric.shadows.ShadowLocationManager

@RunWith(RobolectricTestRunner::class)
class ConstraintEvaluatorTest {
    private lateinit var context: Context
    private lateinit var evaluator: AndroidConstraintEvaluator

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        evaluator = AndroidConstraintEvaluator(context)
    }

    @Test fun screenOnAndOffReflectPowerManagerInteractiveState() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerManager).setIsInteractive(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("screen_on")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("screen_off")))

        shadowOf(powerManager).setIsInteractive(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("screen_on")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("screen_off")))
    }

    @Test fun wifiOnAndOffReflectWifiManagerState() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.setWifiEnabled(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("wifi_on")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("wifi_off")))

        wifiManager.setWifiEnabled(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("wifi_on")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("wifi_off")))
    }

    @Test fun bluetoothOnAndOffReflectAdapterState() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val shadowAdapter = shadowOf(adapter) as ShadowBluetoothAdapter
        shadowAdapter.setEnabled(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("bluetooth_on")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("bluetooth_off")))

        shadowAdapter.setEnabled(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("bluetooth_on")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("bluetooth_off")))
    }

    @Test fun chargingAndDischargingReflectBatteryManagerState() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        shadowOf(batteryManager).setIsCharging(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("charging")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("discharging")))

        shadowOf(batteryManager).setIsCharging(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("charging")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("discharging")))
    }

    @Test fun inPhoneCallAndNotInPhoneCallReflectAudioManagerMode() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_CALL
        assertTrue(evaluator.isSatisfied(ScriptConstraint("in_phone_call")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("not_in_phone_call")))

        audioManager.mode = AudioManager.MODE_NORMAL
        assertFalse(evaluator.isSatisfied(ScriptConstraint("in_phone_call")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("not_in_phone_call")))
    }

    @Test fun flashlightOnAndOffReflectFlashlightControllerState() {
        FlashlightController.setTestState(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("flashlight_on")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("flashlight_off")))

        FlashlightController.setTestState(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("flashlight_on")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("flashlight_off")))
    }

    @Test fun anUnavailableOrUnknownConstraintTypeNeverBlocksAScript() {
        assertTrue(evaluator.isSatisfied(ScriptConstraint("hinge_open")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("not_a_real_constraint")))
    }

    // Pure predicate helpers.

    @Test fun timeWindowIsHalfOpenAndWrapsMidnight() {
        assertTrue(isWithinTimeWindow("09:00", "17:00", LocalTime.of(9, 0)))
        assertTrue(isWithinTimeWindow("09:00", "17:00", LocalTime.of(16, 59)))
        assertFalse(isWithinTimeWindow("09:00", "17:00", LocalTime.of(17, 0)))
        assertFalse(isWithinTimeWindow("09:00", "17:00", LocalTime.of(3, 0)))
        // Wrap: 22:00 -> 06:00 covers late evening and early morning, not the afternoon.
        assertTrue(isWithinTimeWindow("22:00", "06:00", LocalTime.of(23, 30)))
        assertTrue(isWithinTimeWindow("22:00", "06:00", LocalTime.of(2, 0)))
        assertFalse(isWithinTimeWindow("22:00", "06:00", LocalTime.of(6, 0)))
        assertFalse(isWithinTimeWindow("22:00", "06:00", LocalTime.of(14, 0)))
        // end == start wraps too, so it is always satisfied.
        assertTrue(isWithinTimeWindow("08:00", "08:00", LocalTime.of(12, 0)))
    }

    @Test fun malformedTimeNeverBlocks() {
        assertTrue(isWithinTimeWindow(null, "17:00", LocalTime.NOON))
        assertTrue(isWithinTimeWindow("9", "17:00", LocalTime.NOON))
        assertTrue(isWithinTimeWindow("25:00", "17:00", LocalTime.NOON))
        assertTrue(isWithinTimeWindow("09:00", "17:60", LocalTime.NOON))
        assertNull(parseHhMm("9am"))
        assertEquals(LocalTime.of(7, 5), parseHhMm(" 07:05 "))
    }

    @Test fun daySetParsesLowercaseThreeLetterNamesAndEmptyNeverBlocks() {
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY), parseDaySet("mon, fri,sun"))
        assertTrue(isDayInSet("sat,sun", DayOfWeek.SATURDAY))
        assertFalse(isDayInSet("sat,sun", DayOfWeek.WEDNESDAY))
        assertTrue(isDayInSet("", DayOfWeek.WEDNESDAY))
        assertTrue(isDayInSet(null, DayOfWeek.WEDNESDAY))
        assertTrue(isDayInSet("funday", DayOfWeek.WEDNESDAY))
    }

    @Test fun percentComparisonIsStrictAndUnparsableNeverBlocks() {
        assertTrue(comparePercent("50", 51, above = true))
        assertFalse(comparePercent("50", 50, above = true))
        assertTrue(comparePercent("50", 49, above = false))
        assertFalse(comparePercent("50", 50, above = false))
        assertTrue(comparePercent("half", 10, above = true))
        assertTrue(comparePercent(null, 10, above = false))
        assertTrue(comparePercent("50", null, above = true))
    }

    @Test fun ssidNormalizationStripsQuotesAndDropsRedactedValue() {
        assertEquals("Home Net", normalizeSsid("\"Home Net\""))
        assertEquals("Home Net", normalizeSsid("Home Net"))
        assertNull(normalizeSsid(WifiManager.UNKNOWN_SSID))
        assertNull(normalizeSsid(""))
        assertNull(normalizeSsid(null))
    }

    @Test fun pluggedKindMapsAcAndUsbToWiredAndWirelessToWireless() {
        assertEquals("wired", pluggedKind(BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals("wired", pluggedKind(BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals("wireless", pluggedKind(BatteryManager.BATTERY_PLUGGED_WIRELESS))
        assertNull(pluggedKind(0))
    }

    @Test fun orientationMatchesConfigurationAndBlankNeverBlocks() {
        assertTrue(matchesOrientation("portrait", Configuration.ORIENTATION_PORTRAIT))
        assertFalse(matchesOrientation("portrait", Configuration.ORIENTATION_LANDSCAPE))
        assertTrue(matchesOrientation("Landscape", Configuration.ORIENTATION_LANDSCAPE))
        assertTrue(matchesOrientation("", Configuration.ORIENTATION_LANDSCAPE))
        assertTrue(matchesOrientation(null, Configuration.ORIENTATION_PORTRAIT))
    }

    // Shadow-backed device state.

    @Test fun batterySaverReflectsPowerManager() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerManager).setIsPowerSaveMode(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("battery_saver_on")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("battery_saver_off")))

        shadowOf(powerManager).setIsPowerSaveMode(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("battery_saver_on")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("battery_saver_off")))
    }

    @Test fun lockConstraintsReflectKeyguardManager() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setIsDeviceLocked(true)
        shadowOf(keyguard).setKeyguardLocked(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("device_locked")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("device_unlocked")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("lock_screen_showing")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("lock_screen_not_showing")))

        shadowOf(keyguard).setIsDeviceLocked(false)
        shadowOf(keyguard).setKeyguardLocked(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("device_locked")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("device_unlocked")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("lock_screen_showing")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("lock_screen_not_showing")))
    }

    @Test fun ringerAndRingingReflectAudioManager() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertTrue(evaluator.isSatisfied(ScriptConstraint("ringer_vibrate")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("ringer_normal")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("ringer_silent")))

        audioManager.mode = AudioManager.MODE_RINGTONE
        assertTrue(evaluator.isSatisfied(ScriptConstraint("phone_ringing")))
        audioManager.mode = AudioManager.MODE_NORMAL
        assertFalse(evaluator.isSatisfied(ScriptConstraint("phone_ringing")))
    }

    @Test fun batteryLevelAndChargerKindReflectBatteryState() {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        shadowOf(batteryManager).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 42)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("battery_above", mapOf("percent" to "40"))))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("battery_above", mapOf("percent" to "42"))))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("battery_below", mapOf("percent" to "50"))))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("battery_below", mapOf("percent" to "42"))))

        val sticky = Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        context.sendStickyBroadcast(sticky)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("charging_wired")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("charging_wireless")))
    }

    @Test fun locationReflectsLocationManager() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadow = shadowOf(locationManager) as ShadowLocationManager
        shadow.setLocationEnabled(true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("location_on")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("location_off")))

        shadow.setLocationEnabled(false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("location_on")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("location_off")))
    }

    @Test fun airplaneModeAndMobileDataReflectGlobalSettings() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("airplane_mode_on")))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("airplane_mode_off")))
        Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("airplane_mode_on")))

        Settings.Global.putInt(context.contentResolver, "mobile_data", 0)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("mobile_data_on")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("mobile_data_off")))
        Settings.Global.putInt(context.contentResolver, "mobile_data", 1)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("mobile_data_on")))
    }

    @Test fun bluetoothDeviceConstraintsFollowTheAclTrackedSet() {
        val address = "AA:BB:CC:DD:EE:FF"
        val params = mapOf("address" to address, "name" to "Headset")
        DeviceEventReceiver.setConnectedForTest(address, "Headset", true)
        assertTrue(evaluator.isSatisfied(ScriptConstraint("bluetooth_device_connected", params)))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("bluetooth_device_disconnected", params)))
        // Blank address falls back to the name, case-insensitively, so the app agrees with the bridge's anonymized view.
        assertTrue(evaluator.isSatisfied(ScriptConstraint("bluetooth_device_connected", mapOf("address" to "", "name" to "headset"))))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("bluetooth_device_connected", mapOf("address" to "", "name" to "Other"))))
        assertFalse(evaluator.isSatisfied(ScriptConstraint("bluetooth_device_connected", mapOf("address" to "11:22:33:44:55:66", "name" to "Headset"))))

        DeviceEventReceiver.setConnectedForTest(address, "Headset", false)
        assertFalse(evaluator.isSatisfied(ScriptConstraint("bluetooth_device_connected", params)))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("bluetooth_device_disconnected", params)))
    }

    @Test fun unknownStateNeverBlocks() {
        // No Accessibility service instance in a JVM test, no Usage Access, no location grant.
        assertTrue(evaluator.isSatisfied(ScriptConstraint("keyboard_showing")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("keyboard_not_showing")))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("app_in_foreground", mapOf("packageName" to "com.example.other"))))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("app_not_in_foreground", mapOf("packageName" to "com.example.other"))))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("time", mapOf("start" to "bad", "end" to "09:00"))))
        assertTrue(evaluator.isSatisfied(ScriptConstraint("day_of_week", mapOf("days" to ""))))
    }
}
