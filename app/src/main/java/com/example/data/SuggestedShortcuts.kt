package com.example.data

import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R

/**
 * A ready-made shortcut offered on Home. Tapping a key-bound one skips the editor unless
 * [needsEditor]: the draft is pre-filled with these actions and the user only assigns a key. An [automation] opens the editor
 * pre-filled instead, so the user sees the condition and can finish any choice it needs (a
 * Bluetooth device, say) before saving.
 *
 * Tile colour is assigned at render time from the first action's category, not declared here, so a
 * suggestion keeps its colour once it is built into a script.
 *
 * [available] is derived from the catalogs rather than declared, so a suggestion can never
 * advertise an action or condition that has no working implementation.
 */
/**
 * Shelves of the Home recipe gallery, in display order. The first three are the old shape groups,
 * which a recipe falls into unless it names a shelf of its own.
 */
enum class RecipeCategory(@StringRes val titleRes: Int) {
    SINGLE(R.string.home_suggestions_group_single),
    MULTI(R.string.home_suggestions_group_multi),
    AUTOMATION(R.string.home_suggestions_group_automation),
    ROUTINES(R.string.recipe_category_routines),
    COMMANDS(R.string.recipe_category_commands),
}

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
    /** What the user fills in and what can go wrong, shown in full on the row and in the editor. */
    @StringRes val noteRes: Int? = null,
    val category: RecipeCategory = when {
        automation -> RecipeCategory.AUTOMATION
        actions.size > 1 -> RecipeCategory.MULTI
        else -> RecipeCategory.SINGLE
    },
) {
    val available: Boolean = actions.all { ScriptActionCatalog.isAvailable(it.type) } &&
        constraints.all { ScriptConstraintCatalog.isAvailable(it.type) } &&
        (!automation || constraints.any { ScriptConstraintCatalog.isAutomation(it.type) })

    /**
     * True when picking this cannot go straight to "press a key, done": an automation shows its
     * condition first, a recipe shipped with a blank required field (a webhook's URL, which
     * CLAUDE.md forbids shipping) must be filled in before it can run, and a privileged action is
     * shown literally before it can run (CLAUDE.md "Privileged actions must stay legible").
     */
    val needsEditor: Boolean get() = automation || actions.any {
        it.type in ScriptActionCatalog.PRIVILEGED || ActionEditorForms.missingRequired(it).isNotEmpty()
    }
}

object SuggestedShortcuts {

    private fun delay(ms: Long) = ScriptAction("delay", mapOf("duration" to ms.toString()))
    private fun brightness(percent: Int) = ScriptAction("change_brightness", mapOf("value" to percent.toString()))

    /** An HTTP request with its URL deliberately left out; the editor asks for it. */
    private fun webhook(method: String, body: String = "") = ScriptAction(
        "http_request",
        if (body.isEmpty()) mapOf("method" to method) else mapOf("method" to method, "body" to body),
    )

    private fun shell(command: String) = ScriptAction("shell_command", mapOf("command" to command))
    private fun link(uri: String) = ScriptAction("open_url", mapOf("url" to uri))
    private fun atTime(time: String) = ScriptConstraint(ScriptConstraintCatalog.AT_TIME, mapOf("time" to time))

    /** One single-action recipe on the Ready-made commands shelf. */
    private fun command(
        id: String,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
        action: ScriptAction,
        automation: Boolean = false,
        constraints: List<ScriptConstraint> = emptyList(),
    ) = SuggestedShortcut(
        id = id,
        titleRes = titleRes,
        subtitleRes = subtitleRes,
        iconType = action.type,
        actions = listOf(action),
        automation = automation,
        constraints = constraints,
        category = RecipeCategory.COMMANDS,
    )

    // Ready-made commands. Long-running ones end in `&`: the bridge runs actions on one thread.
    private const val SCREEN_RECORD =
        "screenrecord --time-limit 30 /sdcard/Movies/KeySnap_$(date +%Y%m%d_%H%M%S).mp4 >/dev/null 2>&1 &"
    private const val VIDEO_CAMERA = "am start -a android.media.action.VIDEO_CAMERA"
    private const val INTERNET_PANEL = "am start -a android.settings.panel.action.INTERNET_CONNECTIVITY"
    private const val ANIMATIONS_FAST = "settings put global window_animation_scale 0.5; " +
        "settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"
    private const val ANIMATIONS_NORMAL = "settings put global window_animation_scale 1; " +
        "settings put global transition_animation_scale 1; settings put global animator_duration_scale 1"
    private const val TIMER_5_MIN = "am start -a android.intent.action.SET_TIMER " +
        "--ei android.intent.extra.alarm.LENGTH 300 --ez android.intent.extra.alarm.SKIP_UI true"
    private const val DARK_TOGGLE = "if cmd uimode night | grep -q yes; then cmd uimode night no; else cmd uimode night yes; fi"
    private const val DARK_ON = "cmd uimode night yes"
    private const val DARK_OFF = "cmd uimode night no"
    private const val POWER_SAVING_TOGGLE =
        """if [ "$(settings get global low_power)" = 1 ]; then cmd power set-mode 0; else cmd power set-mode 1; fi"""
    private const val POWER_SAVING_ON = "cmd power set-mode 1"
    private const val GRAYSCALE_TOGGLE =
        """if [ "$(settings get secure accessibility_display_daltonizer_enabled)" = 1 ]; then """ +
            "settings put secure accessibility_display_daltonizer_enabled 0; else " +
            "settings put secure accessibility_display_daltonizer 0; settings put secure accessibility_display_daltonizer_enabled 1; fi"
    private const val GRAYSCALE_ON =
        "settings put secure accessibility_display_daltonizer 0; settings put secure accessibility_display_daltonizer_enabled 1"
    // Map searches stay English: no per-locale query mechanism, and map apps match English terms anywhere.
    private const val PHARMACY = "geo:0,0?q=pharmacy"
    private const val EMAIL = "mailto:"
    private const val GAS = "geo:0,0?q=gas+station"
    private const val EV_CHARGERS = "geo:0,0?q=ev+charging+station"

    /**
     * The only commands, URIs and request bodies a recipe may pre-fill into a [ScriptActionCatalog.PRIVILEGED]
     * action (CLAUDE.md "Privileged actions must stay legible"). Each was reviewed on its own: none
     * changes a security-relevant setting, disables a protection or calls a network endpoint. HTTP
     * URLs are never here: the user pastes them. Every recipe carrying one opens the editor first.
     */
    val PREFILLED_PAYLOADS: Set<String> = setOf(
        SCREEN_RECORD, VIDEO_CAMERA, INTERNET_PANEL, ANIMATIONS_FAST, ANIMATIONS_NORMAL, TIMER_5_MIN,
        DARK_TOGGLE, DARK_ON, DARK_OFF, POWER_SAVING_TOGGLE, POWER_SAVING_ON, GRAYSCALE_TOGGLE, GRAYSCALE_ON,
        PHARMACY, EMAIL, GAS, EV_CHARGERS,
        "content=Sent from KeySnap", "Sent from KeySnap", "source=KeySnap", "text=Sent from KeySnap",
    )

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
            category = RecipeCategory.ROUTINES,
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
            category = RecipeCategory.ROUTINES,
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
        // Opens the chat with the message typed in; the user taps Send. Contact, app and text are
        // left blank: the user picks them on their own phone, so no app is named here.
        SuggestedShortcut(
            id = "recipe_send_message",
            titleRes = R.string.recipe_send_message,
            subtitleRes = R.string.recipe_send_message_subtitle,
            iconType = "send_message",
            actions = listOf(ScriptAction("send_message")),
            noteRes = R.string.recipe_send_message_note,
        ),
        // Everyday routines: the phone automations people most often set up elsewhere, rebuilt from
        // local conditions. A geofence becomes a Wi-Fi network, the place signal KeySnap has.
        SuggestedShortcut(
            id = "recipe_wifi_vibrate",
            titleRes = R.string.recipe_wifi_vibrate,
            subtitleRes = R.string.recipe_wifi_vibrate_subtitle,
            iconType = "change_ringer_mode",
            actions = listOf(ScriptAction("change_ringer_mode", mapOf("mode" to "vibrate"))),
            automation = true,
            // No SSID: the editor opens on the condition and the subtitle asks for the work network.
            constraints = listOf(ScriptConstraint("connected_to_wifi_network")),
            category = RecipeCategory.ROUTINES,
        ),
        SuggestedShortcut(
            id = "recipe_wifi_ring",
            titleRes = R.string.recipe_wifi_ring,
            subtitleRes = R.string.recipe_wifi_ring_subtitle,
            iconType = "change_ringer_mode",
            actions = listOf(ScriptAction("change_ringer_mode", mapOf("mode" to "normal"))),
            automation = true,
            constraints = listOf(ScriptConstraint("disconnected_from_wifi_network")),
            category = RecipeCategory.ROUTINES,
        ),
        SuggestedShortcut(
            id = "recipe_morning_dnd_off",
            titleRes = R.string.recipe_morning_dnd_off,
            subtitleRes = R.string.recipe_morning_dnd_off_subtitle,
            iconType = "disable_dnd",
            actions = listOf(ScriptAction("disable_dnd")),
            automation = true,
            constraints = listOf(
                ScriptConstraint("discharging"),
                ScriptConstraint("time", mapOf("start" to "06:00", "end" to "10:00")),
            ),
            category = RecipeCategory.ROUTINES,
        ),
        // Ready-made commands: every privileged payload comes from PREFILLED_PAYLOADS and every one
        // of these opens the editor (needsEditor), so the literal command is read before it can run.
        // Key-triggered ones ship with no key; the editor asks for it.
        command("recipe_screen_record", R.string.recipe_screen_record, R.string.recipe_screen_record_subtitle, shell(SCREEN_RECORD)),
        command("recipe_video_camera", R.string.recipe_video_camera, R.string.recipe_video_camera_subtitle, shell(VIDEO_CAMERA)),
        command("recipe_internet_panel", R.string.recipe_internet_panel, R.string.recipe_internet_panel_subtitle, shell(INTERNET_PANEL)),
        command("recipe_animations_fast", R.string.recipe_animations_fast, R.string.recipe_animations_fast_subtitle, shell(ANIMATIONS_FAST)),
        command("recipe_animations_normal", R.string.recipe_animations_normal, R.string.recipe_animations_normal_subtitle, shell(ANIMATIONS_NORMAL)),
        command("recipe_timer_5_min", R.string.recipe_timer_5_min, R.string.recipe_timer_5_min_subtitle, shell(TIMER_5_MIN)),
        command("recipe_dark_mode_toggle", R.string.recipe_dark_mode_toggle, R.string.recipe_dark_mode_toggle_subtitle, shell(DARK_TOGGLE)),
        command("recipe_power_saving_toggle", R.string.recipe_power_saving_toggle, R.string.recipe_power_saving_toggle_subtitle, shell(POWER_SAVING_TOGGLE)),
        command("recipe_grayscale_toggle", R.string.recipe_grayscale_toggle, R.string.recipe_grayscale_toggle_subtitle, shell(GRAYSCALE_TOGGLE)),
        command("recipe_nearby_pharmacy", R.string.recipe_nearby_pharmacy, R.string.recipe_nearby_pharmacy_subtitle, link(PHARMACY)),
        command("recipe_write_email", R.string.recipe_write_email, R.string.recipe_write_email_subtitle, link(EMAIL)),
        command(
            "recipe_evening_dark", R.string.recipe_evening_dark, R.string.recipe_evening_dark_subtitle, shell(DARK_ON),
            automation = true, constraints = listOf(atTime("21:00")),
        ),
        command(
            "recipe_morning_light", R.string.recipe_morning_light, R.string.recipe_morning_light_subtitle, shell(DARK_OFF),
            automation = true, constraints = listOf(atTime("07:00")),
        ),
        command(
            "recipe_low_battery_power_saving", R.string.recipe_low_battery_power_saving,
            R.string.recipe_low_battery_power_saving_subtitle, shell(POWER_SAVING_ON),
            automation = true,
            constraints = listOf(ScriptConstraint("battery_below", mapOf("percent" to "20")), ScriptConstraint("discharging")),
        ),
        command(
            "recipe_bedtime_grayscale", R.string.recipe_bedtime_grayscale, R.string.recipe_bedtime_grayscale_subtitle, shell(GRAYSCALE_ON),
            automation = true, constraints = listOf(atTime("23:00")),
        ),
        // Key-triggered with an Only-if: no device address, so the editor asks for the car.
        command(
            "recipe_car_gas", R.string.recipe_car_gas, R.string.recipe_car_gas_subtitle, link(GAS),
            constraints = listOf(ScriptConstraint("bluetooth_device_connected")),
        ),
        command(
            "recipe_car_chargers", R.string.recipe_car_chargers, R.string.recipe_car_chargers_subtitle, link(EV_CHARGERS),
            constraints = listOf(ScriptConstraint("bluetooth_device_connected")),
        ),
        // Service recipes: every URL is blank (CLAUDE.md forbids shipping an endpoint the user did
        // not enter), so each opens the editor on it, and the subtitle says what to paste. Bodies are
        // form-encoded because the HTTP action sets no Content-Type and HttpURLConnection then sends
        // form-urlencoded. Renamed ones keep their old ids, which users may have built.
        command("recipe_smart_home", R.string.recipe_smart_home, R.string.recipe_smart_home_subtitle, webhook("POST")),
        command(
            "recipe_ha_arrived_home", R.string.recipe_ha_arrived_home, R.string.recipe_ha_arrived_home_subtitle, webhook("POST"),
            // No SSID: the editor opens on the condition and the subtitle asks for the home network.
            automation = true, constraints = listOf(ScriptConstraint("connected_to_wifi_network")),
        ),
        command("recipe_chat_channel", R.string.recipe_chat_channel, R.string.recipe_chat_channel_subtitle, webhook("POST", "content=Sent from KeySnap")),
        command("recipe_phone_notification", R.string.recipe_phone_notification, R.string.recipe_phone_notification_subtitle, webhook("POST", "Sent from KeySnap")),
        command("recipe_spreadsheet", R.string.recipe_spreadsheet, R.string.recipe_spreadsheet_subtitle, webhook("POST", "source=KeySnap")),
        command("recipe_web_address", R.string.recipe_web_address, R.string.recipe_web_address_subtitle, webhook("GET")),
        // A sample body keeps it apart from the blank Home Assistant POST (one shape per recipe).
        command("recipe_web_post", R.string.recipe_web_post, R.string.recipe_web_post_subtitle, webhook("POST", "text=Sent from KeySnap")),
    )

    /** Working suggestions first, so the grid never opens on a wall of greyed-out tiles. */
    val ordered: List<SuggestedShortcut> = all.sortedByDescending { it.available }
}
