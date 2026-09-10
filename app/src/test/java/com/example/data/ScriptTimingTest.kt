package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the timing-resolution rule. `BridgeConfig` (Java) mirrors this exactly with an identical
 * set of cases in `BridgeConfigTimingTest`, since a script authored here is later matched by the
 * detached bridge and the two must never resolve different timings for the same key.
 */
class ScriptTimingTest {
    private val defaults = KeyMappingConfig(doublePressWindowMs = 300L, longPressThresholdMs = 500L)

    private fun script(
        id: String,
        keyCode: Int,
        pressType: TriggerPressType,
        enabled: Boolean = true,
        doubleOverride: Long? = null,
        longOverride: Long? = null,
    ) = ShortcutScript(
        id, id, enabled,
        listOf(ScriptTrigger(listOf(keyCode), pressType, doubleOverride, longOverride)),
        listOf(ScriptAction.ToggleFlashlight),
    )

    @Test fun noTriggersForKeyFallsBackToDefaults() {
        val resolved = resolveTimingForKey(emptyList(), 24, defaults)
        assertEquals(300L, resolved.doublePressWindowMs)
        assertEquals(500L, resolved.longPressThresholdMs)
    }

    @Test fun overrideOnMatchingPressTypeWins() {
        val scripts = listOf(script("a", 24, TriggerPressType.DOUBLE_PRESS, doubleOverride = 450L))
        val resolved = resolveTimingForKey(scripts, 24, defaults)
        assertEquals(450L, resolved.doublePressWindowMs)
        assertEquals(500L, resolved.longPressThresholdMs)
    }

    @Test fun overrideOnNonMatchingTypeStillAppliesAsFallback() {
        // A SINGLE_PRESS trigger's doublePressWindowMs override still governs a DOUBLE_PRESS
        // gesture on the same key, since no DOUBLE_PRESS-typed override exists for it.
        val scripts = listOf(script("a", 24, TriggerPressType.SINGLE_PRESS, doubleOverride = 600L))
        val resolved = resolveTimingForKey(scripts, 24, defaults)
        assertEquals(600L, resolved.doublePressWindowMs)
    }

    @Test fun firstScriptInOrderWins() {
        val scripts = listOf(
            script("first", 24, TriggerPressType.DOUBLE_PRESS, doubleOverride = 400L),
            script("second", 24, TriggerPressType.DOUBLE_PRESS, doubleOverride = 700L),
        )
        val resolved = resolveTimingForKey(scripts, 24, defaults)
        assertEquals(400L, resolved.doublePressWindowMs)
    }

    @Test fun disabledScriptOverrideIsIgnored() {
        val scripts = listOf(
            script("disabled", 24, TriggerPressType.DOUBLE_PRESS, enabled = false, doubleOverride = 700L),
            script("active", 24, TriggerPressType.DOUBLE_PRESS, doubleOverride = 450L),
        )
        val resolved = resolveTimingForKey(scripts, 24, defaults)
        assertEquals(450L, resolved.doublePressWindowMs)
    }

    @Test fun resolvedValueIsNormalizedToTheNearestStep() {
        // 733 is within [150,750] but not a multiple of the 50ms step from 150; must snap to 750.
        val scripts = listOf(script("a", 24, TriggerPressType.DOUBLE_PRESS, doubleOverride = 733L))
        val resolved = resolveTimingForKey(scripts, 24, defaults)
        assertEquals(750L, resolved.doublePressWindowMs)
    }

    @Test fun differentKeyIsUnaffected() {
        val scripts = listOf(script("a", 24, TriggerPressType.DOUBLE_PRESS, doubleOverride = 450L))
        val resolved = resolveTimingForKey(scripts, 25, defaults)
        assertEquals(300L, resolved.doublePressWindowMs)
    }
}
