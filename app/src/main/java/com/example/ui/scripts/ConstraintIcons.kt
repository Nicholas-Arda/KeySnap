package com.example.ui.scripts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.MobileOff
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.TabletMac
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.AirplanemodeInactive
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DoNotDisturbOff
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.HeadsetOff
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalCellularOff
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.ActionCategory
import com.example.data.ConstraintCategory

private val perConstraintIcons: Map<String, ImageVector> = mapOf(
    // Apps
    "app_in_foreground" to Icons.Filled.Apps,
    "app_not_in_foreground" to Icons.Outlined.AppBlocking,
    "app_playing_media" to Icons.Outlined.MusicNote,
    "app_not_playing_media" to Icons.Outlined.MusicOff,
    // Media
    "no_media_playing" to Icons.Outlined.MusicOff,
    "media_is_playing" to Icons.Outlined.MusicNote,
    // Bluetooth
    "bluetooth_on" to Icons.Outlined.Bluetooth,
    "bluetooth_off" to Icons.Outlined.BluetoothDisabled,
    "bluetooth_device_connected" to Icons.Outlined.BluetoothConnected,
    "bluetooth_device_disconnected" to Icons.AutoMirrored.Outlined.BluetoothSearching,
    // Display
    "screen_on" to Icons.Outlined.BrightnessHigh,
    "screen_off" to Icons.Outlined.MobileOff,
    "screen_orientation" to Icons.Outlined.ScreenRotation,
    "physical_orientation" to Icons.Outlined.StayCurrentPortrait,
    "dark_mode_on" to Icons.Outlined.DarkMode,
    "dark_mode_off" to Icons.Outlined.LightMode,
    // Flashlight
    "flashlight_on" to Icons.Outlined.FlashOn,
    "flashlight_off" to Icons.Outlined.FlashOff,
    // WiFi
    "wifi_on" to Icons.Outlined.Wifi,
    "wifi_off" to Icons.Outlined.WifiOff,
    "connected_to_wifi_network" to Icons.Outlined.WifiTethering,
    "disconnected_from_wifi_network" to Icons.Outlined.WifiOff,
    // Connectivity
    "mobile_data_on" to Icons.Outlined.SignalCellularAlt,
    "mobile_data_off" to Icons.Outlined.SignalCellularOff,
    "airplane_mode_on" to Icons.Outlined.AirplanemodeActive,
    "airplane_mode_off" to Icons.Outlined.AirplanemodeInactive,
    // Keyboard
    "input_method_chosen" to Icons.Outlined.Keyboard,
    "input_method_not_chosen" to Icons.Outlined.KeyboardHide,
    "keyboard_showing" to Icons.Outlined.Keyboard,
    "keyboard_not_showing" to Icons.Outlined.KeyboardHide,
    // Lock
    "device_locked" to Icons.Outlined.Lock,
    "device_unlocked" to Icons.Outlined.LockOpen,
    "lock_screen_showing" to Icons.Outlined.Lock,
    "lock_screen_not_showing" to Icons.Outlined.LockOpen,
    // Phone
    "in_phone_call" to Icons.Filled.Call,
    "not_in_phone_call" to Icons.Outlined.CallEnd,
    "phone_ringing" to Icons.Outlined.PhoneCallback,
    // Sound
    "ringer_normal" to Icons.Outlined.NotificationsActive,
    "ringer_vibrate" to Icons.Outlined.Vibration,
    "ringer_silent" to Icons.Outlined.NotificationsOff,
    "dnd_on" to Icons.Outlined.DoNotDisturbOn,
    "dnd_off" to Icons.Outlined.DoNotDisturbOff,
    "headphones_connected" to Icons.Outlined.Headset,
    "headphones_disconnected" to Icons.Outlined.HeadsetOff,
    // Power
    "charging" to Icons.Outlined.BatteryChargingFull,
    "discharging" to Icons.Outlined.BatteryStd,
    "battery_saver_on" to Icons.Outlined.BatterySaver,
    "battery_saver_off" to Icons.Outlined.BatteryStd,
    "charging_wired" to Icons.Outlined.Cable,
    "charging_wireless" to Icons.Outlined.BatteryChargingFull,
    "battery_above" to Icons.Outlined.BatteryFull,
    "battery_below" to Icons.Outlined.BatteryAlert,
    // Device
    "hinge_closed" to Icons.Outlined.StayCurrentPortrait,
    "hinge_open" to Icons.Outlined.TabletMac,
    "location_on" to Icons.Outlined.LocationOn,
    "location_off" to Icons.Outlined.LocationOff,
    // Time
    "time" to Icons.Outlined.Timer,
    "day_of_week" to Icons.Outlined.DateRange,
)

private val categoryFallbackIcons: Map<ConstraintCategory, ImageVector> = mapOf(
    ConstraintCategory.APPS to Icons.Filled.Apps,
    ConstraintCategory.MEDIA to Icons.Outlined.MusicNote,
    ConstraintCategory.BLUETOOTH to Icons.Outlined.Bluetooth,
    ConstraintCategory.DISPLAY to Icons.Outlined.BrightnessHigh,
    ConstraintCategory.FLASHLIGHT to Icons.Outlined.FlashOn,
    ConstraintCategory.WIFI to Icons.Outlined.Wifi,
    ConstraintCategory.CONNECTIVITY to Icons.Outlined.SignalCellularAlt,
    ConstraintCategory.KEYBOARD to Icons.Outlined.Keyboard,
    ConstraintCategory.LOCK to Icons.Outlined.Lock,
    ConstraintCategory.PHONE to Icons.Filled.Call,
    ConstraintCategory.SOUND to Icons.Outlined.VolumeUp,
    ConstraintCategory.POWER to Icons.Outlined.BatteryStd,
    ConstraintCategory.DEVICE to Icons.Outlined.PhoneAndroid,
    ConstraintCategory.TIME to Icons.Outlined.Timer,
)

/**
 * Tile swatch for a constraint category, borrowed from the solved [com.example.ui.theme.colorFor]
 * action palette rather than adding a second one: those twelve values are tuned together for white
 * glyph contrast and mutual separation, and a fifteenth-colour set would have to be re-solved as a
 * whole. Three of the fifteen categories therefore reuse a swatch, paired with a related meaning
 * and kept far apart in the list.
 */
fun constraintTileCategory(category: ConstraintCategory): ActionCategory = when (category) {
    ConstraintCategory.APPS -> ActionCategory.APPS
    ConstraintCategory.MEDIA -> ActionCategory.MEDIA
    ConstraintCategory.BLUETOOTH -> ActionCategory.CONNECTIVITY
    ConstraintCategory.DISPLAY -> ActionCategory.DISPLAY
    ConstraintCategory.FLASHLIGHT -> ActionCategory.FLASHLIGHT
    ConstraintCategory.WIFI -> ActionCategory.VOLUME
    ConstraintCategory.CONNECTIVITY -> ActionCategory.INPUT
    ConstraintCategory.KEYBOARD -> ActionCategory.KEYBOARD
    ConstraintCategory.LOCK -> ActionCategory.NAVIGATION
    ConstraintCategory.PHONE -> ActionCategory.AUTOMATION
    ConstraintCategory.SOUND -> ActionCategory.SOUND
    ConstraintCategory.POWER -> ActionCategory.APPS
    ConstraintCategory.DEVICE -> ActionCategory.DEVICE
    ConstraintCategory.TIME -> ActionCategory.NAVIGATION
}

/** Rail glyph for a whole category, used by the constraint picker's category chips. */
fun constraintCategoryIcon(category: ConstraintCategory): ImageVector =
    categoryFallbackIcons[category] ?: Icons.Outlined.Timer

/** Falls back to a category icon, then to a generic glyph, so a new catalog entry never crashes the picker. */
fun constraintIcon(type: String, category: ConstraintCategory? = null): ImageVector =
    perConstraintIcons[type]
        ?: category?.let(categoryFallbackIcons::get)
        ?: Icons.Outlined.Timer

fun constraintIconFor(type: String): ImageVector {
    val descriptor = com.example.data.ScriptConstraintCatalog.descriptor(type)
    return constraintIcon(type, descriptor?.category)
}
