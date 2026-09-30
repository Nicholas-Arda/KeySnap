package com.example.viewmodel

import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.data.isSurfaceOnly
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A shortcut needs a way to run: a key, an automation, or a widget/tile. No fourth state saves. */
class SurfaceOnlyTest {

    private val key = ScriptTrigger(listOf(25), TriggerPressType.SINGLE_PRESS)

    private fun script(triggers: List<ScriptTrigger> = emptyList(), automation: Boolean = false) = ShortcutScript(
        id = "s",
        name = "S",
        triggers = triggers,
        actions = listOf(ScriptAction.ToggleFlashlight),
        automation = automation,
    )

    private val draft = ScriptDraft(id = "s", name = "", enabled = true, trigger = null, actions = emptyList(), isNew = true)

    @Test fun aKeylessNonAutomationShortcutIsSurfaceOnly() {
        assertTrue(script().isSurfaceOnly)
    }

    @Test fun aKeyOrAnAutomationIsNotSurfaceOnly() {
        assertFalse(script(triggers = listOf(key)).isSurfaceOnly)
        assertFalse(script(automation = true).isSurfaceOnly)
    }

    @Test fun aDraftHasATriggerOnlyWithAKeyAnAutomationOrASurface() {
        assertFalse(draft.hasTrigger())
        assertTrue(draft.copy(trigger = key).hasTrigger())
        assertTrue(draft.copy(automation = true).hasTrigger())
        assertTrue(draft.copy(surfaceOnly = true).hasTrigger())
    }
}
