package com.example.mode

import com.example.data.ScriptAction
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edition-independent gate used by save, import, and the cycle count field. */
class ModeFeatureGateTest {
    private val switch = ShortcutScript(
        id = "switch",
        name = "switch",
        triggers = emptyList(),
        actions = listOf(ScriptAction(ModeHooks.CYCLE_MODE, mapOf("count" to "3"))),
    )

    @Test fun savingAModeFeatureNeedsPro() {
        assertFalse(ModeHooks.allows(ModeHooks.CYCLE_MODE, isPro = false))
        assertFalse(ModeHooks.allows(ScriptConstraintCatalog.MODE_IS, isPro = false))
        assertEquals(ModeHooks.AVAILABLE, ModeHooks.allows(ModeHooks.CYCLE_MODE, isPro = true))
        assertEquals(ModeHooks.AVAILABLE, ModeHooks.allows(ScriptConstraintCatalog.MODE_IS, isPro = true))
        assertTrue(ModeHooks.allows("toggle_flashlight", isPro = false))
    }

    @Test fun modeConditionStaysVisibleUnlessRunsInIsDrawn() {
        assertFalse(ModeHooks.hidesFromEditor(ScriptConstraintCatalog.MODE_IS, "other", emptyList(), isPro = true))
        assertFalse(ModeHooks.hidesFromEditor(ScriptConstraintCatalog.MODE_IS, "other", listOf(switch), isPro = false))
        assertEquals(
            ModeHooks.AVAILABLE,
            ModeHooks.hidesFromEditor(ScriptConstraintCatalog.MODE_IS, "other", listOf(switch), isPro = true),
        )
        assertFalse(ModeHooks.hidesFromEditor(ScriptConstraintCatalog.MODE_IS, switch.id, listOf(switch), isPro = true))
        assertFalse(ModeHooks.hidesFromEditor("screen_on", "other", listOf(switch), isPro = true))
    }

    @Test fun cycleCountFollowsTheModesListOnceASwitchExists() {
        assertFalse(ModeHooks.cycleCountLocked(ModeHooks.CYCLE_MODE, "count", emptyList()))
        assertFalse(ModeHooks.cycleCountLocked(ModeHooks.CYCLE_MODE, "feedback", listOf(switch)))
        assertEquals(ModeHooks.AVAILABLE, ModeHooks.cycleCountLocked(ModeHooks.CYCLE_MODE, "count", listOf(switch)))
        val aligned = ModeHooks.alignCycleCount(
            ModeHooks.CYCLE_MODE,
            mapOf("count" to "9", "feedback" to "toast"),
            listOf(switch),
        )
        assertEquals("toast", aligned["feedback"])
        assertEquals(if (ModeHooks.AVAILABLE) "3" else "9", aligned["count"])
    }
}
