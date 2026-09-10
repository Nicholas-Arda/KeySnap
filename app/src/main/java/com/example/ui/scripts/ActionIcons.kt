package com.example.ui.scripts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoNotDisturbOff
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.LastPage
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.StayCurrentLandscape
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeMute
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.ActionCategory

private val perActionIcons: Map<String, ImageVector> = mapOf(
    // Flashlight
    "toggle_flashlight" to Icons.Outlined.FlashOn,
    "enable_flashlight" to Icons.Outlined.FlashOn,
    "disable_flashlight" to Icons.Outlined.FlashOff,
    "change_flashlight_brightness" to Icons.Outlined.Brightness6,
    // Input
    "input_key_code" to Icons.Outlined.Dialpad,
    "input_key_event" to Icons.Outlined.Keyboard,
    "input_text" to Icons.Outlined.TextFields,
    "tap_screen" to Icons.Outlined.TouchApp,
    "swipe_screen" to Icons.Outlined.SwapHoriz,
    "pinch_screen" to Icons.Outlined.ZoomIn,
    // Apps
    "launch_app" to Icons.Filled.Apps,
    "launch_app_shortcut" to Icons.Outlined.OpenInNew,
    "open_url" to Icons.Outlined.Link,
    "http_request" to Icons.Outlined.Http,
    "shell_command" to Icons.Outlined.Terminal,
    "send_intent" to Icons.Outlined.Code,
    "interact_with_app_element" to Icons.Outlined.TouchApp,
    "launch_voice_assistant" to Icons.Filled.Send,
    "launch_device_assistant" to Icons.Outlined.Android,
    "open_camera" to Icons.Outlined.CameraAlt,
    "open_settings" to Icons.Filled.Settings,
    "force_stop_app" to Icons.Outlined.Block,
    "close_and_clear_app" to Icons.Outlined.DeleteSweep,
    "modify_setting" to Icons.Outlined.Tune,
    // Navigation
    "expand_notification_drawer" to Icons.Outlined.ExpandMore,
    "toggle_notification_drawer" to Icons.Outlined.ExpandMore,
    "expand_quick_settings" to Icons.Outlined.ExpandMore,
    "toggle_quick_settings" to Icons.Outlined.ExpandMore,
    "collapse_status_bar" to Icons.Outlined.ExpandLess,
    "go_back" to Icons.Filled.ArrowBack,
    "go_home" to Icons.Filled.Home,
    "open_recents" to Icons.Outlined.History,
    "open_menu" to Icons.Filled.Menu,
    "go_last_app" to Icons.Outlined.History,
    "toggle_split_screen" to Icons.Outlined.CallSplit,
    "take_screenshot" to Icons.Outlined.Screenshot,
    "move_cursor_to_end" to Icons.Outlined.LastPage,
    // Volume
    "volume_up" to Icons.Outlined.VolumeUp,
    "volume_down" to Icons.Outlined.VolumeDown,
    "volume_mute" to Icons.Outlined.VolumeOff,
    "volume_unmute" to Icons.Outlined.VolumeUp,
    "volume_toggle_mute" to Icons.Outlined.VolumeMute,
    "show_volume_dialog" to Icons.Outlined.VolumeUp,
    "cycle_ringer_mode" to Icons.Outlined.Repeat,
    "change_ringer_mode" to Icons.Outlined.Tune,
    "change_volume_stream" to Icons.Outlined.Tune,
    // Display
    "screen_on" to Icons.Outlined.BrightnessHigh,
    "screen_off" to Icons.Outlined.BrightnessLow,
    "toggle_screen" to Icons.Outlined.BrightnessHigh,
    "toggle_auto_rotate" to Icons.Outlined.ScreenRotation,
    "portrait_mode" to Icons.Outlined.StayCurrentPortrait,
    "landscape_mode" to Icons.Outlined.StayCurrentLandscape,
    "cycle_rotations" to Icons.Outlined.ScreenRotation,
    "change_brightness" to Icons.Outlined.Brightness6,
    "toggle_auto_brightness" to Icons.Outlined.BrightnessAuto,
    "increase_brightness" to Icons.Outlined.BrightnessHigh,
    "decrease_brightness" to Icons.Outlined.BrightnessLow,
    // Media
    "media_play" to Icons.Filled.PlayArrow,
    "media_pause" to Icons.Outlined.Pause,
    "media_play_pause" to Icons.Outlined.PlayCircleOutline,
    "media_next" to Icons.Outlined.SkipNext,
    "media_previous" to Icons.Outlined.SkipPrevious,
    "media_fast_forward" to Icons.Outlined.FastForward,
    "media_rewind" to Icons.Outlined.FastRewind,
    "media_stop" to Icons.Outlined.Stop,
    // Connectivity
    "toggle_wifi" to Icons.Outlined.Wifi,
    "enable_wifi" to Icons.Outlined.Wifi,
    "disable_wifi" to Icons.Outlined.WifiOff,
    "toggle_bluetooth" to Icons.Outlined.Bluetooth,
    "enable_bluetooth" to Icons.Outlined.Bluetooth,
    "disable_bluetooth" to Icons.Outlined.BluetoothDisabled,
    "toggle_mobile_data" to Icons.Outlined.SignalCellularAlt,
    "toggle_nfc" to Icons.Outlined.Nfc,
    "toggle_airplane_mode" to Icons.Outlined.AirplanemodeActive,
    "toggle_location" to Icons.Outlined.LocationOff,
    "toggle_hotspot" to Icons.Outlined.WifiTethering,
    // Keyboard
    "show_keyboard" to Icons.Outlined.Keyboard,
    "hide_keyboard" to Icons.Outlined.KeyboardHide,
    "toggle_keyboard" to Icons.Outlined.Keyboard,
    "switch_keyboard" to Icons.Outlined.Translate,
    "show_keyboard_picker" to Icons.Outlined.Keyboard,
    "text_cut" to Icons.Outlined.ContentCut,
    "text_copy" to Icons.Outlined.ContentCopy,
    "text_paste" to Icons.Outlined.ContentPaste,
    "select_word_at_cursor" to Icons.Outlined.SelectAll,
    // Device
    "lock_device" to Icons.Filled.Lock,
    "secure_lock_device" to Icons.Outlined.LockOpen,
    "power_dialog" to Icons.Outlined.PowerSettingsNew,
    "toggle_dnd" to Icons.Outlined.DoNotDisturbOn,
    "enable_dnd" to Icons.Outlined.DoNotDisturbOn,
    "disable_dnd" to Icons.Outlined.DoNotDisturbOff,
    "open_app_drawer" to Icons.Filled.Apps,
    "dismiss_all_notifications" to Icons.Outlined.NotificationsOff,
    "answer_call" to Icons.Filled.Call,
    "end_call" to Icons.Outlined.CallEnd,
    // Sound
    "play_sound" to Icons.Outlined.MusicNote,
    "text_to_speech" to Icons.Outlined.RecordVoiceOver,
    "vibrate" to Icons.Outlined.Vibration,
    "stop_text_to_speech" to Icons.Outlined.VoiceOverOff,
    // Automation
    "delay" to Icons.Outlined.Timer,
    "repeat_previous" to Icons.Outlined.Repeat,
    "toggle_mapping" to Icons.Outlined.Sync,
    "pause_mapping" to Icons.Outlined.PauseCircleOutline,
    "resume_mapping" to Icons.Outlined.PlayCircleOutline,
)

private val categoryFallbackIcons: Map<ActionCategory, ImageVector> = mapOf(
    ActionCategory.INPUT to Icons.Outlined.Keyboard,
    ActionCategory.APPS to Icons.Filled.Apps,
    ActionCategory.FLASHLIGHT to Icons.Outlined.FlashOn,
    ActionCategory.NAVIGATION to Icons.Outlined.Navigation,
    ActionCategory.VOLUME to Icons.Outlined.VolumeUp,
    ActionCategory.DISPLAY to Icons.Outlined.BrightnessHigh,
    ActionCategory.MEDIA to Icons.Outlined.PlayCircleOutline,
    ActionCategory.CONNECTIVITY to Icons.Outlined.Wifi,
    ActionCategory.KEYBOARD to Icons.Outlined.Keyboard,
    ActionCategory.DEVICE to Icons.Outlined.PhoneAndroid,
    ActionCategory.SOUND to Icons.Outlined.MusicNote,
    ActionCategory.AUTOMATION to Icons.Outlined.AutoAwesome,
)

/** Falls back to a category icon, then to a generic glyph, so a new catalog entry never crashes the picker. */
fun actionIcon(type: String, category: ActionCategory? = null): ImageVector =
    perActionIcons[type]
        ?: category?.let(categoryFallbackIcons::get)
        ?: Icons.Filled.Bolt

/** The glyph representing a whole category, e.g. for the action-picker's category chip rail. */
fun categoryIcon(category: ActionCategory): ImageVector = categoryFallbackIcons[category] ?: Icons.Filled.Bolt

fun actionIconFor(type: String): ImageVector {
    val descriptor = com.example.data.ScriptActionCatalog.descriptor(type)
    return actionIcon(type, descriptor?.category)
}
