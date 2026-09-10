package com.example.data

import com.squareup.moshi.JsonClass

/** An executable mapper script. New action implementations can be added without changing triggers. */
data class ShortcutScript(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val triggers: List<ScriptTrigger>,
    val actions: List<ScriptAction>,
    val constraints: List<ScriptConstraint> = emptyList(),
    /**
     * A key-less script: instead of a key press it fires once each time every one of its
     * [constraints] turns from false to true. See `HardwareKeyTriggerCoordinator.reevaluateAutomations`.
     */
    val automation: Boolean = false,
    val consumeOriginalEvent: Boolean? = null,
    /** Null defers to the global [KeyMappingConfig] default; see [resolveVibrateForScript]. */
    val vibrateOnTrigger: Boolean? = null,
    val hapticDurationMs: Long? = null,
)

data class ScriptTrigger(
    /** A list intentionally models future simultaneous/sequential key combinations. */
    val keyCodes: List<Int>,
    val pressType: TriggerPressType,
    /** Null defers to the global [KeyMappingConfig] default; see [resolveTimingForKey]. */
    val doublePressWindowMs: Long? = null,
    val longPressThresholdMs: Long? = null,
)

/** [type] keys into [ScriptActionCatalog]; [params] are the action's typed parameters as strings. */
data class ScriptAction(val type: String, val params: Map<String, String> = emptyMap()) {
    companion object {
        val ToggleFlashlight = ScriptAction("toggle_flashlight")
    }
}

/** [type] keys into [ScriptConstraintCatalog]; a script only fires while every constraint is satisfied. */
data class ScriptConstraint(val type: String, val params: Map<String, String> = emptyMap())

@JsonClass(generateAdapter = true)
internal data class PersistedScriptStore(
    val version: Int = 2,
    val globallyEnabled: Boolean = true,
    val scripts: List<PersistedShortcutScript> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class PersistedShortcutScript(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val triggers: List<PersistedScriptTrigger>,
    val actions: List<PersistedScriptAction>,
    val constraints: List<PersistedScriptConstraint> = emptyList(),
    /** Read-only since the automation flag replaced it; see [migrateEventTrigger]. Never written. */
    val eventTriggers: List<PersistedScriptEventTrigger> = emptyList(),
    val automation: Boolean = false,
    val consumeOriginalEvent: Boolean? = null,
    val vibrateOnTrigger: Boolean? = null,
    val hapticDurationMs: Long? = null,
)

@JsonClass(generateAdapter = true)
internal data class PersistedScriptTrigger(
    val keyCodes: List<Int>,
    val pressType: String,
    val doublePressWindowMs: Long? = null,
    val longPressThresholdMs: Long? = null,
)

@JsonClass(generateAdapter = true)
internal data class PersistedScriptAction(val type: String, val params: Map<String, String> = emptyMap())

@JsonClass(generateAdapter = true)
internal data class PersistedScriptConstraint(val type: String, val params: Map<String, String> = emptyMap())

@JsonClass(generateAdapter = true)
internal data class PersistedScriptEventTrigger(val type: String, val params: Map<String, String> = emptyMap())

internal fun ShortcutScript.toPersisted() = PersistedShortcutScript(
    id, name, enabled,
    triggers.map { PersistedScriptTrigger(it.keyCodes, it.pressType.name, it.doublePressWindowMs, it.longPressThresholdMs) },
    actions.map { PersistedScriptAction(it.type, it.params) },
    constraints.map { PersistedScriptConstraint(it.type, it.params) },
    automation = automation,
    consumeOriginalEvent = consumeOriginalEvent,
    vibrateOnTrigger = vibrateOnTrigger,
    hapticDurationMs = hapticDurationMs,
)

/** Dropped from the catalog because it ran the identical `input keyevent` the bridge runs for
 * "input_key_code"; aliased rather than deleted so saved scripts survive. */
private const val LEGACY_INPUT_KEY_EVENT = "input_key_event"

/**
 * Rewrites an action persisted by an older build into its current shape. Returns the action
 * unchanged when nothing applies, so this stays cheap on every load.
 */
internal fun migrateAction(type: String, params: Map<String, String>): ScriptAction {
    val migratedType = if (type == LEGACY_INPUT_KEY_EVENT) "input_key_code" else type
    if (migratedType != "pinch_screen" || params.containsKey("x1")) return ScriptAction(migratedType, params)
    // Pre-two-point pinch: a center plus a span along an implied horizontal axis.
    val x = params["x"]?.toFloatOrNull()
    val y = params["y"]?.toFloatOrNull()
    val distance = params["distance"]?.toFloatOrNull()
    if (x == null || y == null || distance == null) return ScriptAction(migratedType, params)
    val half = distance / 2f
    val points = mapOf(
        "x1" to (x - half).toInt().toString(),
        "y1" to y.toInt().toString(),
        "x2" to (x + half).toInt().toString(),
        "y2" to y.toInt().toString(),
    )
    return ScriptAction(migratedType, params - setOf("x", "y", "distance") + points)
}

internal fun PersistedShortcutScript.toDomain(): ShortcutScript? {
    val validTriggers = triggers.mapNotNull { trigger ->
        val type = runCatching { TriggerPressType.valueOf(trigger.pressType) }.getOrNull()
        if (type == null || trigger.keyCodes.isEmpty()) null
        else ScriptTrigger(trigger.keyCodes.distinct(), type, trigger.doublePressWindowMs, trigger.longPressThresholdMs)
    }
    val validActions = actions.mapNotNull { action ->
        val migrated = migrateAction(action.type, action.params)
        if (ScriptActionCatalog.descriptor(migrated.type) == null) null else migrated
    }
    val validConstraints = constraints.mapNotNull { constraint ->
        if (ScriptConstraintCatalog.descriptor(constraint.type)?.availability != ConstraintAvailability.AVAILABLE) null
        else ScriptConstraint(constraint.type, constraint.params)
    }
    val migratedEventTriggers = eventTriggers.mapNotNull { migrateEventTrigger(it.type, it.params) }
    return if (id.isBlank() || validActions.isEmpty()) null
    else ShortcutScript(
        id, name.ifBlank { "Unnamed script" }, enabled, validTriggers, validActions,
        validConstraints + migratedEventTriggers,
        automation || eventTriggers.isNotEmpty(),
        consumeOriginalEvent, vibrateOnTrigger, hapticDurationMs,
    )
}

/**
 * The v1 automation triggers were four bespoke events; each becomes the matching constraint on a
 * script flagged [ShortcutScript.automation]. A Bluetooth trigger without a device ("any device")
 * has no constraint equivalent and is dropped, which the editor surfaces as "needs a condition".
 */
private fun migrateEventTrigger(type: String, params: Map<String, String>): ScriptConstraint? {
    val address = params["deviceAddress"]
    return when (type) {
        "charger_connected" -> ScriptConstraint("charging")
        "charger_disconnected" -> ScriptConstraint("discharging")
        "bluetooth_connected", "bluetooth_disconnected" ->
            if (address.isNullOrBlank()) null
            else ScriptConstraint("bluetooth_device_${type.removePrefix("bluetooth_")}", mapOf("address" to address))
        else -> null
    }
}
