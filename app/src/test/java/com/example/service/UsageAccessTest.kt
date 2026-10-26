package com.example.service

import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAccessTest {
    @Test fun enabledKeyShortcutsNeedTheGrantRegardlessOfHowTheyWereCreated() {
        for (type in listOf("no_app_open", "app_in_foreground", "app_not_in_foreground")) {
            val script = ShortcutScript(
                "imported", "Imported shortcut", true,
                listOf(ScriptTrigger(listOf(25), TriggerPressType.SINGLE_PRESS)),
                listOf(ScriptAction.ToggleFlashlight), listOf(ScriptConstraint(type)),
            )
            assertTrue(needsUsageAccess(listOf(script)))
            assertFalse(needsUsageAccess(listOf(script.copy(enabled = false))))
            assertFalse(needsUsageAccess(listOf(script.copy(triggers = emptyList()))))
            assertFalse(needsUsageAccess(listOf(script.copy(constraints = listOf(ScriptConstraint("no_media_playing"))))))
        }
        assertFalse(needsUsageAccess(emptyList()))
    }
}
