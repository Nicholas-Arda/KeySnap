package com.example.bridge

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.*
import android.media.AudioManager
import android.os.*
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Runs the real app and bridge evaluators against the same device snapshot. */
@RunWith(RobolectricTestRunner::class)
class ConstraintNegationParityTest {
    @Test fun allBridgeConstraintTypesMatchTheAppWithNegation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        RuntimeEnvironment.setQualifiers("+port-notnight")
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(false)
        shadowOf(context.getSystemService(BatteryManager::class.java)).setIsCharging(false)
        shadowOf(context.getSystemService(BatteryManager::class.java)).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY,42)
        shadowOf(context.getSystemService(BluetoothManager::class.java).adapter).setEnabled(false)
        context.getSystemService(android.net.wifi.WifiManager::class.java).setWifiEnabled(false)
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        context.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_NORMAL
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(true)
        context.getSystemService(NotificationManager::class.java).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceLocked(false)
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(context.getSystemService(android.location.LocationManager::class.java)).setLocationEnabled(false)
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED,0))
        Settings.Global.putInt(context.contentResolver,"mobile_data",0)
        Settings.Global.putInt(context.contentResolver,Settings.Global.AIRPLANE_MODE_ON,0)
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName,AppOpsManager.MODE_ALLOWED)
        AndroidConstraintEvaluator.rememberForegroundPackageForTest("test.app")
        FlashlightController.setTestState(false)
        val torch = TorchController()
        TorchController::class.java.getDeclaredField("knownState").apply { isAccessible = true }.set(torch,false)
        val bridge = ConstraintEvaluator(torch,null) { command ->
            when (command.joinToString(" ")) {
                "dumpsys power" -> "mWakefulness=Awake"
                "dumpsys battery" -> "status: 3\nlevel: 42\nAC powered: false\nUSB powered: false\nWireless powered: false"
                "dumpsys audio" -> "mMode=0\nConnected devices:\n"
                "dumpsys activity activities" -> "topResumedActivity=ActivityRecord{x u0 test.app/.Main}"
                "dumpsys media_session" -> "MEDIA SESSION SERVICE\nSessions Stack - have 0 sessions"
                "dumpsys bluetooth_manager" -> "enabled: false"
                "dumpsys display" -> "mCurrentOrientation=0"
                "cmd uimode night" -> "Night mode: no"
                "cmd wifi status" -> "Wifi is not connected"
                "dumpsys trust" -> "(current) deviceLocked=0"
                "dumpsys window policy" -> "KeyguardServiceDelegate\n showing=false"
                "settings get global mode_ringer" -> "2"
                "settings get secure location_mode" -> "0"
                else -> if (command.first() == "settings") "0" else ""
            }
        }
        val app = AndroidConstraintEvaluator(context)
        val params = mapOf("negate" to "true","packageName" to "other.app","percent" to "50","orientation" to "portrait",
            "ssid" to "","address" to "AA:BB:CC:DD:EE:FF","start" to "00:00","end" to "00:00","days" to "mon,tue,wed,thu,fri,sat,sun","mode" to "1")
        ScriptConstraintCatalog.all.filter { ScriptConstraintCatalog.isAvailable(it.type) && it.type !in ScriptConstraintCatalog.AUTOMATION_ONLY }.forEach {
            assertEquals(it.type,app.isSatisfied(ScriptConstraint(it.type,params)),bridge.isSatisfied(BridgeConfig.Constraint(it.type,params),true))
        }
    }
}
