package com.example.data

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
                "toggle_airplane_mode",
                "toggle_location",
                "lock_device",
                "power_dialog",
                "toggle_dnd",
                "enable_dnd",
                "disable_dnd",
                "text_cut",
                "text_copy",
                "text_paste",
                "show_keyboard_picker",
                "switch_keyboard",
                "launch_app",
                "open_url",
                "launch_voice_assistant",
                "launch_device_assistant",
                "open_camera",
                "open_settings",
                "force_stop_app",
                "modify_setting",
                "send_intent",
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
        assertTrue(!ScriptActionCatalog.isAvailable("launch_app_shortcut"))
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
                "input_text",
                "lock_device",
                "take_screenshot",
                "text_cut",
                "text_copy",
                "text_paste",
                "open_recents",
            ),
            ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE,
        )
    }

    @Test fun everyUnsupportedByBridgeActionIsAvailable() {
        val available = ScriptActionCatalog.all.filter { it.availability == ActionAvailability.AVAILABLE }.map { it.type }.toSet()
        assertTrue(ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE.all { it in available })
    }
}
