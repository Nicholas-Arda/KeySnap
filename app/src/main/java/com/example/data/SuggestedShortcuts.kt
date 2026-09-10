package com.example.data

import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R

/**
 * A ready-made shortcut offered on Home. Tapping a key-bound one skips the editor: the draft is
 * pre-filled with these actions and the user only assigns a key. An [automation] opens the editor
 * pre-filled instead, so the user sees the condition and can finish any choice it needs (a
 * Bluetooth device, say) before saving.
 *
 * Tile colour is assigned at render time from the first action's category, not declared here, so a
 * suggestion keeps its colour once it is built into a script.
 *
 * [available] is derived from the catalogs rather than declared, so a suggestion can never
 * advertise an action or condition that has no working implementation.
 */
data class SuggestedShortcut(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    /** Action type whose icon represents the whole suggestion. */
    val iconType: String,
    val actions: List<ScriptAction>,
    /** Key-less: fires on the false -> true edge of [constraints]; see [ShortcutScript.automation]. */
    val automation: Boolean = false,
    val constraints: List<ScriptConstraint> = emptyList(),
) {
    val available: Boolean = actions.all { ScriptActionCatalog.isAvailable(it.type) } &&
        constraints.all { ScriptConstraintCatalog.isAvailable(it.type) } &&
        (!automation || constraints.any { ScriptConstraintCatalog.isAutomation(it.type) })
}

object SuggestedShortcuts {

    private fun delay(ms: Long) = ScriptAction("delay", mapOf("duration" to ms.toString()))
    private fun brightness(percent: Int) = ScriptAction("change_brightness", mapOf("value" to percent.toString()))

    private const val MORSE_UNIT_MS = 200L

    /** One torch flash of [units] time units followed by the one-unit intra-letter gap. */
    private fun flash(units: Int) = listOf(
        ScriptAction("enable_flashlight"),
        delay(MORSE_UNIT_MS * units),
        ScriptAction("disable_flashlight"),
        delay(MORSE_UNIT_MS),
    )

    /** S O S in Morse: three dots, three dashes, three dots, with the three-unit letter gap between. */
    private val sos: List<ScriptAction> = buildList {
        repeat(3) { addAll(flash(1)) }
        add(delay(MORSE_UNIT_MS * 2))
        repeat(3) { addAll(flash(3)) }
        add(delay(MORSE_UNIT_MS * 2))
        repeat(3) { addAll(flash(1)) }
    }

    /** Key-bound shortcuts and automations deliberately interleaved so the list reads as one menu. */
    val all: List<SuggestedShortcut> = listOf(
        SuggestedShortcut(
            id = "suggestion_flashlight",
            titleRes = R.string.suggestion_flashlight,
            subtitleRes = R.string.suggestion_flashlight_subtitle,
            iconType = "toggle_flashlight",
            actions = listOf(ScriptAction("toggle_flashlight")),
        ),
        SuggestedShortcut(
            id = "suggestion_headphones_play",
            titleRes = R.string.suggestion_headphones_play,
            subtitleRes = R.string.suggestion_headphones_play_subtitle,
            iconType = "media_play",
            actions = listOf(ScriptAction("media_play")),
            automation = true,
            constraints = listOf(ScriptConstraint("headphones_connected")),
        ),
        SuggestedShortcut(
            id = "suggestion_play_pause",
            titleRes = R.string.suggestion_play_pause,
            subtitleRes = R.string.suggestion_play_pause_subtitle,
            iconType = "media_play_pause",
            actions = listOf(ScriptAction("media_play_pause")),
        ),
        SuggestedShortcut(
            id = "suggestion_assistant",
            titleRes = R.string.suggestion_assistant,
            subtitleRes = R.string.suggestion_assistant_subtitle,
            iconType = "launch_voice_assistant",
            actions = listOf(ScriptAction("launch_voice_assistant")),
        ),
        SuggestedShortcut(
            id = "suggestion_screenshot",
            titleRes = R.string.suggestion_screenshot,
            subtitleRes = R.string.suggestion_screenshot_subtitle,
            iconType = "take_screenshot",
            actions = listOf(ScriptAction("take_screenshot")),
        ),
        SuggestedShortcut(
            id = "suggestion_bedtime",
            titleRes = R.string.suggestion_bedtime,
            subtitleRes = R.string.suggestion_bedtime_subtitle,
            iconType = "enable_dnd",
            actions = listOf(ScriptAction("enable_dnd"), brightness(10), ScriptAction("lock_device")),
        ),
        SuggestedShortcut(
            id = "suggestion_notifications",
            titleRes = R.string.suggestion_notifications,
            subtitleRes = R.string.suggestion_notifications_subtitle,
            iconType = "expand_notification_drawer",
            actions = listOf(ScriptAction("expand_notification_drawer")),
        ),
        SuggestedShortcut(
            id = "suggestion_car_bluetooth",
            titleRes = R.string.suggestion_car_bluetooth,
            subtitleRes = R.string.suggestion_car_bluetooth_subtitle,
            iconType = "toggle_bluetooth",
            actions = listOf(ScriptAction("media_play")),
            automation = true,
            // No address: the editor asks for the device before the script can be saved.
            constraints = listOf(ScriptConstraint("bluetooth_device_connected")),
        ),
        SuggestedShortcut(
            id = "suggestion_camera",
            titleRes = R.string.suggestion_camera,
            subtitleRes = R.string.suggestion_camera_subtitle,
            iconType = "open_camera",
            actions = listOf(ScriptAction("open_camera")),
        ),
        SuggestedShortcut(
            id = "suggestion_dnd",
            titleRes = R.string.suggestion_dnd,
            subtitleRes = R.string.suggestion_dnd_subtitle,
            iconType = "toggle_dnd",
            actions = listOf(ScriptAction("toggle_dnd")),
        ),
        SuggestedShortcut(
            id = "suggestion_sos",
            titleRes = R.string.suggestion_sos,
            subtitleRes = R.string.suggestion_sos_subtitle,
            iconType = "toggle_flashlight",
            actions = sos,
        ),
        SuggestedShortcut(
            id = "suggestion_wifi",
            titleRes = R.string.suggestion_wifi,
            subtitleRes = R.string.suggestion_wifi_subtitle,
            iconType = "toggle_wifi",
            actions = listOf(ScriptAction("toggle_wifi")),
        ),
        SuggestedShortcut(
            id = "suggestion_low_battery",
            titleRes = R.string.suggestion_low_battery,
            subtitleRes = R.string.suggestion_low_battery_subtitle,
            iconType = "disable_bluetooth",
            actions = listOf(ScriptAction("disable_bluetooth"), brightness(20)),
            automation = true,
            constraints = listOf(ScriptConstraint("battery_below", mapOf("percent" to "15"))),
        ),
        SuggestedShortcut(
            id = "suggestion_lock",
            titleRes = R.string.suggestion_lock,
            subtitleRes = R.string.suggestion_lock_subtitle,
            iconType = "lock_device",
            actions = listOf(ScriptAction("lock_device")),
        ),
        SuggestedShortcut(
            id = "suggestion_auto_rotate",
            titleRes = R.string.suggestion_auto_rotate,
            subtitleRes = R.string.suggestion_auto_rotate_subtitle,
            iconType = "toggle_auto_rotate",
            actions = listOf(ScriptAction("toggle_auto_rotate")),
        ),
        SuggestedShortcut(
            id = "suggestion_night_charge_dnd",
            titleRes = R.string.suggestion_night_charge_dnd,
            subtitleRes = R.string.suggestion_night_charge_dnd_subtitle,
            iconType = "enable_dnd",
            actions = listOf(ScriptAction("enable_dnd")),
            automation = true,
            constraints = listOf(
                ScriptConstraint("charging"),
                ScriptConstraint("time", mapOf("start" to "22:00", "end" to "07:00")),
            ),
        ),
        SuggestedShortcut(
            id = "suggestion_bluetooth",
            titleRes = R.string.suggestion_bluetooth,
            subtitleRes = R.string.suggestion_bluetooth_subtitle,
            iconType = "toggle_bluetooth",
            actions = listOf(ScriptAction("toggle_bluetooth")),
        ),
        SuggestedShortcut(
            id = "suggestion_vibrate_mode",
            titleRes = R.string.suggestion_vibrate_mode,
            subtitleRes = R.string.suggestion_vibrate_mode_subtitle,
            iconType = "vibrate",
            actions = listOf(ScriptAction("change_ringer_mode", mapOf("mode" to "vibrate"))),
        ),
        SuggestedShortcut(
            id = "suggestion_torch_off_screen_off",
            titleRes = R.string.suggestion_torch_off_screen_off,
            subtitleRes = R.string.suggestion_torch_off_screen_off_subtitle,
            iconType = "disable_flashlight",
            actions = listOf(ScriptAction("disable_flashlight")),
            automation = true,
            constraints = listOf(ScriptConstraint("screen_off"), ScriptConstraint("flashlight_on")),
        ),
        SuggestedShortcut(
            id = "suggestion_battery_saver",
            titleRes = R.string.suggestion_battery_saver,
            subtitleRes = R.string.suggestion_battery_saver_subtitle,
            iconType = "disable_wifi",
            actions = listOf(ScriptAction("disable_bluetooth"), ScriptAction("disable_wifi"), brightness(20)),
        ),
    )

    /** Working suggestions first, so the grid never opens on a wall of greyed-out tiles. */
    val ordered: List<SuggestedShortcut> = all.sortedByDescending { it.available }
}
