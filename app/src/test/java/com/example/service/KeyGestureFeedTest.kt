package com.example.service

import com.example.bridge.ChordTracker
import com.example.data.KeyMappingConfig
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KeyGestureFeedTest {
    private val chordId = ChordTracker.chordId(listOf(24, 25))

    // Same constructor shape as ScriptTriggerMatcherTest.script(...).
    private fun script(id: String, vararg triggers: ScriptTrigger) =
        ShortcutScript(id, id, true, triggers.toList(), listOf(ScriptAction.ToggleFlashlight))

    private val scripts = listOf(
        script("chord", ScriptTrigger(listOf(24, 25), TriggerPressType.SINGLE_PRESS)),
        script("volUp", ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)),
    )

    @Test
    fun chordSinglePressFiresOnceAndSuppressesMembers() = runTest {
        val fired = mutableListOf<Pair<Int, TriggerPressType>>()
        val feed = KeyGestureFeed(this) { id, type -> fired += id to type }

        feed.onEvent(24, true, 0, scripts, KeyMappingConfig())
        feed.onEvent(25, true, 80, scripts, KeyMappingConfig())
        feed.onEvent(24, false, 150, scripts, KeyMappingConfig())
        feed.onEvent(25, false, 400, scripts, KeyMappingConfig())
        advanceUntilIdle()

        assertEquals(listOf(chordId to TriggerPressType.SINGLE_PRESS), fired)
    }

    @Test
    fun chordLongPressFires() = runTest {
        val fired = mutableListOf<Pair<Int, TriggerPressType>>()
        val feed = KeyGestureFeed(this) { id, type -> fired += id to type }

        feed.onEvent(24, true, 0, scripts, KeyMappingConfig())
        feed.onEvent(25, true, 50, scripts, KeyMappingConfig())
        advanceUntilIdle()

        assertEquals(listOf(chordId to TriggerPressType.LONG_PRESS), fired)
    }

    @Test
    fun chordDoublePressFires() = runTest {
        val fired = mutableListOf<Pair<Int, TriggerPressType>>()
        val feed = KeyGestureFeed(this) { id, type -> fired += id to type }

        feed.onEvent(24, true, 0, scripts, KeyMappingConfig())
        feed.onEvent(25, true, 20, scripts, KeyMappingConfig())
        feed.onEvent(24, false, 100, scripts, KeyMappingConfig())
        feed.onEvent(25, false, 110, scripts, KeyMappingConfig())
        feed.onEvent(25, true, 200, scripts, KeyMappingConfig())
        feed.onEvent(24, true, 220, scripts, KeyMappingConfig())
        feed.onEvent(24, false, 260, scripts, KeyMappingConfig())
        feed.onEvent(25, false, 270, scripts, KeyMappingConfig())
        advanceUntilIdle()

        assertEquals(listOf(chordId to TriggerPressType.DOUBLE_PRESS), fired)
    }

    @Test
    fun memberPressedAloneFiresItsOwnScript() = runTest {
        val fired = mutableListOf<Pair<Int, TriggerPressType>>()
        val feed = KeyGestureFeed(this) { id, type -> fired += id to type }

        feed.onEvent(24, true, 0, scripts, KeyMappingConfig())
        feed.onEvent(24, false, 60, scripts, KeyMappingConfig())
        advanceUntilIdle()

        assertEquals(listOf(24 to TriggerPressType.SINGLE_PRESS), fired)
    }

    @Test
    fun duplicateDeliveriesFireTheChordOnce() = runTest {
        val fired = mutableListOf<Pair<Int, TriggerPressType>>()
        val feed = KeyGestureFeed(this) { id, type -> fired += id to type }

        // Accessibility and Activity both report each event, interleaved.
        for ((key, down, t) in listOf(
            Triple(24, true, 0L), Triple(25, true, 30L), Triple(24, true, 35L), Triple(25, true, 40L),
            Triple(24, false, 120L), Triple(25, false, 125L), Triple(24, false, 130L), Triple(25, false, 135L),
        )) feed.onEvent(key, down, t, scripts, KeyMappingConfig())
        advanceUntilIdle()

        assertEquals(listOf(chordId to TriggerPressType.SINGLE_PRESS), fired)
    }
}
