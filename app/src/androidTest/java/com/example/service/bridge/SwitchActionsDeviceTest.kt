package com.example.service.bridge

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ScriptAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in phone probe: the bridge's fixed on/off actions for mobile data and NFC; puts both back as found. */
@RunWith(AndroidJUnit4::class)
class SwitchActionsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun dataOn() = shell("settings get global mobile_data").trim() == "1"
    private fun nfcOn() = shell("dumpsys nfc").contains("mState=on")

    @Test fun onAndOffActionsSetTheSwitch() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("switchActionsProbe") == "true")
        val payload = BridgePayload(instrumentation.targetContext)
        val client = BridgeClient(port = payload.controlPort, token = payload.controlToken)
        suspend fun run(type: String) {
            val json = JSONObject().put("actions", JSONArray().put(ScriptAction(type).toBridgeJson()))
            client.requestTestAction(json.toString())
        }
        suspend fun settle(expected: Boolean, read: () -> Boolean): Boolean {
            val until = SystemClock.elapsedRealtime() + 5_000
            while (read() != expected && SystemClock.elapsedRealtime() < until) delay(200)
            return read()
        }
        val data = dataOn()
        val nfc = nfcOn()
        try {
            for (on in listOf(!data, data)) {
                run(if (on) "enable_mobile_data" else "disable_mobile_data")
                assertEquals("mobile data", on, settle(on, ::dataOn))
            }
            for (on in listOf(!nfc, nfc)) {
                run(if (on) "enable_nfc" else "disable_nfc")
                assertEquals("NFC", on, settle(on, ::nfcOn))
            }
        } finally {
            if (dataOn() != data) run(if (data) "enable_mobile_data" else "disable_mobile_data")
            if (nfcOn() != nfc) run(if (nfc) "enable_nfc" else "disable_nfc")
        }
    }
}
