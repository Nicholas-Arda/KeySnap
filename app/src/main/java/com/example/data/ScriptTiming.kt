package com.example.data

import com.example.bridge.ChordTracker

/** The key this trigger listens to: the key code, or the shared chord id for several keys. */
fun ScriptTrigger.keyId(): Int = ChordTracker.triggerKeyId(keyCodes)

/**
 * Resolves the effective press timing for one key, given every script that targets it.
 *
 * A script's trigger may carry its own `doublePressWindowMs` / `longPressThresholdMs`; `null`
 * defers to [defaults]. When several enabled scripts target the same key, the first explicit
 * override on a trigger of the matching press type wins, then the first explicit override on any
 * trigger for that key, then the global default. `BridgeConfig` (Java) mirrors this rule exactly,
 * because a script authored here is later matched by the detached bridge and the two must never
 * resolve different timings for the same key. [keyId] may be a chord id (see `ChordTracker`);
 * `BridgeConfig` resolves it the same way.
 */
fun resolveTimingForKey(scripts: List<ShortcutScript>, keyId: Int, defaults: KeyMappingConfig): KeyMappingConfig {
    val triggersForKey = scripts.asSequence()
        .filter { it.enabled }
        .flatMap { it.triggers.asSequence() }
        .filter { it.keyId() == keyId }
        .toList()

    val doubleWindow = triggersForKey.firstOrNull { it.pressType == TriggerPressType.DOUBLE_PRESS && it.doublePressWindowMs != null }?.doublePressWindowMs
        ?: triggersForKey.firstOrNull { it.doublePressWindowMs != null }?.doublePressWindowMs
        ?: defaults.doublePressWindowMs

    val longThreshold = triggersForKey.firstOrNull { it.pressType == TriggerPressType.LONG_PRESS && it.longPressThresholdMs != null }?.longPressThresholdMs
        ?: triggersForKey.firstOrNull { it.longPressThresholdMs != null }?.longPressThresholdMs
        ?: defaults.longPressThresholdMs

    return defaults.copy(
        doublePressWindowMs = normalizeTimingValue(doubleWindow, TriggerTimingSpecs.doublePressWindow),
        longPressThresholdMs = normalizeTimingValue(longThreshold, TriggerTimingSpecs.longPressThreshold),
    )
}

fun resolveConsumeForScript(script: ShortcutScript, globalDefault: Boolean): Boolean =
    script.consumeOriginalEvent ?: globalDefault

/**
 * Whether a key's own job is swallowed: some enabled script uses it (alone or as a chord member)
 * and resolves to consume. Accessibility and Activity both decide through this.
 */
fun shouldConsumeKey(scripts: List<ShortcutScript>, keyCode: Int, globallyEnabled: Boolean, globalDefault: Boolean): Boolean =
    globallyEnabled && scripts.any { script ->
        script.enabled && script.triggers.any { keyCode in it.keyCodes } && resolveConsumeForScript(script, globalDefault)
    }

fun resolveVibrateForScript(script: ShortcutScript, globalDefault: Boolean): Boolean =
    script.vibrateOnTrigger ?: globalDefault

fun resolveHapticDurationForScript(script: ShortcutScript, globalDefault: Long): Long =
    normalizeTimingValue(script.hapticDurationMs ?: globalDefault, TriggerTimingSpecs.hapticDuration)
