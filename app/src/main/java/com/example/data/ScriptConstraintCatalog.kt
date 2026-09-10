package com.example.data

import android.content.res.Resources
import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R

/** Groups the constraint catalog for the picker sheet and matches the reference Key Mapper layout. */
enum class ConstraintCategory(@StringRes val titleRes: Int) {
    APPS(R.string.constraint_category_apps),
    MEDIA(R.string.constraint_category_media),
    BLUETOOTH(R.string.constraint_category_bluetooth),
    DISPLAY(R.string.constraint_category_display),
    FLASHLIGHT(R.string.constraint_category_flashlight),
    WIFI(R.string.constraint_category_wifi),
    CONNECTIVITY(R.string.constraint_category_connectivity),
    KEYBOARD(R.string.constraint_category_keyboard),
    LOCK(R.string.constraint_category_lock),
    PHONE(R.string.constraint_category_phone),
    SOUND(R.string.constraint_category_sound),
    POWER(R.string.constraint_category_power),
    DEVICE(R.string.constraint_category_device),
    TIME(R.string.constraint_category_time),
}

/** Whether a constraint can actually be evaluated yet. Everything else renders greyed out as "Coming soon". */
enum class ConstraintAvailability { AVAILABLE, COMING_SOON }

data class ConstraintDescriptor(
    val type: String,
    @StringRes val titleRes: Int,
    val category: ConstraintCategory,
    val availability: ConstraintAvailability = ConstraintAvailability.COMING_SOON,
)

/**
 * Single source of truth for constraint type ids. A script only fires while every one of its
 * constraints is satisfied. `BridgeConfig` (Java) mirrors the AVAILABLE subset and must never
 * diverge from it, since a script authored here is later matched and executed by the bridge.
 */
object ScriptConstraintCatalog {

    val all: List<ConstraintDescriptor> = listOf(
        // Apps
        ConstraintDescriptor("app_in_foreground", R.string.constraint_app_in_foreground, ConstraintCategory.APPS, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("app_not_in_foreground", R.string.constraint_app_not_in_foreground, ConstraintCategory.APPS, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("app_playing_media", R.string.constraint_app_playing_media, ConstraintCategory.APPS),
        ConstraintDescriptor("app_not_playing_media", R.string.constraint_app_not_playing_media, ConstraintCategory.APPS),

        // Media
        ConstraintDescriptor("no_media_playing", R.string.constraint_no_media_playing, ConstraintCategory.MEDIA, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("media_is_playing", R.string.constraint_media_is_playing, ConstraintCategory.MEDIA, ConstraintAvailability.AVAILABLE),

        // Bluetooth
        ConstraintDescriptor("bluetooth_on", R.string.constraint_bluetooth_on, ConstraintCategory.BLUETOOTH, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("bluetooth_off", R.string.constraint_bluetooth_off, ConstraintCategory.BLUETOOTH, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("bluetooth_device_connected", R.string.constraint_bluetooth_device_connected, ConstraintCategory.BLUETOOTH, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("bluetooth_device_disconnected", R.string.constraint_bluetooth_device_disconnected, ConstraintCategory.BLUETOOTH, ConstraintAvailability.AVAILABLE),

        // Display
        ConstraintDescriptor("screen_on", R.string.constraint_screen_on, ConstraintCategory.DISPLAY, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("screen_off", R.string.constraint_screen_off, ConstraintCategory.DISPLAY, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("screen_orientation", R.string.constraint_screen_orientation, ConstraintCategory.DISPLAY, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("physical_orientation", R.string.constraint_physical_orientation, ConstraintCategory.DISPLAY),
        ConstraintDescriptor("dark_mode_on", R.string.constraint_dark_mode_on, ConstraintCategory.DISPLAY, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("dark_mode_off", R.string.constraint_dark_mode_off, ConstraintCategory.DISPLAY, ConstraintAvailability.AVAILABLE),

        // Flashlight
        ConstraintDescriptor("flashlight_on", R.string.constraint_flashlight_on, ConstraintCategory.FLASHLIGHT, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("flashlight_off", R.string.constraint_flashlight_off, ConstraintCategory.FLASHLIGHT, ConstraintAvailability.AVAILABLE),

        // WiFi
        ConstraintDescriptor("wifi_on", R.string.constraint_wifi_on, ConstraintCategory.WIFI, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("wifi_off", R.string.constraint_wifi_off, ConstraintCategory.WIFI, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("connected_to_wifi_network", R.string.constraint_connected_to_wifi_network, ConstraintCategory.WIFI, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("disconnected_from_wifi_network", R.string.constraint_disconnected_from_wifi_network, ConstraintCategory.WIFI, ConstraintAvailability.AVAILABLE),

        // Connectivity
        ConstraintDescriptor("mobile_data_on", R.string.constraint_mobile_data_on, ConstraintCategory.CONNECTIVITY, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("mobile_data_off", R.string.constraint_mobile_data_off, ConstraintCategory.CONNECTIVITY, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("airplane_mode_on", R.string.constraint_airplane_mode_on, ConstraintCategory.CONNECTIVITY, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("airplane_mode_off", R.string.constraint_airplane_mode_off, ConstraintCategory.CONNECTIVITY, ConstraintAvailability.AVAILABLE),

        // Keyboard
        ConstraintDescriptor("input_method_chosen", R.string.constraint_input_method_chosen, ConstraintCategory.KEYBOARD),
        ConstraintDescriptor("input_method_not_chosen", R.string.constraint_input_method_not_chosen, ConstraintCategory.KEYBOARD),
        ConstraintDescriptor("keyboard_showing", R.string.constraint_keyboard_showing, ConstraintCategory.KEYBOARD, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("keyboard_not_showing", R.string.constraint_keyboard_not_showing, ConstraintCategory.KEYBOARD, ConstraintAvailability.AVAILABLE),

        // Lock
        ConstraintDescriptor("device_locked", R.string.constraint_device_locked, ConstraintCategory.LOCK, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("device_unlocked", R.string.constraint_device_unlocked, ConstraintCategory.LOCK, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("lock_screen_showing", R.string.constraint_lock_screen_showing, ConstraintCategory.LOCK, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("lock_screen_not_showing", R.string.constraint_lock_screen_not_showing, ConstraintCategory.LOCK, ConstraintAvailability.AVAILABLE),

        // Phone
        ConstraintDescriptor("in_phone_call", R.string.constraint_in_phone_call, ConstraintCategory.PHONE, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("not_in_phone_call", R.string.constraint_not_in_phone_call, ConstraintCategory.PHONE, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("phone_ringing", R.string.constraint_phone_ringing, ConstraintCategory.PHONE, ConstraintAvailability.AVAILABLE),

        // Sound
        ConstraintDescriptor("ringer_normal", R.string.constraint_ringer_normal, ConstraintCategory.SOUND, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("ringer_vibrate", R.string.constraint_ringer_vibrate, ConstraintCategory.SOUND, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("ringer_silent", R.string.constraint_ringer_silent, ConstraintCategory.SOUND, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("dnd_on", R.string.constraint_dnd_on, ConstraintCategory.SOUND, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("dnd_off", R.string.constraint_dnd_off, ConstraintCategory.SOUND, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("headphones_connected", R.string.constraint_headphones_connected, ConstraintCategory.SOUND, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("headphones_disconnected", R.string.constraint_headphones_disconnected, ConstraintCategory.SOUND, ConstraintAvailability.AVAILABLE),

        // Power
        ConstraintDescriptor("charging", R.string.constraint_charging, ConstraintCategory.POWER, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("discharging", R.string.constraint_discharging, ConstraintCategory.POWER, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("battery_saver_on", R.string.constraint_battery_saver_on, ConstraintCategory.POWER, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("battery_saver_off", R.string.constraint_battery_saver_off, ConstraintCategory.POWER, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("charging_wired", R.string.constraint_charging_wired, ConstraintCategory.POWER, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("charging_wireless", R.string.constraint_charging_wireless, ConstraintCategory.POWER, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("battery_above", R.string.constraint_battery_above, ConstraintCategory.POWER, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("battery_below", R.string.constraint_battery_below, ConstraintCategory.POWER, ConstraintAvailability.AVAILABLE),

        // Device
        ConstraintDescriptor("hinge_closed", R.string.constraint_hinge_closed, ConstraintCategory.DEVICE),
        ConstraintDescriptor("hinge_open", R.string.constraint_hinge_open, ConstraintCategory.DEVICE),
        ConstraintDescriptor("location_on", R.string.constraint_location_on, ConstraintCategory.DEVICE, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("location_off", R.string.constraint_location_off, ConstraintCategory.DEVICE, ConstraintAvailability.AVAILABLE),

        // Time
        ConstraintDescriptor("time", R.string.constraint_time, ConstraintCategory.TIME, ConstraintAvailability.AVAILABLE),
        ConstraintDescriptor("day_of_week", R.string.constraint_day_of_week, ConstraintCategory.TIME, ConstraintAvailability.AVAILABLE),
    )

    private val byType: Map<String, ConstraintDescriptor> = all.associateBy { it.type }
    val byCategory: Map<ConstraintCategory, List<ConstraintDescriptor>> = all.groupBy { it.category }

    fun descriptor(type: String): ConstraintDescriptor? = byType[type]

    fun isAvailable(type: String): Boolean = byType[type]?.availability == ConstraintAvailability.AVAILABLE

    /**
     * Constraints that can wake a key-less (automation) script: states Android announces with a
     * broadcast or callback, on which `DeviceEventReceiver` re-evaluates — nothing polls. An
     * automation needs at least one of these; the others still gate it. Foreground app, media,
     * keyboard, mobile data and time are left out for that reason; add one only together with its
     * wake-up source.
     */
    val AUTOMATION_TYPES: Set<String> = setOf(
        "screen_on", "screen_off",
        "wifi_on", "wifi_off", "connected_to_wifi_network", "disconnected_from_wifi_network",
        "bluetooth_on", "bluetooth_off", "bluetooth_device_connected", "bluetooth_device_disconnected",
        "charging", "discharging", "charging_wired", "charging_wireless",
        "battery_above", "battery_below", "battery_saver_on", "battery_saver_off",
        "flashlight_on", "flashlight_off",
        "dark_mode_on", "dark_mode_off", "screen_orientation",
        "device_locked", "device_unlocked", "lock_screen_showing", "lock_screen_not_showing",
        "ringer_normal", "ringer_vibrate", "ringer_silent", "dnd_on", "dnd_off",
        "headphones_connected", "headphones_disconnected",
        "airplane_mode_on", "airplane_mode_off",
        "location_on", "location_off",
    )

    fun isAutomation(type: String): Boolean = type in AUTOMATION_TYPES && isAvailable(type)

    /** Titles are localized, so search matches against the strings the user is actually reading. */
    fun search(query: String, resources: Resources): List<ConstraintDescriptor> {
        if (query.isBlank()) return all
        val needle = query.trim()
        return all.filter { resources.getString(it.titleRes).contains(needle, ignoreCase = true) }
    }
}
