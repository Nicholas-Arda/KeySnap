package com.example.service

import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptTriggerMatcherTest {
    private fun script(id: String, type: TriggerPressType, enabled: Boolean = true, keys: List<Int> = listOf(24)) =
        ShortcutScript(id, id, enabled, listOf(ScriptTrigger(keys, type)), listOf(ScriptAction.ToggleFlashlight))

    @Test fun singlePressMatchesOnRelease() {
        val matcher = ScriptTriggerMatcher()
        val target = script("single", TriggerPressType.SINGLE_PRESS)
        assertTrue(matcher.onEvent(listOf(target), 24, true, 100).isEmpty())
        assertEquals(listOf(target), matcher.onEvent(listOf(target), 24, false, 200))
    }

    @Test fun doublePressMatchesSecondDownWithinWindow() {
        val matcher = ScriptTriggerMatcher()
        val target = script("double", TriggerPressType.DOUBLE_PRESS)
        matcher.onEvent(listOf(target), 24, true, 100)
        matcher.onEvent(listOf(target), 24, false, 150)
        assertEquals(listOf(target), matcher.onEvent(listOf(target), 24, true, 400))
    }

    @Test fun longPressMatchesOnReleaseAfterThreshold() {
        val matcher = ScriptTriggerMatcher()
        val target = script("long", TriggerPressType.LONG_PRESS)
        matcher.onEvent(listOf(target), 24, true, 100)
        assertEquals(listOf(target), matcher.onEvent(listOf(target), 24, false, 600))
    }

    @Test fun customTimingValuesChangeClassificationBoundaries() {
        val matcher = ScriptTriggerMatcher()
        val target = script("long", TriggerPressType.LONG_PRESS)
        val config = com.example.data.KeyMappingConfig(
            doublePressWindowMs = 500,
            longPressThresholdMs = 700,
        )
        matcher.onEvent(listOf(target), 24, true, 100, config)
        assertTrue(matcher.onEvent(listOf(target), 24, false, 799, config).isEmpty())
        matcher.reset()
        matcher.onEvent(listOf(target), 24, true, 100, config)
        assertEquals(listOf(target), matcher.onEvent(listOf(target), 24, false, 800, config))
    }

    @Test fun pausedConflictingAndCombinationScriptsAreIgnoredSafely() {
        val matcher = ScriptTriggerMatcher()
        val paused = script("paused", TriggerPressType.SINGLE_PRESS, enabled = false)
        val wrongKey = script("wrong", TriggerPressType.SINGLE_PRESS, keys = listOf(25))
        val futureCombination = script("combo", TriggerPressType.SINGLE_PRESS, keys = listOf(24, 25))
        matcher.onEvent(listOf(paused, wrongKey, futureCombination), 24, true, 0)
        assertTrue(matcher.onEvent(listOf(paused, wrongKey, futureCombination), 24, false, 20).isEmpty())
    }


    @Test fun conflictsDeterministicallyReturnEveryEnabledScriptInRepositoryOrder() {
        val matcher = ScriptTriggerMatcher()
        val first = script("first", TriggerPressType.SINGLE_PRESS)
        val second = script("second", TriggerPressType.SINGLE_PRESS)
        matcher.onEvent(listOf(first, second), 24, true, 0)
        assertEquals(listOf(first, second), matcher.onEvent(listOf(first, second), 24, false, 20))
    }
}
