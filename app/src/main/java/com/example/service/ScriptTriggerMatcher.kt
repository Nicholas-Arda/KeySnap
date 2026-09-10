package com.example.service

import com.example.data.KeyMappingConfig
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.data.TriggerTimingSpecs
import com.example.data.normalizeTimingValue
import com.example.data.resolveTimingForKey

/** Pure state machine: Android-free and deterministic under an injected event clock. */
class ScriptTriggerMatcher(
    private val defaultConfig: KeyMappingConfig = KeyMappingConfig(),
) {
    private data class KeyState(
        var downAt: Long = 0,
        var lastUpAt: Long = Long.MIN_VALUE,
        var clickCount: Int = 0,
        var doublePressWindowMs: Long = TriggerTimingSpecs.doublePressWindow.default,
        var longPressThresholdMs: Long = TriggerTimingSpecs.longPressThreshold.default,
    )

    private val states = mutableMapOf<Int, KeyState>()

    fun onEvent(
        scripts: List<ShortcutScript>,
        keyCode: Int,
        isDown: Boolean,
        eventTime: Long,
        config: KeyMappingConfig = defaultConfig,
    ): List<ShortcutScript> {
        val effectiveConfig = resolveTimingForKey(scripts, keyCode, config)
        val state = states.getOrPut(keyCode) { KeyState() }
        val detected = mutableSetOf<TriggerPressType>()
        if (isDown) {
            if (state.clickCount == 1 && eventTime - state.lastUpAt <= state.doublePressWindowMs) {
                detected += TriggerPressType.DOUBLE_PRESS
                state.clickCount = 0
            } else {
                state.doublePressWindowMs = normalizeTimingValue(effectiveConfig.doublePressWindowMs, TriggerTimingSpecs.doublePressWindow)
                state.longPressThresholdMs = normalizeTimingValue(effectiveConfig.longPressThresholdMs, TriggerTimingSpecs.longPressThreshold)
                state.downAt = eventTime
            }
        } else {
            val held = eventTime - state.downAt
            if (held >= state.longPressThresholdMs) {
                detected += TriggerPressType.LONG_PRESS
                state.clickCount = 0
            } else {
                detected += TriggerPressType.SINGLE_PRESS
                state.clickCount = 1
                state.lastUpAt = eventTime
            }
        }
        return scripts.filter { script ->
            script.enabled && script.triggers.any { it.isSupportedSingleKey(keyCode) && it.pressType in detected }
        }
    }

    fun reset() = states.clear()

    private fun ScriptTrigger.isSupportedSingleKey(keyCode: Int) = keyCodes.size == 1 && keyCodes.single() == keyCode
}
