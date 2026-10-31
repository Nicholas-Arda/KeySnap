package com.example.service

import android.app.*
import android.content.*
import android.media.AudioManager
import android.os.*
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.ConstraintNegation
import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ConstraintNegationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun negated(type: String, params: Map<String,String> = emptyMap()) = ScriptConstraint(type, params + ("negate" to "true"))

    @Before fun resetPermissionState() {
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName,AppOpsManager.MODE_IGNORED)
        shadowOf(context as Application).denyPermissions(android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.BLUETOOTH_CONNECT)
    }

    @Test fun devicePredicatesInvertKnownStateIncludingExistingInverseIds() {
        val evaluator = AndroidConstraintEvaluator(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode = AudioManager.MODE_NORMAL
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY,42)
        FlashlightController.setTestState(false)
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED,0))
        Settings.Global.putInt(context.contentResolver,"mobile_data",0)
        Settings.Global.putInt(context.contentResolver,Settings.Global.AIRPLANE_MODE_ON,0)
        RuntimeEnvironment.setQualifiers("+port-notnight")
        val params = mapOf(
            "battery_above" to mapOf("percent" to "50"), "battery_below" to mapOf("percent" to "50"),
            "screen_orientation" to mapOf("orientation" to "portrait"),
            "time" to mapOf("start" to "00:00","end" to "00:00"),
            "day_of_week" to mapOf("days" to "mon,tue,wed,thu,fri,sat,sun"),
        )
        val types = listOf("screen_on","screen_off","wifi_on","wifi_off","bluetooth_on","bluetooth_off",
            "charging","discharging","in_phone_call","not_in_phone_call","phone_ringing","flashlight_on","flashlight_off",
            "media_is_playing","no_media_playing","screen_orientation","dark_mode_on","dark_mode_off",
            "device_locked","device_unlocked","lock_screen_showing","lock_screen_not_showing",
            "battery_saver_on","battery_saver_off","charging_wired","charging_wireless","battery_above","battery_below",
            "location_on","location_off","time","day_of_week","mobile_data_on","mobile_data_off",
            "airplane_mode_on","airplane_mode_off","ringer_normal","ringer_vibrate","ringer_silent",
            "dnd_on","dnd_off","headphones_connected","headphones_disconnected",
            "connected_to_wifi_network","disconnected_from_wifi_network")
        for (type in types) {
            val p = params[type].orEmpty()
            assertEquals(type, !evaluator.isSatisfied(ScriptConstraint(type,p)), evaluator.isSatisfied(negated(type,p)))
        }
    }

    @Test fun foregroundAndBluetoothNegateKnownStates() {
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName,AppOpsManager.MODE_ALLOWED)
        AndroidConstraintEvaluator.rememberForegroundPackageForTest("test.app")
        val e = AndroidConstraintEvaluator(context)
        for (type in listOf("app_in_foreground","app_not_in_foreground")) {
            val p = mapOf("packageName" to "other.app")
            assertEquals(!e.isSatisfied(ScriptConstraint(type,p)),e.isSatisfied(negated(type,p)))
        }
        for (connected in listOf(true,false)) {
            DeviceEventReceiver.setConnectedForTest("AA:BB:CC:DD:EE:FF","Headset",connected)
            // A disabled adapter is positive evidence that no Bluetooth device is connected.
            val manager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            shadowOf(manager.adapter).setEnabled(true)
            for (type in listOf("bluetooth_device_connected","bluetooth_device_disconnected")) {
                val p = mapOf("address" to "AA:BB:CC:DD:EE:FF")
                assertEquals(type,!e.isSatisfied(ScriptConstraint(type,p)),e.isSatisfied(negated(type,p)))
            }
        }
    }

    @Test fun unreadableAndMalformedCannotBecomeTrue() {
        val e = AndroidConstraintEvaluator(context)
        val cases = listOf(
            negated("keyboard_showing"),negated("keyboard_not_showing"),
            negated("app_in_foreground",mapOf("packageName" to "test.app")),negated("app_not_in_foreground",mapOf("packageName" to "test.app")),
            negated("no_app_open"),negated("calendar_event_active"),negated("notification_received",mapOf("packageName" to "test.app")),
            negated("rss_new_item",mapOf("url" to "https://example.org/unread")),negated("at_time"),negated("interval"),
            negated("time",mapOf("start" to "bad","end" to "00:00")),negated("day_of_week",mapOf("days" to "invalid")),
            negated("battery_above",mapOf("percent" to "bad")),negated("battery_below",mapOf("percent" to "101")),
            negated("screen_orientation",mapOf("orientation" to "bad")),negated("bluetooth_device_connected"),
            negated("bluetooth_device_disconnected"),negated("future_type"),negated("mode_is",mapOf("mode" to "1")),
        )
        cases.forEach { assertFalse(it.toString(),e.isSatisfied(it)) }
        val noServices = object : ContextWrapper(context) { override fun getSystemService(name: String): Any? = null }
        val unavailable = AndroidConstraintEvaluator(noServices)
        for (type in listOf("screen_on","screen_off","wifi_on","wifi_off","bluetooth_on","bluetooth_off","charging","discharging",
            "in_phone_call","not_in_phone_call","phone_ringing","media_is_playing","no_media_playing","device_locked","device_unlocked",
            "lock_screen_showing","lock_screen_not_showing","battery_saver_on","battery_saver_off","battery_above","battery_below",
            "location_on","location_off","ringer_normal","ringer_vibrate","ringer_silent","dnd_on","dnd_off","headphones_connected",
            "headphones_disconnected","connected_to_wifi_network","disconnected_from_wifi_network")) {
            assertFalse(type,unavailable.isSatisfied(negated(type,mapOf("percent" to "50"))))
        }
    }

    @Test fun automationFiresOnTheNegatedFalseToTrueEdgeOnly() {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val e = AndroidConstraintEvaluator(context)
        val c = negated("screen_on")
        val edges = AutomationEdges()
        shadowOf(power).setIsInteractive(true)
        assertFalse(edges.update("s",e.isSatisfied(c)))
        shadowOf(power).setIsInteractive(false)
        assertTrue(edges.update("s",e.isSatisfied(c)))
        assertFalse(edges.update("s",e.isSatisfied(c)))
        shadowOf(power).setIsInteractive(true)
        assertFalse(edges.update("s",e.isSatisfied(c)))
        shadowOf(power).setIsInteractive(false)
        assertTrue(edges.update("s",e.isSatisfied(c)))
    }

    @Test fun timePulseNegationFiresAfterThePulseAndRssUnknownRemainsClosed() {
        val e = AndroidConstraintEvaluator(context)
        for (c in listOf(negated("at_time",mapOf("time" to "12:00")),negated("interval",mapOf("every" to "30","unit" to "m")))) {
            val states = mutableListOf<Boolean>()
            TimeSchedule.fire(System.currentTimeMillis(),listOf(c),seed = {},reevaluate = { states += e.isSatisfied(c); false })
            assertEquals(listOf(false,true),states)
        }
        val rss = negated("rss_new_item",mapOf("url" to "https://example.org/negated"))
        assertFalse(e.isSatisfied(rss))
        val states = mutableListOf<Boolean>()
        RssPolling.pulse(rss.params.getValue("url"),seed = {},reevaluate = { states += e.isSatisfied(rss) })
        assertEquals(listOf(false,true),states)
    }

    @Test fun calendarAndNotificationInvertOnlyReadableSources() {
        val e = AndroidConstraintEvaluator(context)
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.READ_CALENDAR)
        Robolectric.setupContentProvider(CalendarAutomationTest.FakeCalendar::class.java,android.provider.CalendarContract.AUTHORITY)
        CalendarAutomationTest.FakeCalendar.events = emptyList()
        assertTrue(e.isSatisfied(negated("calendar_event_active")))
        val now = System.currentTimeMillis()
        CalendarAutomationTest.FakeCalendar.events = listOf(now - 1000 to now + 60000)
        assertFalse(e.isSatisfied(negated("calendar_event_active")))
        CalendarAutomationTest.FakeCalendar.events = emptyList()
        val listener = Robolectric.buildService(NotificationEventListener::class.java).create().get()
        listener.onListenerConnected()
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(ComponentName(context,NotificationEventListener::class.java),true)
        val c = negated("notification_received",mapOf("packageName" to "test.app"))
        NotificationMatches.clearForTest()
        assertTrue(e.isSatisfied(c))
        NotificationMatches.record(listOf(c),"test.app","key",SystemClock.elapsedRealtime()) { NotificationContent(emptyList(),"message") }
        assertFalse(e.isSatisfied(c))
        assertNull(NotificationMatches.triggeringKey(listOf(c),SystemClock.elapsedRealtime()))
        assertFalse(ScriptConstraintCatalog.startsFromNotification(true,listOf(c)))
        listener.onListenerDisconnected()
        assertFalse(e.isSatisfied(c))
    }

    @Test fun cooldownIsNeutralInTheEdgeAndNegatedInItsHistoryGate() {
        val c = negated("cooldown",mapOf("minutes" to "10"))
        assertTrue(AndroidConstraintEvaluator(context).isSatisfied(c))
        assertFalse(ConstraintNegation.cooldownAllows(null,c.params,1000))
        assertTrue(ConstraintNegation.cooldownAllows(0,c.params,599999))
        assertFalse(ConstraintNegation.cooldownAllows(0,c.params,600000))
        assertFalse(ConstraintNegation.cooldownAllows(0,mapOf("negate" to "true","minutes" to "bad"),1000))
    }
}
