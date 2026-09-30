package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.EntitlementManager
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ShortcutScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A widget or tile tap obeys the key path's rules, and a tap that does nothing always says why —
 * "nothing stops silently" is the lesson from IFTTT's reviews.
 */
@RunWith(RobolectricTestRunner::class)
class SurfaceRunTest {

    private val res = ApplicationProvider.getApplicationContext<Context>().resources
    private val met: (ShortcutScript) -> Boolean = { true }
    private val unmet: (ShortcutScript) -> Boolean = { false }

    private fun script(enabled: Boolean = true, automation: Boolean = false) = ShortcutScript(
        id = "s",
        name = "Torch",
        enabled = enabled,
        triggers = emptyList(),
        actions = listOf(ScriptAction.ToggleFlashlight),
        constraints = listOf(ScriptConstraint("charging")),
        automation = automation,
    )

    @Test fun anEnabledScriptWhoseConditionsHoldRuns() {
        assertNull(surfaceGate(script(), globallyEnabled = true, conditionsMet = met))
    }

    @Test fun theGlobalPauseWinsOverEverythingElse() {
        assertEquals(SurfaceRun.GloballyPaused, surfaceGate(script(enabled = false), globallyEnabled = false, conditionsMet = unmet))
    }

    @Test fun aPausedScriptSaysSoByName() {
        assertEquals(SurfaceRun.ScriptPaused("Torch"), surfaceGate(script(enabled = false), globallyEnabled = true, conditionsMet = met))
    }

    @Test fun anUnmetOnlyIfStopsAKeyShortcut() {
        assertEquals(SurfaceRun.ConditionsUnmet("Torch"), surfaceGate(script(), globallyEnabled = true, conditionsMet = unmet))
    }

    @Test fun anAutomationRunsWhateverItsConditionsSay() {
        // An automation's conditions are its trigger, not a filter; a tap is a second trigger.
        assertNull(surfaceGate(script(automation = true), globallyEnabled = true, conditionsMet = unmet))
    }

    @Test fun aFullRunIsSilentAndAShortOneIsNot() {
        assertNull(SurfaceRun.Ran("Torch", ran = 3, total = 3).message(res))
        assertEquals("Ran 1 of 3 actions", SurfaceRun.Ran("Torch", ran = 1, total = 3).message(res))
        assertEquals("Nothing ran. Check the action settings.", SurfaceRun.Ran("Torch", ran = 0, total = 3).message(res))
    }

    @Test fun everyRefusalExplainsItself() {
        listOf(
            SurfaceRun.Missing,
            SurfaceRun.GloballyPaused,
            SurfaceRun.ScriptPaused("Torch"),
            SurfaceRun.ConditionsUnmet("Torch"),
            SurfaceRun.WidgetOverLimit,
        ).forEach { assertFalse("$it", it.message(res).isNullOrBlank()) }
    }

    @Test fun anOverLimitWidgetNamesTheFreeLimitAndPro() {
        val text = SurfaceRun.WidgetOverLimit.message(res).orEmpty()
        assertTrue(text, "${EntitlementManager.FREE_WIDGET_LIMIT}" in text && "Pro" in text)
    }
}
