package com.example.service

import com.example.data.KeyMappingConfig
import com.example.data.TriggerPressType
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KeyPressSchedulerTest {
    @Test
    fun singlePressWaitsForConfiguredDoublePressWindow() = runTest {
        val triggered = mutableListOf<Pair<Int, TriggerPressType>>()
        val scheduler = KeyPressScheduler(this) { keyCode, type -> triggered += keyCode to type }

        scheduler.onEvent(24, true, 0, KeyMappingConfig())
        scheduler.onEvent(24, false, 10, KeyMappingConfig())
        advanceTimeBy(299)
        assertTrue(triggered.isEmpty())
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf(24 to TriggerPressType.SINGLE_PRESS), triggered)
    }

    @Test
    fun longPressUsesConfiguredThresholdAndFiresOnce() = runTest {
        val triggered = mutableListOf<Pair<Int, TriggerPressType>>()
        val scheduler = KeyPressScheduler(this) { keyCode, type -> triggered += keyCode to type }

        scheduler.onEvent(24, true, 0, KeyMappingConfig())
        advanceTimeBy(499)
        assertTrue(triggered.isEmpty())
        advanceTimeBy(1)
        advanceUntilIdle()
        scheduler.onEvent(24, false, 600, KeyMappingConfig())

        assertEquals(listOf(24 to TriggerPressType.LONG_PRESS), triggered)
    }

    @Test
    fun secondPressWithinWindowCancelsSinglePress() = runTest {
        val triggered = mutableListOf<Pair<Int, TriggerPressType>>()
        val scheduler = KeyPressScheduler(this) { keyCode, type -> triggered += keyCode to type }
        val config = KeyMappingConfig(doublePressWindowMs = 500)

        scheduler.onEvent(24, true, 0, config)
        scheduler.onEvent(24, false, 10, config)
        scheduler.onEvent(24, true, 400, config)
        scheduler.onEvent(24, false, 410, config)
        advanceUntilIdle()

        assertEquals(listOf(24 to TriggerPressType.DOUBLE_PRESS), triggered)
    }

    @Test
    fun differentKeysHaveIndependentTimingState() = runTest {
        val triggered = mutableListOf<Pair<Int, TriggerPressType>>()
        val scheduler = KeyPressScheduler(this) { keyCode, type -> triggered += keyCode to type }

        scheduler.onEvent(24, true, 0, KeyMappingConfig())
        scheduler.onEvent(25, true, 0, KeyMappingConfig())
        scheduler.onEvent(24, false, 10, KeyMappingConfig())
        scheduler.onEvent(25, false, 20, KeyMappingConfig())
        advanceUntilIdle()

        assertEquals(
            listOf(24 to TriggerPressType.SINGLE_PRESS, 25 to TriggerPressType.SINGLE_PRESS),
            triggered,
        )
    }

    @Test
    fun repeatedDownDoesNotStartAnotherLongPress() = runTest {
        val triggered = mutableListOf<Pair<Int, TriggerPressType>>()
        val scheduler = KeyPressScheduler(this) { keyCode, type -> triggered += keyCode to type }

        scheduler.onEvent(24, true, 0, KeyMappingConfig())
        scheduler.onEvent(24, true, 50, KeyMappingConfig())
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals(listOf(24 to TriggerPressType.LONG_PRESS), triggered)
    }
}
