package com.example.capsule

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bridge.BridgeProtocol
import com.example.service.bridge.BridgeClient
import com.example.service.bridge.BridgePayload
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real launches through the production socket; saved shortcuts are never changed. */
@RunWith(AndroidJUnit4::class)
class BridgeLaunchAppDeviceTest {
    @Test fun bridgeOpensNonDefaultLauncherActivities() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("launchAppProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val payload = BridgePayload(context)
        val client = BridgeClient(port = payload.controlPort, token = payload.controlToken)
        assertEquals(BridgeProtocol.VERSION, client.probe()?.protocolVersion)
        try {
            for (pkg in listOf("com.coloros.calculator", "com.anthropic.claude", "com.android.settings")) {
                val action = JSONObject().put("type", "launch_app").put("params", JSONObject().put("packageName", pkg))
                val result = client.requestRun(JSONObject().put("actions", JSONArray().put(action)).toString())
                assertEquals("Bridge launch failed for $pkg", 1, result?.ran)
                val deadline = SystemClock.uptimeMillis() + 5000
                var resumed = false
                while (!resumed && SystemClock.uptimeMillis() < deadline) {
                    val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                    val activity = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                    resumed = activity.lineSequence().any { it.contains("topResumedActivity") && it.contains("$pkg/") }
                    if (!resumed) SystemClock.sleep(100)
                }
                assertTrue("The requested app did not reach the foreground: $pkg", resumed)
            }
        } finally {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
