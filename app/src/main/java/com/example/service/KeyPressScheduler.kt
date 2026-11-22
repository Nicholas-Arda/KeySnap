package com.example.service

import com.example.bridge.PressTimingMachine
import com.example.data.KeyMappingConfig
import com.example.data.TriggerPressType
import com.example.data.TriggerTimingSpecs
import com.example.data.normalizeTimingValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class KeyPressScheduler(
    private val scope: CoroutineScope,
    private val onTriggered: (Int, TriggerPressType) -> Unit,
) {
    private data class TimingSnapshot(
        val doublePressWindowMs: Long = TriggerTimingSpecs.doublePressWindow.default,
        val longPressThresholdMs: Long = TriggerTimingSpecs.longPressThreshold.default,
        val singleAt: Int = PressTimingMachine.SINGLE_AFTER_WINDOW,
    )

    private data class KeyState(
        var downAt: Long? = null,
        var lastUpAt: Long = Long.MIN_VALUE,
        var clickCount: Int = 0,
        var sequence: Long = 0,
        var timing: TimingSnapshot = TimingSnapshot(),
        var longPressTriggered: Boolean = false,
        var doublePressTriggered: Boolean = false,
        var longPressJob: Job? = null,
        var singlePressJob: Job? = null,
    )

    private val lock = Any()
    private val states = mutableMapOf<Int, KeyState>()

    /** [singleAt]: `PressTimingMachine.singlePressAt` for this key. */
    fun onEvent(
        keyCode: Int,
        isDown: Boolean,
        eventTime: Long,
        config: KeyMappingConfig,
        singleAt: Int = PressTimingMachine.SINGLE_AFTER_WINDOW,
    ) {
        if (isDown) {
            onDown(keyCode, eventTime, config, singleAt)
        } else {
            onUp(keyCode, eventTime)
        }
    }

    fun reset() {
        synchronized(lock) {
            states.values.forEach { state ->
                state.longPressJob?.cancel()
                state.singlePressJob?.cancel()
            }
            states.clear()
        }
    }

    /** From a press's down until its up, [cancel] (it joined a chord) or [reset]; stays true after its long press fires. */
    fun isHeld(keyCode: Int): Boolean = synchronized(lock) { states[keyCode]?.downAt != null }

    /** Drops one key's pending press, e.g. when it turns out to be part of a chord. Mirrors PressTimingMachine.cancel. */
    fun cancel(keyCode: Int) {
        synchronized(lock) {
            states[keyCode]?.let { clearStateLocked(keyCode, it) }
        }
    }

    private fun onDown(keyCode: Int, eventTime: Long, config: KeyMappingConfig, singleAt: Int) {
        var singles = 0
        var triggerDouble = false
        synchronized(lock) {
            val state = states.getOrPut(keyCode) { KeyState() }
            if (state.downAt != null) return

            if (state.clickCount == 1) {
                val elapsed = eventTime - state.lastUpAt
                if (elapsed in 0..state.timing.doublePressWindowMs) {
                    state.singlePressJob?.cancel()
                    state.singlePressJob = null
                    state.clickCount = 0
                    state.sequence++
                    state.downAt = eventTime
                    state.doublePressTriggered = true
                    state.longPressTriggered = false
                    state.longPressJob?.cancel()
                    state.longPressJob = null
                    triggerDouble = true
                } else {
                    state.singlePressJob?.cancel()
                    state.singlePressJob = null
                    state.clickCount = 0
                    state.sequence++
                    singles++
                    beginPressLocked(keyCode, state, eventTime, config, singleAt)
                }
            } else {
                beginPressLocked(keyCode, state, eventTime, config, singleAt)
            }
            if (state.downAt != null && state.timing.singleAt == PressTimingMachine.SINGLE_ON_PRESS) singles++
        }
        repeat(singles) { onTriggered(keyCode, TriggerPressType.SINGLE_PRESS) }
        if (triggerDouble) onTriggered(keyCode, TriggerPressType.DOUBLE_PRESS)
    }

    private fun onUp(keyCode: Int, eventTime: Long) {
        var triggerLong = false
        var triggerSingle = false
        synchronized(lock) {
            val state = states[keyCode] ?: return
            val downAt = state.downAt ?: return
            state.downAt = null
            state.longPressJob?.cancel()
            state.longPressJob = null

            // SINGLE_ON_PRESS fired on the down.
            if (state.longPressTriggered || state.doublePressTriggered || state.timing.singleAt == PressTimingMachine.SINGLE_ON_PRESS) {
                clearStateLocked(keyCode, state)
                return
            }
            if (eventTime - downAt >= state.timing.longPressThresholdMs) {
                state.longPressTriggered = true
                triggerLong = true
                clearStateLocked(keyCode, state)
            } else if (state.timing.singleAt == PressTimingMachine.SINGLE_ON_RELEASE) {
                triggerSingle = true
                clearStateLocked(keyCode, state)
            } else {
                state.clickCount = 1
                state.lastUpAt = eventTime
                scheduleSinglePressLocked(keyCode, state)
            }
        }
        if (triggerLong) onTriggered(keyCode, TriggerPressType.LONG_PRESS)
        if (triggerSingle) onTriggered(keyCode, TriggerPressType.SINGLE_PRESS)
    }

    private fun beginPressLocked(keyCode: Int, state: KeyState, eventTime: Long, config: KeyMappingConfig, singleAt: Int) {
        state.timing = TimingSnapshot(
            doublePressWindowMs = normalizeTimingValue(config.doublePressWindowMs, TriggerTimingSpecs.doublePressWindow),
            longPressThresholdMs = normalizeTimingValue(config.longPressThresholdMs, TriggerTimingSpecs.longPressThreshold),
            singleAt = singleAt,
        )
        state.downAt = eventTime
        state.clickCount = 0
        state.longPressTriggered = false
        state.doublePressTriggered = false
        state.sequence++
        val sequence = state.sequence
        val threshold = state.timing.longPressThresholdMs
        state.longPressJob?.cancel()
        state.longPressJob = null
        if (singleAt == PressTimingMachine.SINGLE_ON_PRESS) return // No long press on this key, and its single fires now.
        state.longPressJob = scope.launch {
            delay(threshold)
            var shouldTrigger = false
            synchronized(lock) {
                val current = states[keyCode]
                if (current === state && current.sequence == sequence && current.downAt != null && !current.doublePressTriggered) {
                    current.longPressTriggered = true
                    shouldTrigger = true
                }
            }
            if (shouldTrigger) onTriggered(keyCode, TriggerPressType.LONG_PRESS)
        }
    }

    private fun scheduleSinglePressLocked(keyCode: Int, state: KeyState) {
        state.singlePressJob?.cancel()
        val sequence = state.sequence
        val window = state.timing.doublePressWindowMs
        state.singlePressJob = scope.launch {
            delay(window)
            var shouldTrigger = false
            synchronized(lock) {
                val current = states[keyCode]
                if (current === state && current.sequence == sequence && current.clickCount == 1 && current.downAt == null) {
                    current.clickCount = 0
                    current.singlePressJob = null
                    shouldTrigger = true
                    clearStateLocked(keyCode, current)
                }
            }
            if (shouldTrigger) onTriggered(keyCode, TriggerPressType.SINGLE_PRESS)
        }
    }

    private fun clearStateLocked(keyCode: Int, state: KeyState) {
        state.longPressJob?.cancel()
        state.singlePressJob?.cancel()
        states.remove(keyCode, state)
    }
}
