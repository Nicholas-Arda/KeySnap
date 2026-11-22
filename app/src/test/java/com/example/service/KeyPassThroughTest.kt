package com.example.service

import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.example.bridge.ChordTracker
import com.example.data.KeyMappingConfig
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KeyPassThroughTest {
    private val up = KeyEvent.KEYCODE_VOLUME_UP
    private val timing = KeyMappingConfig() // 300 ms window, 500 ms long press

    @Test fun onlyOurInjectedVolumeKeysPassThrough() {
        assertTrue(KeyPassThrough.isInjected(up, KeyCharacterMap.VIRTUAL_KEYBOARD))
        assertTrue(KeyPassThrough.isInjected(KeyEvent.KEYCODE_VOLUME_DOWN, KeyCharacterMap.VIRTUAL_KEYBOARD))
        // A hardware key has a real device id; other keys keep today's handling either way.
        assertFalse(KeyPassThrough.isInjected(up, 3))
        assertFalse(KeyPassThrough.isInjected(up, 0))
        assertFalse(KeyPassThrough.isInjected(KeyEvent.KEYCODE_CAMERA, KeyCharacterMap.VIRTUAL_KEYBOARD))
    }

    @Test fun givesBackOnlyASwallowedPressThatRanNothing() {
        assertTrue(KeyPassThrough.shouldGiveBack(up, nothingRan = true, swallowedAt = 1_000, now = 1_800, timing))
        assertFalse("a shortcut ran", KeyPassThrough.shouldGiveBack(up, nothingRan = false, swallowedAt = 1_000, now = 1_800, timing))
        assertFalse("never swallowed: the key already did its job", KeyPassThrough.shouldGiveBack(up, true, swallowedAt = null, now = 1_800, timing))
        assertFalse("other keys stay as they are", KeyPassThrough.shouldGiveBack(KeyEvent.KEYCODE_CAMERA, true, 1_000, 1_800, timing))
        // Long press + window + slack = 1300 ms: an older swallow belongs to another press.
        assertTrue(KeyPassThrough.shouldGiveBack(up, true, swallowedAt = 1_000, now = 2_300, timing))
        assertFalse(KeyPassThrough.shouldGiveBack(up, true, swallowedAt = 1_000, now = 2_301, timing))
        assertFalse(KeyPassThrough.shouldGiveBack(up, true, swallowedAt = 2_000, now = 1_000, timing))
    }

    @Test fun singleOnceDoubleTwice() = runTest {
        var steps = 0
        KeyPassThrough.perform(TriggerPressType.SINGLE_PRESS, { true }) { steps++ }
        assertEquals(1, steps)
        KeyPassThrough.perform(TriggerPressType.DOUBLE_PRESS, { true }) { steps++ }
        assertEquals(3, steps)
    }

    @Test fun longPressRepeatsEvery50msWhileHeldAndStopsOnRelease() = runTest {
        val stepTimes = mutableListOf<Long>()
        KeyPassThrough.perform(TriggerPressType.LONG_PRESS, { currentTime < 175 }) { stepTimes += currentTime }
        assertEquals(listOf(0L, 50L, 100L, 150L), stepTimes)
    }

    @Test fun longPressAlreadyReleasedStepsOnce() = runTest {
        var steps = 0
        KeyPassThrough.perform(TriggerPressType.LONG_PRESS, { false }) { steps++ }
        assertEquals(1, steps)
    }

    @Test fun lostReleaseStillEnds() = runTest {
        var steps = 0
        KeyPassThrough.perform(TriggerPressType.LONG_PRESS, { true }) { steps++ }
        assertEquals(1 + (KeyPassThrough.MAX_HOLD_MS / KeyPassThrough.REPEAT_DELAY_MS).toInt(), steps)
    }

    @Test fun heldUntilReleaseOrUntilAChordTakesTheKey() = runTest {
        // Volume up only as a chord member (a modifier, so its own long press can fire first).
        val scripts = listOf(
            ShortcutScript("chord", "chord", true, listOf(ScriptTrigger(listOf(up, 26), TriggerPressType.SINGLE_PRESS)), listOf(ScriptAction.ToggleFlashlight)),
        )
        val fired = mutableListOf<Pair<Int, TriggerPressType>>()
        val feed = KeyGestureFeed(this) { id, type -> fired += id to type }

        feed.onEvent(up, true, 0, scripts, timing)
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf(up to TriggerPressType.LONG_PRESS), fired)
        assertTrue("still held after its long press fired", feed.isHeld(up))

        feed.onEvent(26, true, 700, scripts, timing)
        assertFalse("the chord took the key: its own job must stop", feed.isHeld(up))

        feed.onEvent(up, false, 800, scripts, timing)
        feed.onEvent(26, false, 810, scripts, timing)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(ChordTracker.chordId(listOf(up, 26)) to TriggerPressType.SINGLE_PRESS, fired.last())
        feed.onEvent(up, true, 2_000, scripts, timing)
        assertTrue(feed.isHeld(up))
        feed.onEvent(up, false, 2_100, scripts, timing)
        assertFalse("released", feed.isHeld(up))
    }
}
