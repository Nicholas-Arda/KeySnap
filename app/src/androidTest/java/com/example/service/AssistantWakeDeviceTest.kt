package com.example.service

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.bridge.BridgeProtocol
import com.example.service.bridge.BridgeClient
import com.example.service.bridge.BridgePayload
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in phone acceptance; never changes saved shortcuts or the selected assistant. */
@RunWith(AndroidJUnit4::class)
class AssistantWakeDeviceTest {
    @Test fun bothAssistantActionsShowTheActiveVoiceSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("assistantWakeProbe") == "true")
        val automation = instrumentation.uiAutomation
        val payload = BridgePayload(context)
        val client = BridgeClient(port = payload.controlPort, token = payload.controlToken)
        val directory = context.getExternalFilesDir("assistant-wake")!!.apply { mkdirs() }
        ActivityScenario.launch(MainActivity::class.java).use {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (client.probe()?.protocolVersion != BridgeProtocol.VERSION && SystemClock.elapsedRealtime() < deadline) delay(250)
            assertEquals("Updated detached bridge must be running", BridgeProtocol.VERSION, client.probe()?.protocolVersion)
            for (type in listOf("launch_voice_assistant", "launch_device_assistant")) {
                val request = JSONObject().put("actions", JSONArray().put(JSONObject().put("type", type)))
                assertEquals("Bridge must open the session itself", 1, client.requestRun(request.toString())?.ran)
                val shownDeadline = SystemClock.elapsedRealtime() + 8_000
                var shown = false
                while (!shown && SystemClock.elapsedRealtime() < shownDeadline) {
                    val descriptor = automation.executeShellCommand("dumpsys voiceinteraction")
                    val state = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                    shown = state.contains("mShown=true")
                    if (!shown) delay(100)
                }
                assertTrue("Android must report a shown voice session for $type", shown)
                // A cold Google process can report its session before the overlay's first frame.
                delay(2_000)
                automation.takeScreenshot()?.let { bitmap ->
                    File(directory, "$type.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                val hide = automation.executeShellCommand("cmd voiceinteraction hide")
                ParcelFileDescriptor.AutoCloseInputStream(hide).use { it.readBytes() }
                delay(300)
            }
        }
    }
}
