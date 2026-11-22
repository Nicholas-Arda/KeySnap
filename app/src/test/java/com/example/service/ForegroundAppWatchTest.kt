package com.example.service

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The foreground app has no broadcast: [ForegroundAppWatch] polls it only while it can matter. */
@RunWith(RobolectricTestRunner::class)
class ForegroundAppWatchTest {
    private val clock = ScriptConstraint("app_in_foreground", mapOf("packageName" to CLOCK))

    @Test fun runsOnlyWithTheScreenOnAndALiveForegroundAutomation() {
        val auto = automation(clock)
        assertTrue(ForegroundAppWatch.shouldRun(screenOn = true, listOf(auto), globallyEnabled = true))
        assertFalse("screen off", ForegroundAppWatch.shouldRun(false, listOf(auto), true))
        assertFalse("no scripts", ForegroundAppWatch.shouldRun(true, emptyList(), true))
        assertFalse("disabled", ForegroundAppWatch.shouldRun(true, listOf(auto.copy(enabled = false)), true))
        assertFalse("a key shortcut reads it on its press", ForegroundAppWatch.shouldRun(true, listOf(auto.copy(automation = false, triggers = listOf(ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)))), true))
        assertFalse("another condition", ForegroundAppWatch.shouldRun(true, listOf(automation(ScriptConstraint("dark_mode_on"))), true))
        assertFalse("paused", ForegroundAppWatch.shouldRun(true, listOf(auto), false))
        assertTrue("a resuming automation stays live while paused", ForegroundAppWatch.shouldRun(true, listOf(auto.copy(actions = listOf(ScriptAction("resume_mapping")))), false))
        for (type in listOf("app_not_in_foreground", "no_app_open")) {
            assertTrue(type, ForegroundAppWatch.shouldRun(true, listOf(automation(ScriptConstraint(type, mapOf("packageName" to CLOCK)))), true))
        }
    }

    @Test fun pollReportsOnlyChanges() = runTest {
        var foreground = LAUNCHER
        var changes = 0
        val job = launch { ForegroundAppWatch.poll({ foreground }, { changes++ }) }
        runCurrent()
        fun tick(next: String) {
            foreground = next
            advanceTimeBy(ForegroundAppWatch.POLL_MS)
            runCurrent()
        }
        tick(LAUNCHER)
        assertEquals("no change", 0, changes)
        tick(CLOCK)
        tick(CLOCK)
        assertEquals("staying in it is not a change", 1, changes)
        tick(LAUNCHER)
        tick(CLOCK)
        assertEquals(3, changes)
        job.cancel()
    }

    /** Launcher -> Clock -> Launcher -> Clock: each entry into Clock fires once, staying in it never. */
    @Test fun everyEntryIntoTheAppFiresOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coordinator = HardwareKeyTriggerCoordinator
        val repo = ScriptRepository.getInstance(context)
        var foreground = LAUNCHER
        try {
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            repo.scripts.value.forEach { repo.delete(it.id) }
            idle()
            coordinator.setConstraintEvaluatorForTest(object : ConstraintEvaluator {
                override fun isSatisfied(constraint: ScriptConstraint) =
                    constraint.type != "app_in_foreground" || constraint.params["packageName"] == foreground
            })
            repo.upsert(automation(clock))
            idle()
            val before = coordinator.triggerCount.value
            // What the watch does on each change, plus an unrelated broadcast's re-evaluation.
            fun switchTo(app: String) {
                foreground = app
                coordinator.reevaluateAutomations()
                coordinator.reevaluateAutomations()
                idle()
            }
            switchTo(CLOCK)
            assertEquals("first entry", before + 1, coordinator.triggerCount.value)
            switchTo(CLOCK)
            assertEquals("still in Clock", before + 1, coordinator.triggerCount.value)
            switchTo(LAUNCHER)
            switchTo(CLOCK)
            assertEquals("second entry", before + 2, coordinator.triggerCount.value)
            switchTo(LAUNCHER)
            switchTo(CLOCK)
            assertEquals("third entry", before + 3, coordinator.triggerCount.value)
        } finally {
            repo.scripts.value.forEach { repo.delete(it.id) }
            coordinator.initialize(context, KeyMappingRepository.getInstance(context))
            idle()
        }
    }

    private fun automation(vararg constraints: ScriptConstraint) = ShortcutScript(
        id = "qa-foreground",
        name = "qa-foreground",
        triggers = emptyList(),
        actions = listOf(ScriptAction("toggle_flashlight")),
        constraints = constraints.toList(),
        automation = true,
        vibrateOnTrigger = false,
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val CLOCK = "com.example.clock"
        const val LAUNCHER = "com.example.launcher"
    }
}
