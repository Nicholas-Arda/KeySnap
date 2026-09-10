package com.example.service.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.BridgeConfig
import com.example.bridge.BridgeProtocol
import com.example.data.KeyMappingConfig
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the app-to-bridge contract.
 *
 * The two run in different processes with no shared memory, so a config the app writes is only
 * useful if the bridge's own parser understands it. These tests write with the production writer
 * and read back with the production reader, so a change to either side that breaks the pairing
 * fails here instead of silently disabling Advanced Mode on a device.
 */
@RunWith(RobolectricTestRunner::class)
class BridgePayloadTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun flashlightScript(
        keyCode: Int = 25,
        pressType: TriggerPressType = TriggerPressType.DOUBLE_PRESS,
        enabled: Boolean = true,
    ) = ShortcutScript(
        id = "script-1",
        name = "Flashlight mapping",
        enabled = enabled,
        triggers = listOf(ScriptTrigger(listOf(keyCode), pressType)),
        actions = listOf(ScriptAction.ToggleFlashlight),
    )

    @Test
    fun writtenConfigIsReadableByTheBridgeParser() {
        val payload = BridgePayload(context)
        val written = payload.writeConfig(
            scripts = listOf(flashlightScript()),
            globallyEnabled = true,
            config = KeyMappingConfig(doublePressWindowMs = 300L, longPressThresholdMs = 500L),
        )
        assertTrue(written)

        val parsed = BridgeConfig.read(payload.configFile()!!)
        assertEquals(payload.controlPort, parsed.port)
        assertEquals(payload.controlToken, parsed.token)
        assertEquals(300L, parsed.doublePressWindowMs)
        assertEquals(500L, parsed.longPressThresholdMs)
        assertEquals(1, parsed.scripts.size)
        assertEquals(BridgeConfig.ACTION_TOGGLE_FLASHLIGHT, parsed.scripts[0].actions[0].type)
    }

    @Test
    fun pressTypeAndKeyCodeSurviveTheRoundTrip() {
        val payload = BridgePayload(context)
        payload.writeConfig(
            listOf(flashlightScript(keyCode = 25, pressType = TriggerPressType.LONG_PRESS)),
            globallyEnabled = true,
            config = KeyMappingConfig(),
        )
        val parsed = BridgeConfig.read(payload.configFile()!!)

        assertTrue(parsed.isMappedKey(25))
        assertFalse(parsed.isMappedKey(24))
        assertEquals(1, parsed.scriptsFor(25, BridgeConfig.PRESS_LONG).size)
        // A press type the user did not configure must not fire the script.
        assertEquals(0, parsed.scriptsFor(25, BridgeConfig.PRESS_DOUBLE).size)
    }

    @Test
    fun pausedGlobalMappingDisablesEveryScript() {
        val payload = BridgePayload(context)
        payload.writeConfig(listOf(flashlightScript()), globallyEnabled = false, config = KeyMappingConfig())
        val parsed = BridgeConfig.read(payload.configFile()!!)

        assertFalse(parsed.isMappedKey(25))
        assertEquals(0, parsed.scriptsFor(25, BridgeConfig.PRESS_DOUBLE).size)
    }

    @Test
    fun pausedScriptDisablesOnlyThatScript() {
        val payload = BridgePayload(context)
        val disabled = flashlightScript(enabled = false).copy(id = "disabled")
        val active = flashlightScript(keyCode = 24).copy(id = "active")
        payload.writeConfig(listOf(disabled, active), globallyEnabled = true, config = KeyMappingConfig())
        val parsed = BridgeConfig.read(payload.configFile()!!)

        assertFalse(parsed.isMappedKey(25))
        assertTrue(parsed.isMappedKey(24))
        assertEquals(1, parsed.scriptsFor(24, BridgeConfig.PRESS_DOUBLE).size)
    }

    @Test
    fun controlPortAndTokenAreStableAcrossInstances() {
        // The port is written into the config for the bridge to bind and used by the client to
        // reconnect; if these ever disagreed the app could never find its own bridge.
        val first = BridgePayload(context)
        val port = first.controlPort
        val token = first.controlToken

        val second = BridgePayload(context)
        assertEquals(port, second.controlPort)
        assertEquals(token, second.controlToken)
        assertTrue(port in 38_200 until 38_700)
        assertEquals(32, token.length)
    }

    @Test
    fun parameterizedActionsAndPerTriggerTimingOverridesSurviveTheRoundTrip() {
        val payload = BridgePayload(context)
        val script = ShortcutScript(
            id = "script-2",
            name = "Brightness",
            enabled = true,
            triggers = listOf(ScriptTrigger(listOf(24), TriggerPressType.LONG_PRESS, doublePressWindowMs = 450L, longPressThresholdMs = 900L)),
            actions = listOf(com.example.data.ScriptAction("change_flashlight_brightness", mapOf("strength" to "3"))),
        )
        payload.writeConfig(listOf(script), globallyEnabled = true, config = KeyMappingConfig())
        val parsed = BridgeConfig.read(payload.configFile()!!)

        val parsedAction = parsed.scripts.single().actions.single()
        assertEquals("change_flashlight_brightness", parsedAction.type)
        assertEquals("3", parsedAction.params["strength"])
        // A trigger-level override must win over the config-wide default resolved for that key.
        assertEquals(450L, parsed.doublePressWindowFor(24))
        assertEquals(900L, parsed.longPressThresholdFor(24))
    }

    @Test
    fun spawnCommandDetachesTheBridgeFromTheAdbSession() {
        val command = BridgePayload(context).spawnCommand()
        assertNotNull(command)
        // setsid gives the bridge its own session so closing the ADB stream cannot SIGHUP it,
        // and redirecting all three standard streams detaches it from the transport.
        assertTrue(command!!.contains("setsid"))
        assertTrue(command.contains("nohup"))
        assertTrue(command.contains("</dev/null"))
        assertTrue(command.contains(BridgeProtocol.SPAWN_MARKER))
    }
}
