package com.example.data

import com.example.mode.ModeHooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScriptActionCatalogTest {

    private val res = RuntimeEnvironment.getApplication().resources

    @Test fun everyTypeIdIsUnique() {
        val types = ScriptActionCatalog.all.map { it.type }
        assertEquals(types.size, types.distinct().size)
    }

    /**
     * Pinned deliberately: every id in this set must be implemented by both BridgeConfig's
     * ACTION_* constants/BridgeMain.executeAction and the in-app ScriptActionExecutor, so a
     * script authored in the app runs identically in the detached bridge. This is not a claim
     * that the two lists are identical — the bridge may retain a retired action id (such as
     * "input_key_event") for backward compatibility with a config file written by an older app
     * build. Changing this set means changing both sides.
     */
    @Test fun theAvailableSetIsExactlyWhatBothExecutorsImplement() {
        val available = ScriptActionCatalog.all.filter { it.availability == ActionAvailability.AVAILABLE }.map { it.type }
        assertEquals(
            setOf(
                "toggle_flashlight",
                "enable_flashlight",
                "disable_flashlight",
                "media_play_pause",
                "media_next",
                "media_previous",
                "media_play",
                "media_pause",
                "media_fast_forward",
                "media_rewind",
                "media_stop",
                "volume_toggle_mute",
                "volume_up",
                "volume_down",
                "delay",
                "go_back",
                "go_home",
                "open_recents",
                "open_menu",
                "expand_notification_drawer",
                "expand_quick_settings",
                "collapse_status_bar",
                "take_screenshot",
                "toggle_wifi",
                "enable_wifi",
                "disable_wifi",
                "toggle_mobile_data",
                "enable_mobile_data",
                "disable_mobile_data",
                "toggle_airplane_mode",
                "toggle_location",
                "lock_device",
                "secure_lock_device",
                "toggle_hotspot",
                "power_dialog",
                "toggle_dnd",
                "enable_dnd",
                "disable_dnd",
                "toggle_battery_saver",
                "enable_battery_saver",
                "disable_battery_saver",
                "always_on_display",
                "grayscale",
                "charge_limit",
                "bedtime",
                "dark_mode",
                "night_light",
                "extra_dim",
                "screen_timeout",
                "toggle_nfc",
                "enable_nfc",
                "disable_nfc",
                "toggle_notification_drawer",
                "toggle_quick_settings",
                "dismiss_all_notifications",
                "learned_setting",
                "text_cut",
                "text_copy",
                "text_paste",
                "show_keyboard_picker",
                "switch_keyboard",
                "launch_app",
                "launch_app_shortcut",
                "answer_call",
                "end_call",
                "open_url",
                "launch_voice_assistant",
                "launch_device_assistant",
                "mind_space_capture",
                "open_mind_space",
                "built_in_assistant",
                "translate_screen",
                "record_audio",
                "open_osee",
                "open_camera",
                "open_settings",
                "open_hotspot_settings",
                "set_timer",
                "set_alarm",
                "copy_to_clipboard",
                "keep_screen_awake",
                "close_and_clear_app",
                "go_last_app",
                "hide_keyboard",
                "change_flashlight_brightness",
                "force_stop_app",
                "set_payment_app",
                "modify_setting",
                "send_intent",
                "reply_to_notification",
                "http_request",
                "shell_command",
                "screen_on",
                "screen_off",
                "toggle_screen",
                "toggle_auto_rotate",
                "portrait_mode",
                "landscape_mode",
                "cycle_rotations",
                "change_brightness",
                "toggle_auto_brightness",
                "increase_brightness",
                "decrease_brightness",
                "play_sound",
                "text_to_speech",
                "stop_text_to_speech",
                "vibrate",
                "repeat_previous",
                "cycle_mode",
                "toggle_mapping",
                "pause_mapping",
                "resume_mapping",
                "input_key_code",
                "input_text",
                "tap_screen",
                "swipe_screen",
                "pinch_screen",
                "toggle_split_screen",
                "move_cursor_to_end",
                "select_word_at_cursor",
                "volume_mute",
                "volume_unmute",
                "show_volume_dialog",
                "change_volume_stream",
                "toggle_bluetooth",
                "enable_bluetooth",
                "disable_bluetooth",
                "cycle_ringer_mode",
                "change_ringer_mode",
                "show_notification",
                "set_wallpaper",
                "send_message",
            ),
            available.toSet(),
        )
    }

    @Test fun everyCategoryHasAtLeastOneEntry() {
        ActionCategory.entries.forEach { category ->
            assertTrue("No catalog entries for $category", ScriptActionCatalog.byCategory[category].orEmpty().isNotEmpty())
        }
    }

    @Test fun searchIsCaseInsensitiveAndMatchesTitleSubstring() {
        val results = ScriptActionCatalog.search("flash", res)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { res.getString(it.titleRes).contains("flash", ignoreCase = true) })
    }

    @Test fun blankSearchReturnsEverything() {
        assertEquals(ScriptActionCatalog.all.size, ScriptActionCatalog.search("", res).size)
        assertEquals(ScriptActionCatalog.all.size, ScriptActionCatalog.search("   ", res).size)
    }

    @Test fun descriptorLooksUpByType() {
        val descriptor = ScriptActionCatalog.descriptor("toggle_flashlight")
        assertEquals("toggle_flashlight", descriptor?.type)
        assertEquals(ActionCategory.FLASHLIGHT, descriptor?.category)
    }

    @Test fun isAvailableMatchesDescriptorAvailability() {
        assertTrue(ScriptActionCatalog.isAvailable("toggle_flashlight"))
        assertTrue(!ScriptActionCatalog.isAvailable("open_app_drawer"))
        assertTrue(!ScriptActionCatalog.isAvailable("not_a_real_action"))
    }

    @Test fun tapScreenTakesAnOptionalRepeatCountAndInterval() {
        val params = ScriptActionCatalog.descriptor("tap_screen")!!.parameters.associateBy { it.key }
        assertEquals(setOf("x", "y", "count", "interval"), params.keys)
        assertTrue(params.getValue("x").required)
        assertTrue(!params.getValue("count").required)
        assertTrue(!params.getValue("interval").required)
    }

    @Test fun pinchScreenIsDefinedByTwoPointsAndADirection() {
        val params = ScriptActionCatalog.descriptor("pinch_screen")!!.parameters.associateBy { it.key }
        assertEquals(setOf("x1", "y1", "x2", "y2", "pinchType", "duration"), params.keys)
        assertEquals(listOf("in", "out"), params.getValue("pinchType").choices)
    }

    @Test fun cycleModeKeepsItsTimingsOptionalAndThePickerFollowsTheEdition() {
        val params = ScriptActionCatalog.descriptor("cycle_mode")!!.parameters.associateBy { it.key }
        assertEquals(setOf("count", "feedback", "pulseMs", "gapMs", "leadGapMs"), params.keys)
        assertTrue(params.getValue("count").required)
        assertTrue(!params.getValue("pulseMs").required)
        assertTrue(!params.getValue("gapMs").required)
        assertTrue(!params.getValue("leadGapMs").required)
        assertEquals(ModeHooks.AVAILABLE, ModeHooks.offered("cycle_mode"))
        assertEquals(ModeHooks.AVAILABLE, ModeHooks.offered(ScriptConstraintCatalog.MODE_IS))
        assertTrue(ModeHooks.offered("toggle_flashlight"))
    }

    @Test fun inputKeyEventIsNoLongerACatalogEntry() {
        // It duplicated input_key_code exactly; PersistedScriptAction migration aliases it.
        assertEquals(null, ScriptActionCatalog.descriptor("input_key_event"))
    }

    /**
     * Pinned deliberately, same reasoning as [theAvailableSetIsExactlyWhatBothExecutorsImplement]:
     * these are the actions HardwareKeyTriggerCoordinator keeps running in-app while the bridge
     * owns everything else, so a change here silently changes what runs in Advanced Mode. Keep in
     * sync with BridgeMain.executeAction's `return false` branches.
     */
    @Test fun unsupportedByBridgeSetIsPinned() {
        assertEquals(
            setOf(
                "launch_device_assistant",
                "launch_voice_assistant",
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
                "set_wallpaper",
                "send_message",
                "input_text",
                "lock_device",
                "take_screenshot",
                "text_cut",
                "text_copy",
                "text_paste",
                "open_recents",
                "show_notification",
                "reply_to_notification",
                "dismiss_all_notifications",
                "go_last_app",
                "hide_keyboard",
                "change_flashlight_brightness",
                "copy_to_clipboard",
                "keep_screen_awake",
            ),
            ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE,
        )
    }

    @Test fun everyUnsupportedByBridgeActionIsAvailable() {
        val available = ScriptActionCatalog.all.filter { it.availability == ActionAvailability.AVAILABLE }.map { it.type }.toSet()
        assertTrue(ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE.all { it in available })
    }

    /** CLAUDE.md's mirror table: every AVAILABLE id has a BridgeConfig.ACTION_* constant. */
    @Test fun everyAvailableTypeHasABridgeConfigConstant() {
        val bridgeIds = com.example.bridge.BridgeConfig::class.java.fields
            .filter { it.name.startsWith("ACTION_") && it.type == String::class.java }
            .map { it.get(null) as String }
            .toSet()
        val available = ScriptActionCatalog.all.filter { it.availability == ActionAvailability.AVAILABLE }.map { it.type }
        assertEquals(emptyList<String>(), available.filterNot { it in bridgeIds })
    }

    /**
     * CLAUDE.md "Privileged actions must stay legible": the import review and recipe tests key off
     * this set. show_notification is in it because its tap target is a user-typed URL, like open_url;
     * reply_to_notification and send_message because they put user-typed text into a real person's chat.
     */
    @Test fun privilegedActionsAreAvailableCatalogEntries() {
        assertEquals(
            setOf(
                "shell_command", "modify_setting", "force_stop_app", "close_and_clear_app", "send_intent", "http_request", "open_url", "show_notification",
                "reply_to_notification", "send_message", "learned_setting", "set_payment_app",
            ),
            ScriptActionCatalog.PRIVILEGED,
        )
        ScriptActionCatalog.PRIVILEGED.forEach { assertTrue(it, ScriptActionCatalog.isAvailable(it)) }
    }

    @Test
    fun pickerTabsNameOnlyRealAvailableActions() {
        val popular = ScriptActionCatalog.POPULAR
        assertEquals(popular.size, popular.toSet().size)
        (popular + ScriptActionCatalog.NEW).forEach { assertTrue(it, ScriptActionCatalog.isAvailable(it)) }
    }
}
