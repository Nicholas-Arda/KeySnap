package com.example.service.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.BridgeConfig
import com.example.bridge.KeyEventMapping
import com.example.data.KeyMappingConfig
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssistantBridgeConfigTest {
    @Test fun assistantTriggersNeverReachTheBridgeButMixedScriptsKeepTheirPhysicalTrigger() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val payload = BridgePayload(context)
        val assistant = ScriptTrigger(listOf(KeyEventMapping.ASSISTANT_GESTURE), TriggerPressType.SINGLE_PRESS)
        val physical = ScriptTrigger(listOf(25), TriggerPressType.LONG_PRESS)
        val first = ShortcutScript("a", "a", triggers = listOf(assistant), actions = listOf(ScriptAction("cycle_mode")))
        val mixed = first.copy(id = "mixed", triggers = listOf(assistant, physical))
        assertTrue(payload.writeConfig(listOf(first, mixed), true, KeyMappingConfig()))
        assertFalse(payload.configFile()!!.readText().contains(KeyEventMapping.ASSISTANT_GESTURE.toString()))
        val parsed = BridgeConfig.read(payload.configFile())
        assertEquals(1, parsed.scripts.size)
        assertEquals("mixed", parsed.scripts.single().id)
        assertEquals(1, parsed.scripts.single().triggers.size)
    }
}
