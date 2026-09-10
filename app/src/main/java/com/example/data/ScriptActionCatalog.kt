package com.example.data

import android.content.res.Resources
import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R

/** Groups the action catalog for the picker sheet and matches the reference iOS Shortcuts layout. */
enum class ActionCategory(@StringRes val titleRes: Int) {
    INPUT(R.string.action_category_input),
    APPS(R.string.action_category_apps),
    FLASHLIGHT(R.string.action_category_flashlight),
    NAVIGATION(R.string.action_category_navigation),
    VOLUME(R.string.action_category_volume),
    DISPLAY(R.string.action_category_display),
    MEDIA(R.string.action_category_media),
    CONNECTIVITY(R.string.action_category_connectivity),
    KEYBOARD(R.string.action_category_keyboard),
    DEVICE(R.string.action_category_device),
    SOUND(R.string.action_category_sound),
    AUTOMATION(R.string.action_category_automation),
}

/** Whether a catalog entry can actually run yet. Everything else renders greyed out as "Coming soon". */
enum class ActionAvailability { AVAILABLE, COMING_SOON }

enum class ActionParameterKind { TEXT, INTEGER, MILLISECONDS, PACKAGE, KEY_CODE, URL, CHOICE }

data class ActionParameterSpec(
    val key: String,
    @StringRes val labelRes: Int,
    val kind: ActionParameterKind,
    val required: Boolean = true,
    val choices: List<String> = emptyList(),
)

data class ActionDescriptor(
    val type: String,
    @StringRes val titleRes: Int,
    val category: ActionCategory,
    val availability: ActionAvailability = ActionAvailability.COMING_SOON,
    val parameters: List<ActionParameterSpec> = emptyList(),
)

/**
 * Single source of truth for action type ids. `BridgeConfig` (Java) mirrors the AVAILABLE subset
 * and must never diverge from it, since a script authored here is later matched by the bridge.
 */
object ScriptActionCatalog {

    private fun param(key: String, @StringRes labelRes: Int, kind: ActionParameterKind, required: Boolean = true, choices: List<String> = emptyList()) =
        ActionParameterSpec(key, labelRes, kind, required, choices)

    val all: List<ActionDescriptor> = listOf(
        // Flashlight
        ActionDescriptor("toggle_flashlight", R.string.action_toggle_flashlight, ActionCategory.FLASHLIGHT, ActionAvailability.AVAILABLE),
        ActionDescriptor("enable_flashlight", R.string.action_enable_flashlight, ActionCategory.FLASHLIGHT, ActionAvailability.AVAILABLE),
        ActionDescriptor("disable_flashlight", R.string.action_disable_flashlight, ActionCategory.FLASHLIGHT, ActionAvailability.AVAILABLE),
        ActionDescriptor("change_flashlight_brightness", R.string.action_change_flashlight_brightness, ActionCategory.FLASHLIGHT, parameters = listOf(param("strength", R.string.param_strength, ActionParameterKind.INTEGER))),

        // Input
        ActionDescriptor("input_key_code", R.string.action_input_key_code, ActionCategory.INPUT, ActionAvailability.AVAILABLE, listOf(param("keyCode", R.string.param_key_code, ActionParameterKind.KEY_CODE))),
        ActionDescriptor("input_text", R.string.action_input_text, ActionCategory.INPUT, ActionAvailability.AVAILABLE, listOf(param("text", R.string.param_text, ActionParameterKind.TEXT))),
        ActionDescriptor(
            "tap_screen", R.string.action_tap_screen, ActionCategory.INPUT, ActionAvailability.AVAILABLE,
            listOf(
                param("x", R.string.param_x, ActionParameterKind.INTEGER),
                param("y", R.string.param_y, ActionParameterKind.INTEGER),
                param("count", R.string.param_taps, ActionParameterKind.INTEGER, required = false),
                param("interval", R.string.param_interval_ms, ActionParameterKind.MILLISECONDS, required = false),
            ),
        ),
        ActionDescriptor(
            "swipe_screen", R.string.action_swipe_screen, ActionCategory.INPUT, ActionAvailability.AVAILABLE,
            listOf(
                param("x1", R.string.param_start_x, ActionParameterKind.INTEGER),
                param("y1", R.string.param_start_y, ActionParameterKind.INTEGER),
                param("x2", R.string.param_end_x, ActionParameterKind.INTEGER),
                param("y2", R.string.param_end_y, ActionParameterKind.INTEGER),
                param("duration", R.string.param_duration_ms, ActionParameterKind.MILLISECONDS, required = false),
            ),
        ),
        // Two points define the pinch axis at any angle; "in" closes them onto their midpoint,
        // "out" spreads them from it. The bridge has no counterpart — the shell `input` command
        // has no multi-touch primitive — so this stays an Accessibility-only action.
        ActionDescriptor(
            "pinch_screen", R.string.action_pinch_screen, ActionCategory.INPUT, ActionAvailability.AVAILABLE,
            listOf(
                param("x1", R.string.param_point_a_x, ActionParameterKind.INTEGER),
                param("y1", R.string.param_point_a_y, ActionParameterKind.INTEGER),
                param("x2", R.string.param_point_b_x, ActionParameterKind.INTEGER),
                param("y2", R.string.param_point_b_y, ActionParameterKind.INTEGER),
                param("pinchType", R.string.param_direction, ActionParameterKind.CHOICE, choices = listOf("in", "out")),
                param("duration", R.string.param_duration_ms, ActionParameterKind.MILLISECONDS, required = false),
            ),
        ),

        // Apps
        ActionDescriptor("launch_app", R.string.action_launch_app, ActionCategory.APPS, ActionAvailability.AVAILABLE, listOf(param("packageName", R.string.param_app, ActionParameterKind.PACKAGE))),
        ActionDescriptor("launch_app_shortcut", R.string.action_launch_app_shortcut, ActionCategory.APPS, parameters = listOf(param("shortcutId", R.string.param_shortcut, ActionParameterKind.TEXT))),
        ActionDescriptor("open_url", R.string.action_open_url, ActionCategory.APPS, ActionAvailability.AVAILABLE, listOf(param("url", R.string.param_url, ActionParameterKind.URL))),
        ActionDescriptor("http_request", R.string.action_http_request, ActionCategory.APPS, ActionAvailability.AVAILABLE, listOf(param("method", R.string.param_method, ActionParameterKind.CHOICE, choices = listOf("GET", "POST", "PUT", "DELETE")), param("url", R.string.param_url, ActionParameterKind.URL), param("body", R.string.param_body, ActionParameterKind.TEXT, required = false))),
        ActionDescriptor("shell_command", R.string.action_shell_command, ActionCategory.APPS, ActionAvailability.AVAILABLE, listOf(param("command", R.string.param_command, ActionParameterKind.TEXT))),
        // "extras" is optional "key=value" pairs separated by ';', each added as a String extra.
        ActionDescriptor("send_intent", R.string.action_send_intent, ActionCategory.APPS, ActionAvailability.AVAILABLE, listOf(param("action", R.string.param_action, ActionParameterKind.TEXT), param("uri", R.string.param_uri, ActionParameterKind.TEXT, required = false), param("extras", R.string.param_extras, ActionParameterKind.TEXT, required = false))),
        ActionDescriptor("interact_with_app_element", R.string.action_interact_with_app_element, ActionCategory.APPS, parameters = listOf(param("packageName", R.string.param_app, ActionParameterKind.PACKAGE), param("element", R.string.param_element, ActionParameterKind.TEXT))),
        ActionDescriptor("launch_voice_assistant", R.string.action_launch_voice_assistant, ActionCategory.APPS, ActionAvailability.AVAILABLE),
        ActionDescriptor("launch_device_assistant", R.string.action_launch_device_assistant, ActionCategory.APPS, ActionAvailability.AVAILABLE),
        ActionDescriptor("open_camera", R.string.action_open_camera, ActionCategory.APPS, ActionAvailability.AVAILABLE),
        ActionDescriptor("open_settings", R.string.action_open_settings, ActionCategory.APPS, ActionAvailability.AVAILABLE),
        ActionDescriptor("force_stop_app", R.string.action_force_stop_app, ActionCategory.APPS, ActionAvailability.AVAILABLE, listOf(param("packageName", R.string.param_app, ActionParameterKind.PACKAGE))),
        ActionDescriptor("close_and_clear_app", R.string.action_close_and_clear_app, ActionCategory.APPS, parameters = listOf(param("packageName", R.string.param_app, ActionParameterKind.PACKAGE))),
        ActionDescriptor("modify_setting", R.string.action_modify_setting, ActionCategory.APPS, ActionAvailability.AVAILABLE, listOf(param("namespace", R.string.param_namespace, ActionParameterKind.CHOICE, choices = listOf("system", "secure", "global")), param("key", R.string.param_key, ActionParameterKind.TEXT), param("value", R.string.param_value, ActionParameterKind.TEXT))),

        // Navigation
        ActionDescriptor("expand_notification_drawer", R.string.action_expand_notification_drawer, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_notification_drawer", R.string.action_toggle_notification_drawer, ActionCategory.NAVIGATION),
        ActionDescriptor("expand_quick_settings", R.string.action_expand_quick_settings, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_quick_settings", R.string.action_toggle_quick_settings, ActionCategory.NAVIGATION),
        ActionDescriptor("collapse_status_bar", R.string.action_collapse_status_bar, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("go_back", R.string.action_go_back, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("go_home", R.string.action_go_home, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("open_recents", R.string.action_open_recents, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("open_menu", R.string.action_open_menu, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("go_last_app", R.string.action_go_last_app, ActionCategory.NAVIGATION),
        ActionDescriptor("toggle_split_screen", R.string.action_toggle_split_screen, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("take_screenshot", R.string.action_take_screenshot, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("move_cursor_to_end", R.string.action_move_cursor_to_end, ActionCategory.NAVIGATION, ActionAvailability.AVAILABLE),

        // Volume
        ActionDescriptor("volume_up", R.string.action_volume_up, ActionCategory.VOLUME, ActionAvailability.AVAILABLE),
        ActionDescriptor("volume_down", R.string.action_volume_down, ActionCategory.VOLUME, ActionAvailability.AVAILABLE),
        ActionDescriptor("volume_mute", R.string.action_volume_mute, ActionCategory.VOLUME, ActionAvailability.AVAILABLE),
        ActionDescriptor("volume_unmute", R.string.action_volume_unmute, ActionCategory.VOLUME, ActionAvailability.AVAILABLE),
        ActionDescriptor("volume_toggle_mute", R.string.action_volume_toggle_mute, ActionCategory.VOLUME, ActionAvailability.AVAILABLE),
        ActionDescriptor("show_volume_dialog", R.string.action_show_volume_dialog, ActionCategory.VOLUME, ActionAvailability.AVAILABLE),
        // Silent/vibrate ringer modes need ACCESS_NOTIFICATION_POLICY on API 23+ (Settings > Special
        // app access > Do Not Disturb access, granted outside the app); the executor checks
        // NotificationManager.isNotificationPolicyAccessGranted() and fails safe if it isn't.
        ActionDescriptor("cycle_ringer_mode", R.string.action_cycle_ringer_mode, ActionCategory.VOLUME, ActionAvailability.AVAILABLE),
        ActionDescriptor("change_ringer_mode", R.string.action_change_ringer_mode, ActionCategory.VOLUME, ActionAvailability.AVAILABLE, listOf(param("mode", R.string.param_mode, ActionParameterKind.CHOICE, choices = listOf("normal", "vibrate", "silent")))),
        ActionDescriptor("change_volume_stream", R.string.action_change_volume_stream, ActionCategory.VOLUME, ActionAvailability.AVAILABLE, listOf(param("stream", R.string.param_stream, ActionParameterKind.CHOICE, choices = listOf("ring", "media", "alarm", "notification")), param("value", R.string.param_value, ActionParameterKind.INTEGER))),

        // Display
        ActionDescriptor("screen_on", R.string.action_screen_on, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("screen_off", R.string.action_screen_off, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_screen", R.string.action_toggle_screen, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_auto_rotate", R.string.action_toggle_auto_rotate, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("portrait_mode", R.string.action_portrait_mode, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("landscape_mode", R.string.action_landscape_mode, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("cycle_rotations", R.string.action_cycle_rotations, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("change_brightness", R.string.action_change_brightness, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE, listOf(param("value", R.string.param_value, ActionParameterKind.INTEGER))),
        ActionDescriptor("toggle_auto_brightness", R.string.action_toggle_auto_brightness, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("increase_brightness", R.string.action_increase_brightness, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),
        ActionDescriptor("decrease_brightness", R.string.action_decrease_brightness, ActionCategory.DISPLAY, ActionAvailability.AVAILABLE),

        // Media
        ActionDescriptor("media_play", R.string.action_media_play, ActionCategory.MEDIA, ActionAvailability.AVAILABLE),
        ActionDescriptor("media_pause", R.string.action_media_pause, ActionCategory.MEDIA, ActionAvailability.AVAILABLE),
        ActionDescriptor("media_play_pause", R.string.action_media_play_pause, ActionCategory.MEDIA, ActionAvailability.AVAILABLE),
        ActionDescriptor("media_next", R.string.action_media_next, ActionCategory.MEDIA, ActionAvailability.AVAILABLE),
        ActionDescriptor("media_previous", R.string.action_media_previous, ActionCategory.MEDIA, ActionAvailability.AVAILABLE),
        ActionDescriptor("media_fast_forward", R.string.action_media_fast_forward, ActionCategory.MEDIA, ActionAvailability.AVAILABLE),
        ActionDescriptor("media_rewind", R.string.action_media_rewind, ActionCategory.MEDIA, ActionAvailability.AVAILABLE),
        ActionDescriptor("media_stop", R.string.action_media_stop, ActionCategory.MEDIA, ActionAvailability.AVAILABLE),

        // Connectivity
        ActionDescriptor("toggle_wifi", R.string.action_toggle_wifi, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("enable_wifi", R.string.action_enable_wifi, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("disable_wifi", R.string.action_disable_wifi, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_bluetooth", R.string.action_toggle_bluetooth, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("enable_bluetooth", R.string.action_enable_bluetooth, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("disable_bluetooth", R.string.action_disable_bluetooth, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_mobile_data", R.string.action_toggle_mobile_data, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_nfc", R.string.action_toggle_nfc, ActionCategory.CONNECTIVITY),
        ActionDescriptor("toggle_airplane_mode", R.string.action_toggle_airplane_mode, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_location", R.string.action_toggle_location, ActionCategory.CONNECTIVITY, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_hotspot", R.string.action_toggle_hotspot, ActionCategory.CONNECTIVITY),

        // Keyboard
        ActionDescriptor("show_keyboard", R.string.action_show_keyboard, ActionCategory.KEYBOARD),
        ActionDescriptor("hide_keyboard", R.string.action_hide_keyboard, ActionCategory.KEYBOARD),
        ActionDescriptor("toggle_keyboard", R.string.action_toggle_keyboard, ActionCategory.KEYBOARD),
        ActionDescriptor("switch_keyboard", R.string.action_switch_keyboard, ActionCategory.KEYBOARD, ActionAvailability.AVAILABLE),
        ActionDescriptor("show_keyboard_picker", R.string.action_show_keyboard_picker, ActionCategory.KEYBOARD, ActionAvailability.AVAILABLE),
        ActionDescriptor("text_cut", R.string.action_text_cut, ActionCategory.KEYBOARD, ActionAvailability.AVAILABLE),
        ActionDescriptor("text_copy", R.string.action_text_copy, ActionCategory.KEYBOARD, ActionAvailability.AVAILABLE),
        ActionDescriptor("text_paste", R.string.action_text_paste, ActionCategory.KEYBOARD, ActionAvailability.AVAILABLE),
        ActionDescriptor("select_word_at_cursor", R.string.action_select_word_at_cursor, ActionCategory.KEYBOARD, ActionAvailability.AVAILABLE),

        // Device
        ActionDescriptor("lock_device", R.string.action_lock_device, ActionCategory.DEVICE, ActionAvailability.AVAILABLE),
        ActionDescriptor("secure_lock_device", R.string.action_secure_lock_device, ActionCategory.DEVICE),
        ActionDescriptor("power_dialog", R.string.action_power_dialog, ActionCategory.DEVICE, ActionAvailability.AVAILABLE),
        ActionDescriptor("toggle_dnd", R.string.action_toggle_dnd, ActionCategory.DEVICE, ActionAvailability.AVAILABLE),
        ActionDescriptor("enable_dnd", R.string.action_enable_dnd, ActionCategory.DEVICE, ActionAvailability.AVAILABLE),
        ActionDescriptor("disable_dnd", R.string.action_disable_dnd, ActionCategory.DEVICE, ActionAvailability.AVAILABLE),
        ActionDescriptor("open_app_drawer", R.string.action_open_app_drawer, ActionCategory.DEVICE),
        ActionDescriptor("dismiss_all_notifications", R.string.action_dismiss_all_notifications, ActionCategory.DEVICE),
        ActionDescriptor("answer_call", R.string.action_answer_call, ActionCategory.DEVICE),
        ActionDescriptor("end_call", R.string.action_end_call, ActionCategory.DEVICE),

        // Sound
        ActionDescriptor("play_sound", R.string.action_play_sound, ActionCategory.SOUND, ActionAvailability.AVAILABLE, listOf(param("uri", R.string.param_sound, ActionParameterKind.URL))),
        ActionDescriptor("text_to_speech", R.string.action_text_to_speech, ActionCategory.SOUND, ActionAvailability.AVAILABLE, listOf(param("text", R.string.param_text, ActionParameterKind.TEXT))),
        ActionDescriptor("vibrate", R.string.action_vibrate, ActionCategory.SOUND, ActionAvailability.AVAILABLE, listOf(param("duration", R.string.param_duration_ms, ActionParameterKind.MILLISECONDS))),
        ActionDescriptor("stop_text_to_speech", R.string.action_stop_text_to_speech, ActionCategory.SOUND, ActionAvailability.AVAILABLE),

        // Automation
        ActionDescriptor("delay", R.string.action_delay, ActionCategory.AUTOMATION, ActionAvailability.AVAILABLE, listOf(param("duration", R.string.param_duration_ms, ActionParameterKind.MILLISECONDS))),
        ActionDescriptor("repeat_previous", R.string.action_repeat_previous, ActionCategory.AUTOMATION, ActionAvailability.AVAILABLE, listOf(param("count", R.string.param_times, ActionParameterKind.INTEGER))),
        ActionDescriptor("toggle_mapping", R.string.action_toggle_mapping, ActionCategory.AUTOMATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("pause_mapping", R.string.action_pause_mapping, ActionCategory.AUTOMATION, ActionAvailability.AVAILABLE),
        ActionDescriptor("resume_mapping", R.string.action_resume_mapping, ActionCategory.AUTOMATION, ActionAvailability.AVAILABLE),
    )

    /**
     * AVAILABLE action types the app process runs even while the bridge is up, because the bridge
     * either cannot run them at all (no live Context/window in the shell-UID process) or can only
     * run them worse than the app can — `input text` corrupting non-ASCII, KEYCODE_SLEEP not
     * actually locking, KEYCODE_SYSRQ not taking a screenshot on most OEM builds. Mirrored by the
     * `return false` branches in `BridgeMain.executeAction` (`:bridge`) — keep both lists in sync
     * when an action moves between "app-only" and "bridge-supported".
     */
    val UNSUPPORTED_BY_BRIDGE: Set<String> = setOf(
        "pinch_screen",
        "toggle_split_screen",
        "move_cursor_to_end",
        "select_word_at_cursor",
        "volume_mute",
        "volume_unmute",
        "show_volume_dialog",
        "change_volume_stream",
        "power_dialog",
        "show_keyboard_picker",
        "text_to_speech",
        "stop_text_to_speech",
        "play_sound",
        "toggle_mapping",
        "pause_mapping",
        "resume_mapping",
        // Bridge-capable, but its shell equivalent is weaker than the app's Accessibility path.
        "input_text",
        "lock_device",
        "take_screenshot",
        "text_cut",
        "text_copy",
        "text_paste",
        "open_recents",
    )

    private val byType: Map<String, ActionDescriptor> = all.associateBy { it.type }
    val byCategory: Map<ActionCategory, List<ActionDescriptor>> = all.groupBy { it.category }

    fun descriptor(type: String): ActionDescriptor? = byType[type]

    fun isAvailable(type: String): Boolean = byType[type]?.availability == ActionAvailability.AVAILABLE

    /** Titles are localized, so search matches against the strings the user is actually reading. */
    fun search(query: String, resources: Resources): List<ActionDescriptor> {
        if (query.isBlank()) return all
        val needle = query.trim()
        return all.filter { resources.getString(it.titleRes).contains(needle, ignoreCase = true) }
    }
}
