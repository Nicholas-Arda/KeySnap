package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyConsumptionTest {
    private val script = ShortcutScript(
        "volume", "Volume", true,
        listOf(ScriptTrigger(listOf(25), TriggerPressType.SINGLE_PRESS)),
        listOf(ScriptAction.ToggleFlashlight),
        constraints = listOf(ScriptConstraint("no_media_playing")),
    )

    @Test fun matchingKeyRespectsConstraintsAndConsumeOverrides() {
        assertTrue(shouldConsumeKey(listOf(script), 25, true, true) { true })
        assertFalse(shouldConsumeKey(listOf(script), 25, true, true) { false })
        assertFalse(shouldConsumeKey(listOf(script.copy(consumeOriginalEvent = false)), 25, true, true) { true })
        assertTrue(shouldConsumeKey(listOf(script.copy(consumeOriginalEvent = true)), 25, true, false) { true })
        assertFalse(shouldConsumeKey(listOf(script.copy(consumeOriginalEvent = true)), 25, true, false) { false })
        assertTrue(shouldConsumeKey(listOf(script.copy(constraints = emptyList())), 25, true, true))
        // A caller that omits evaluation must never silently ignore a constraint.
        assertFalse(shouldConsumeKey(listOf(script), 25, true, true))
    }

    @Test fun unrelatedDisabledPausedAndNonConsumingScriptsNeverEvaluate() {
        val unexpected: (ShortcutScript) -> Boolean = { error("Should not evaluate") }
        assertFalse(shouldConsumeKey(listOf(script), 24, true, true, unexpected))
        assertFalse(shouldConsumeKey(listOf(script.copy(enabled = false)), 25, true, true, unexpected))
        assertFalse(shouldConsumeKey(listOf(script), 25, false, true, unexpected))
        assertFalse(shouldConsumeKey(listOf(script), 25, true, false, unexpected))
    }

    @Test fun anyEligibleScriptCanConsumeAndChordMembersKeepTheirOldMeaning() {
        val chord = script.copy(id = "chord", triggers = listOf(ScriptTrigger(listOf(24, 25), TriggerPressType.LONG_PRESS)))
        assertTrue(shouldConsumeKey(listOf(script, chord), 25, true, true) { it.id == "chord" })
        assertTrue(shouldConsumeKey(listOf(chord), 24, true, true) { true })
        assertFalse(shouldConsumeKey(listOf(chord), 24, true, true) { false })
        assertFalse(shouldConsumeKey(listOf(chord), 26, true, true) { true })
    }

    @Test fun downDecisionSurvivesConstraintChangesRepeatsAndLongPressRelease() {
        for (initial in listOf(false, true)) {
            val tracker = KeyConsumptionTracker()
            var allowed = initial
            var calls = 0
            val decide = { calls++; allowed }
            assertEquals(initial, tracker.onEvent(25, 1, 100L, true, 0, decide))
            allowed = !initial
            assertEquals(initial, tracker.onEvent(25, 1, 100L, true, 1, decide))
            assertEquals(initial, tracker.onEvent(25, 1, 100L, true, 8, decide))
            assertEquals(initial, tracker.onEvent(25, 1, 100L, false, 0, decide))
            assertEquals(1, calls)
            assertEquals(!initial, tracker.onEvent(25, 1, 200L, true, 0, decide))
            assertEquals(2, calls)
        }
    }

    @Test fun keysAndDevicesAreIndependentAndMissingEventsFailOpen() {
        val tracker = KeyConsumptionTracker()
        assertTrue(tracker.onEvent(25, 1, 100L, true, 0) { true })
        assertFalse(tracker.onEvent(24, 1, 100L, true, 0) { false })
        assertFalse(tracker.onEvent(25, 2, 100L, true, 0) { false })
        assertFalse(tracker.onEvent(25, 2, 100L, false, 0) { error("up") })
        assertTrue(tracker.onEvent(25, 1, 100L, false, 0) { error("up") })
        assertFalse(tracker.onEvent(99, 1, 100L, false, 0) { error("orphan up") })
        assertFalse(tracker.onEvent(99, 1, 200L, true, 2) { error("orphan repeat") })
        assertFalse(tracker.onEvent(99, 1, 200L, false, 0) { error("up") })
        // A lost release must not pin the decision into the next press.
        assertFalse(tracker.onEvent(24, 1, 300L, true, 0) { false })
        assertTrue(tracker.onEvent(24, 1, 400L, true, 0) { true })
    }

    @Test fun lateReleaseCannotEraseANewerPressDecision() {
        val tracker = KeyConsumptionTracker()
        assertFalse(tracker.onEvent(25, 1, 100L, true, 0) { false })
        assertTrue(tracker.onEvent(25, 1, 200L, true, 0) { true })
        assertFalse(tracker.onEvent(25, 1, 100L, false, 0) { error("old up") })
        assertTrue(tracker.onEvent(25, 1, 200L, true, 1) { error("repeat") })
        assertTrue(tracker.onEvent(25, 1, 200L, false, 0) { error("current up") })
    }
}
