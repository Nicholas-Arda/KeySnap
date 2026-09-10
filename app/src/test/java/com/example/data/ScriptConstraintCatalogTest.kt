package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScriptConstraintCatalogTest {

    private val res = RuntimeEnvironment.getApplication().resources

    @Test fun everyTypeIdIsUnique() {
        val types = ScriptConstraintCatalog.all.map { it.type }
        assertEquals(types.size, types.distinct().size)
    }

    /**
     * Pinned deliberately: BridgeConfig's CONSTRAINT_* constants and
     * com.example.bridge.ConstraintEvaluator mirror this exact set, so a constraint authored in
     * the app is evaluated identically by the detached bridge. Changing this set means changing
     * both sides.
     */
    @Test fun theAvailableSetIsExactlyWhatBothEvaluatorsImplement() {
        val available = ScriptConstraintCatalog.all.filter { it.availability == ConstraintAvailability.AVAILABLE }.map { it.type }
        assertEquals(
            setOf(
                "app_in_foreground", "app_not_in_foreground",
                "media_is_playing", "no_media_playing",
                "bluetooth_on", "bluetooth_off",
                "bluetooth_device_connected", "bluetooth_device_disconnected",
                "screen_on", "screen_off",
                "screen_orientation",
                "dark_mode_on", "dark_mode_off",
                "flashlight_on", "flashlight_off",
                "wifi_on", "wifi_off",
                "connected_to_wifi_network", "disconnected_from_wifi_network",
                "mobile_data_on", "mobile_data_off",
                "airplane_mode_on", "airplane_mode_off",
                "keyboard_showing", "keyboard_not_showing",
                "device_locked", "device_unlocked",
                "lock_screen_showing", "lock_screen_not_showing",
                "in_phone_call", "not_in_phone_call",
                "phone_ringing",
                "ringer_normal", "ringer_vibrate", "ringer_silent",
                "dnd_on", "dnd_off",
                "headphones_connected", "headphones_disconnected",
                "charging", "discharging",
                "battery_saver_on", "battery_saver_off",
                "charging_wired", "charging_wireless",
                "battery_above", "battery_below",
                "location_on", "location_off",
                "time", "day_of_week",
            ),
            available.toSet(),
        )
    }

    @Test fun theComingSoonSetIsExactlyWhatNeitherEvaluatorImplements() {
        val comingSoon = ScriptConstraintCatalog.all.filter { it.availability == ConstraintAvailability.COMING_SOON }.map { it.type }
        assertEquals(
            setOf(
                "app_playing_media", "app_not_playing_media",
                "physical_orientation",
                "input_method_chosen", "input_method_not_chosen",
                "hinge_closed", "hinge_open",
            ),
            comingSoon.toSet(),
        )
    }

    @Test fun everyAutomationTypeIsAnAvailableConstraint() {
        ScriptConstraintCatalog.AUTOMATION_TYPES.forEach { type ->
            assertTrue("$type is not an AVAILABLE constraint", ScriptConstraintCatalog.isAvailable(type))
            assertTrue(ScriptConstraintCatalog.isAutomation(type))
        }
        // No wake-up source: foreground app, media, keyboard, mobile data and time only gate.
        listOf("app_in_foreground", "media_is_playing", "keyboard_showing", "mobile_data_on", "time", "day_of_week").forEach {
            assertFalse("$it must not be an automation trigger", ScriptConstraintCatalog.isAutomation(it))
        }
    }

    @Test fun everyCategoryHasAtLeastOneEntry() {
        ConstraintCategory.entries.forEach { category ->
            assertTrue("No catalog entries for $category", ScriptConstraintCatalog.byCategory[category].orEmpty().isNotEmpty())
        }
    }

    @Test fun searchIsCaseInsensitiveAndMatchesTitleSubstring() {
        val results = ScriptConstraintCatalog.search("wifi", res)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { res.getString(it.titleRes).contains("wifi", ignoreCase = true) })
    }

    @Test fun blankSearchReturnsEverything() {
        assertEquals(ScriptConstraintCatalog.all.size, ScriptConstraintCatalog.search("", res).size)
        assertEquals(ScriptConstraintCatalog.all.size, ScriptConstraintCatalog.search("   ", res).size)
    }

    @Test fun descriptorLooksUpByType() {
        val descriptor = ScriptConstraintCatalog.descriptor("wifi_on")
        assertEquals("wifi_on", descriptor?.type)
        assertEquals(ConstraintCategory.WIFI, descriptor?.category)
    }

    @Test fun isAvailableMatchesDescriptorAvailability() {
        assertTrue(ScriptConstraintCatalog.isAvailable("screen_on"))
        assertTrue(!ScriptConstraintCatalog.isAvailable("hinge_open"))
        assertTrue(!ScriptConstraintCatalog.isAvailable("not_a_real_constraint"))
    }
}
