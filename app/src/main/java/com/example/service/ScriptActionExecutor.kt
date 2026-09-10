package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.ScriptAction
import com.example.data.ScriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

fun interface ScriptActionHandler {
    suspend fun execute(action: ScriptAction): Boolean
}

/** One finger's straight-line path, in absolute screen pixels. */
internal data class GestureStroke(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

/**
 * Two-finger pinch along the axis through A and B, at any angle. "in" drags both fingers from
 * their points onto the midpoint; "out" starts them together at the midpoint and spreads them to
 * A and B. Pure so it can be unit-tested without Android's Path.
 */
internal fun pinchStrokes(
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
    closingIn: Boolean,
): Pair<GestureStroke, GestureStroke> {
    val midX = (ax + bx) / 2f
    val midY = (ay + by) / 2f
    return if (closingIn) {
        GestureStroke(ax, ay, midX, midY) to GestureStroke(bx, by, midX, midY)
    } else {
        GestureStroke(midX, midY, ax, ay) to GestureStroke(midX, midY, bx, by)
    }
}

/**
 * Resolves a `vibrate` action's raw "duration" param to a valid clamped duration, or `null` if it
 * should be rejected. Mirrors BridgeMain's vibrate handling: unparsable or non-positive durations
 * are rejected outright rather than silently coerced up to a minimum. Pure so it can be
 * unit-tested without a live Vibrator, matching [pinchStrokes]'s pattern.
 */
internal fun resolveVibrateDurationMs(durationParam: String?, maxDurationMs: Long): Long? {
    val parsed = durationParam?.toLongOrNull() ?: return null
    return parsed.takeIf { it > 0L }?.coerceAtMost(maxDurationMs)
}

/**
 * Whitespace-delimited word bounds touching [cursor] in [text], or `null` when the cursor sits in
 * whitespace or the text is empty. A cursor exactly between two words resolves to the word before
 * it, matching double-click-to-select-word semantics in most text editors. Pure so it can be
 * unit-tested without a live AccessibilityNodeInfo, matching [pinchStrokes]'s pattern.
 */
/**
 * Maps a `change_ringer_mode` "mode" param to [AudioManager]'s ringer-mode constant, or `null` for
 * an unrecognized value. Pure so it can be unit-tested without a live AudioManager.
 */
internal fun ringerModeFor(mode: String?): Int? = when (mode) {
    "normal" -> AudioManager.RINGER_MODE_NORMAL
    "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
    "silent" -> AudioManager.RINGER_MODE_SILENT
    else -> null
}

/**
 * The zen filter a DND action should land on: priority-only for on, all-through for off, matching
 * the bridge's `settings put global zen_mode 1/0`. [enabled] null toggles, reading [current] --
 * anything other than ALL counts as "DND is on" so an OEM's alarms-only or none filter turns off
 * rather than flipping to priority. Pure so it can be unit-tested without a live
 * NotificationManager.
 */
internal fun dndFilterFor(current: Int, enabled: Boolean?): Int {
    val turnOn = enabled ?: (current == NotificationManager.INTERRUPTION_FILTER_ALL)
    return if (turnOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
}

/**
 * Normal -> Vibrate -> Silent -> Normal, matching the Sound quick-settings tile's cycle order.
 * Pure so it can be unit-tested without a live AudioManager.
 */
internal fun nextRingerMode(current: Int): Int = when (current) {
    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
    else -> AudioManager.RINGER_MODE_NORMAL
}

/** The `screen_brightness` range the bridge writes, and the step its +/- actions move by. */
internal const val MAX_BRIGHTNESS = 255
internal const val BRIGHTNESS_STEP = 26

/**
 * The `screen_brightness` value a brightness action should land on, mirroring the bridge's
 * `setBrightness` clamp so a script lands identically in both modes. Pure so it can be unit-tested
 * without a live ContentResolver.
 */
internal fun brightnessLevelFor(current: Int, delta: Int): Int = (current + delta).coerceIn(0, MAX_BRIGHTNESS)

/**
 * Portrait -> landscape -> reverse portrait -> reverse landscape -> portrait, matching the bridge's
 * `(user_rotation + 1) % 4`. Pure so it can be unit-tested without a live ContentResolver.
 */
internal fun nextUserRotation(current: Int): Int = (current + 1) % 4

internal fun wordBoundsAtCursor(text: String, cursor: Int): IntRange? {
    if (text.isEmpty()) return null
    val pos = cursor.coerceIn(0, text.length)
    val probe = if (pos > 0 && (pos == text.length || text[pos].isWhitespace())) pos - 1 else pos
    if (probe !in text.indices || text[probe].isWhitespace()) return null
    var start = probe
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    var end = probe
    while (end < text.length - 1 && !text[end + 1].isWhitespace()) end++
    return start..end
}

class ScriptActionExecutor(private val handler: ScriptActionHandler) {

    /**
     * "repeat_previous" has no meaning as a standalone action; it replays whichever real action
     * immediately preceded it in this same list, so it's expanded here rather than passed to
     * [handler]. A leading "repeat_previous" (no previous action yet) fails safe as `false`.
     *
     * [shouldRun] lets a caller run only part of the list while still tracking "previous" from the
     * full, unfiltered list — used when the privileged bridge already owns some of a script's
     * actions and the app must run only the rest, without shifting what "repeat_previous" repeats.
     */
    suspend fun execute(actions: List<ScriptAction>, shouldRun: (ScriptAction) -> Boolean = { true }): List<Boolean> {
        val results = mutableListOf<Boolean>()
        var previous: ScriptAction? = null
        for (action in actions) {
            if (action.type == REPEAT_PREVIOUS_TYPE) {
                val target = previous
                val count = action.params["count"]?.toIntOrNull()?.coerceIn(0, MAX_REPEAT_COUNT) ?: 0
                if (target == null || count <= 0 || !shouldRun(target)) {
                    results.add(false)
                } else {
                    repeat(count) { results.add(handler.execute(target)) }
                }
            } else {
                results.add(if (shouldRun(action)) handler.execute(action) else false)
                previous = action
            }
        }
        return results
    }

    companion object {
        private const val MAX_DELAY_MS = 5_000L
        private const val MAX_REPEAT_COUNT = 20
        private const val REPEAT_PREVIOUS_TYPE = "repeat_previous"

        /**
         * Every catalog entry that is not AVAILABLE is deliberately inert here rather than
         * throwing, so an old persisted script referencing a not-yet-implemented action fails safe.
         */
        fun android(context: Context) = ScriptActionExecutor { action ->
            val ctx = context.applicationContext
            when (action.type) {
                "toggle_flashlight" -> FlashlightController.toggle(ctx)
                "enable_flashlight" -> FlashlightController.setTorchMode(ctx, true)
                "disable_flashlight" -> FlashlightController.setTorchMode(ctx, false)
                // Arbitrary key-code injection needs INJECT_EVENTS, a signature permission no app
                // holds and no Accessibility API grants; only reachable through Advanced Mode/the
                // bridge's shell `input keyevent`.
                "input_key_code" -> false
                "input_text" -> inputText(action.params["text"])
                "tap_screen" -> tapScreen(action.params)
                "swipe_screen" -> swipeScreen(action.params)
                "pinch_screen" -> pinchScreen(action.params)
                "media_play_pause" -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "media_play" -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY)
                "media_pause" -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE)
                "media_next" -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
                "media_previous" -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "media_fast_forward" -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                "media_rewind" -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_REWIND)
                "media_stop" -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_STOP)
                "volume_toggle_mute" -> toggleMute(ctx)
                "volume_mute" -> setMuted(ctx, true)
                "volume_unmute" -> setMuted(ctx, false)
                "volume_up" -> adjustVolume(ctx, AudioManager.ADJUST_RAISE)
                "volume_down" -> adjustVolume(ctx, AudioManager.ADJUST_LOWER)
                "show_volume_dialog" -> adjustVolume(ctx, AudioManager.ADJUST_SAME)
                "change_volume_stream" -> changeVolumeStream(ctx, action.params)
                "cycle_ringer_mode" -> cycleRingerMode(ctx)
                "change_ringer_mode" -> changeRingerMode(ctx, action.params["mode"])
                "go_back" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "go_home" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "open_recents" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "expand_notification_drawer" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "expand_quick_settings" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                "collapse_status_bar" ->
                    KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                "take_screenshot" ->
                    KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                "toggle_split_screen" ->
                    KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                "move_cursor_to_end" -> KeyMapperAccessibilityService.moveFocusedCursorToEnd()
                // No AccessibilityService global action and no app-UID key-injection path exists for
                // MENU; only reachable through Advanced Mode/the bridge's shell `input keyevent`.
                "open_menu" -> false
                // Radio/location power state has had no app-UID API since Android 10+ (WifiManager
                // .setWifiEnabled and friends are no-ops for non-privileged apps); only reachable
                // through Advanced Mode/the bridge's shell `svc`/`cmd` commands.
                "toggle_wifi", "enable_wifi", "disable_wifi",
                "toggle_mobile_data", "toggle_airplane_mode", "toggle_location" -> false
                // BluetoothAdapter.enable()/disable() have been no-ops for non-privileged apps since
                // Android 13; only reachable through Advanced Mode/the bridge's shell `svc bluetooth`.
                "toggle_bluetooth", "enable_bluetooth", "disable_bluetooth" -> false
                "lock_device" ->
                    KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                "power_dialog" ->
                    KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                "toggle_dnd" -> setDndEnabled(ctx, null)
                "enable_dnd" -> setDndEnabled(ctx, true)
                "disable_dnd" -> setDndEnabled(ctx, false)
                "text_cut" -> KeyMapperAccessibilityService.performFocusedEditAction(AccessibilityNodeInfo.ACTION_CUT)
                "text_copy" -> KeyMapperAccessibilityService.performFocusedEditAction(AccessibilityNodeInfo.ACTION_COPY)
                "text_paste" -> KeyMapperAccessibilityService.performFocusedEditAction(AccessibilityNodeInfo.ACTION_PASTE)
                "show_keyboard_picker" -> showInputMethodPicker(ctx)
                "select_word_at_cursor" -> KeyMapperAccessibilityService.selectFocusedWordAtCursor()
                // Cycling the enabled IME list needs WRITE_SECURE_SETTINGS, which shell holds and
                // an app never can; only reachable through Advanced Mode/the bridge's `ime` command.
                "switch_keyboard" -> false
                "launch_app" -> launchApp(ctx, action.params["packageName"])
                "open_url" -> openUrl(ctx, action.params["url"])
                "launch_voice_assistant" -> startActivitySafely(ctx, Intent(Intent.ACTION_VOICE_COMMAND))
                "launch_device_assistant" -> startActivitySafely(ctx, Intent(Intent.ACTION_ASSIST))
                "open_camera" -> startActivitySafely(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                "open_settings" -> startActivitySafely(ctx, Intent(Settings.ACTION_SETTINGS))
                "send_intent" -> sendIntent(ctx, action.params)
                "http_request" -> httpRequest(action.params["method"], action.params["url"], action.params["body"])
                "shell_command" -> runShellCommand(action.params["command"])
                // Neither killing another app's process nor writing system/secure/global settings
                // has an app-UID API since Android 10+ (FORCE_STOP_PACKAGES and WRITE_SECURE_SETTINGS
                // are signature permissions); only reachable through Advanced Mode/the bridge's shell
                // `am force-stop`/`settings put`.
                "force_stop_app", "modify_setting" -> false
                // Forcing the screen on or off needs privilege an app never holds (there is no
                // public "turn screen off" API short of Device Admin); only reachable through
                // Advanced Mode/the bridge's shell `input keyevent`.
                "screen_on", "screen_off", "toggle_screen" -> false
                "toggle_auto_rotate" -> toggleSystemFlag(ctx, Settings.System.ACCELEROMETER_ROTATION)
                "portrait_mode" -> lockRotation(ctx, 0)
                "landscape_mode" -> lockRotation(ctx, 1)
                "cycle_rotations" -> lockRotation(ctx, nextUserRotation(systemInt(ctx, Settings.System.USER_ROTATION, 0)))
                "change_brightness" -> setBrightness(ctx, action.params["value"]?.trim()?.toIntOrNull())
                "toggle_auto_brightness" -> toggleSystemFlag(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE)
                "increase_brightness" -> adjustBrightness(ctx, BRIGHTNESS_STEP)
                "decrease_brightness" -> adjustBrightness(ctx, -BRIGHTNESS_STEP)
                "play_sound" -> playSound(ctx, action.params["uri"])
                "text_to_speech" -> TextToSpeechController.speak(ctx, action.params["text"])
                "stop_text_to_speech" -> TextToSpeechController.stop()
                "vibrate" -> vibrate(ctx, action.params["duration"])
                "toggle_mapping" -> setMappingEnabled(ctx, null)
                "pause_mapping" -> setMappingEnabled(ctx, false)
                "resume_mapping" -> setMappingEnabled(ctx, true)
                "delay" -> {
                    delay(action.params["duration"]?.toLongOrNull()?.coerceIn(0L, MAX_DELAY_MS) ?: 0L)
                    true
                }
                else -> false
            }
        }

        private fun dispatchMediaKey(context: Context, keyCode: Int): Boolean {
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            val eventTime = android.os.SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
            return true
        }

        private fun toggleMute(context: Context): Boolean {
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            return true
        }

        private fun setMuted(context: Context, muted: Boolean): Boolean {
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0,
            )
            return true
        }

        /** Adjusts whichever stream is currently active, matching physical volume-button behavior.
         *  [AudioManager.ADJUST_SAME] with [AudioManager.FLAG_SHOW_UI] shows the dialog without
         *  changing the level, which is how `show_volume_dialog` reuses this. */
        private fun adjustVolume(context: Context, direction: Int): Boolean {
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            audio.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
            return true
        }

        private val VOLUME_STREAM_IDS = mapOf(
            "ring" to AudioManager.STREAM_RING,
            "media" to AudioManager.STREAM_MUSIC,
            "alarm" to AudioManager.STREAM_ALARM,
            "notification" to AudioManager.STREAM_NOTIFICATION,
        )

        private fun changeVolumeStream(context: Context, params: Map<String, String>): Boolean {
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            val streamId = VOLUME_STREAM_IDS[params["stream"]] ?: return false
            // Ring/notification crossing to or from silent needs DND access, or the OS throws a
            // SecurityException; require it up front like changeRingerMode/cycleRingerMode do.
            if (streamId == AudioManager.STREAM_RING || streamId == AudioManager.STREAM_NOTIFICATION) {
                if (!hasNotificationPolicyAccess(context)) return false
            }
            val value = params["value"]?.toIntOrNull() ?: return false
            val clamped = value.coerceIn(0, audio.getStreamMaxVolume(streamId))
            audio.setStreamVolume(streamId, clamped, AudioManager.FLAG_SHOW_UI)
            return true
        }

        /** Silent/vibrate need the user to have granted Do Not Disturb access outside the app. */
        private fun hasNotificationPolicyAccess(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return manager.isNotificationPolicyAccessGranted
        }

        /**
         * Mirrors the bridge's `settings put global zen_mode 1/0`: priority-only filter for on,
         * all-through for off. Needs the same user-granted DND access as the ringer-mode actions,
         * and fails safe the same way when it hasn't been granted. [enabled] null means toggle.
         */
        private fun setDndEnabled(context: Context, enabled: Boolean?): Boolean {
            if (!hasNotificationPolicyAccess(context)) return false
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            manager.setInterruptionFilter(dndFilterFor(manager.currentInterruptionFilter, enabled))
            return true
        }

        private fun changeRingerMode(context: Context, mode: String?): Boolean {
            if (!hasNotificationPolicyAccess(context)) return false
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            val target = ringerModeFor(mode) ?: return false
            audio.ringerMode = target
            return true
        }

        private fun cycleRingerMode(context: Context): Boolean {
            if (!hasNotificationPolicyAccess(context)) return false
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            audio.ringerMode = nextRingerMode(audio.ringerMode)
            return true
        }

        /**
         * Writing rotation/brightness needs the WRITE_SETTINGS special permission, which the user
         * grants outside the app in "Modify system settings". Same shape as
         * [hasNotificationPolicyAccess]: without it the action fails cleanly instead of throwing,
         * and the action editor's requirement badge offers the grant. In Advanced Mode the bridge does these
         * from the shell UID instead and never reaches here.
         */
        private fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

        private fun systemInt(context: Context, key: String, fallback: Int): Int =
            Settings.System.getInt(context.contentResolver, key, fallback)

        /** Flips a 0/1 `Settings.System` flag, treating anything but 1 as off like the bridge does. */
        private fun toggleSystemFlag(context: Context, key: String): Boolean {
            if (!canWriteSettings(context)) return false
            return Settings.System.putInt(context.contentResolver, key, if (systemInt(context, key, 0) == 1) 0 else 1)
        }

        /** Locking rotation always disables auto-rotate first, matching the quick-settings tile. */
        private fun lockRotation(context: Context, rotation: Int): Boolean {
            if (!canWriteSettings(context)) return false
            val resolver = context.contentResolver
            return Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) and
                Settings.System.putInt(resolver, Settings.System.USER_ROTATION, rotation)
        }

        /** A manual level implies leaving auto-brightness, or the system overwrites it right back. */
        private fun setBrightness(context: Context, value: Int?): Boolean {
            if (value == null || !canWriteSettings(context)) return false
            val resolver = context.contentResolver
            return Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            ) and Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightnessLevelFor(value, 0))
        }

        /** The bridge falls back to mid-scale when the current level is unreadable; match that. */
        private fun adjustBrightness(context: Context, delta: Int): Boolean =
            setBrightness(context, brightnessLevelFor(systemInt(context, Settings.System.SCREEN_BRIGHTNESS, 128), delta))

        private fun showInputMethodPicker(context: Context): Boolean {
            val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java) ?: return false
            imm.showInputMethodPicker()
            return true
        }

        private const val TAP_DURATION_MS = 50L
        private const val MAX_TAP_COUNT = 20
        private const val DEFAULT_TAP_INTERVAL_MS = 100L
        private const val DEFAULT_SWIPE_DURATION_MS = 300L
        private const val DEFAULT_PINCH_DURATION_MS = 300L
        private const val MAX_GESTURE_DURATION_MS = 5_000L

        private fun inputText(text: String?): Boolean {
            if (text.isNullOrEmpty()) return false
            return KeyMapperAccessibilityService.setFocusedText(text)
        }

        private suspend fun tapScreen(params: Map<String, String>): Boolean {
            val x = params["x"]?.toFloatOrNull() ?: return false
            val y = params["y"]?.toFloatOrNull() ?: return false
            val count = params["count"]?.toIntOrNull()?.coerceIn(1, MAX_TAP_COUNT) ?: 1
            val interval = params["interval"]?.toLongOrNull()?.coerceIn(0L, MAX_DELAY_MS)
                ?: DEFAULT_TAP_INTERVAL_MS
            repeat(count) { index ->
                if (index > 0) delay(interval)
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
                    .build()
                // A failed dispatch means the service is gone; stop rather than spin out the rest.
                if (!KeyMapperAccessibilityService.performGesture(gesture)) return false
            }
            return true
        }

        private fun swipeScreen(params: Map<String, String>): Boolean {
            val x1 = params["x1"]?.toFloatOrNull() ?: return false
            val y1 = params["y1"]?.toFloatOrNull() ?: return false
            val x2 = params["x2"]?.toFloatOrNull() ?: return false
            val y2 = params["y2"]?.toFloatOrNull() ?: return false
            val duration = params["duration"]?.toLongOrNull()?.coerceIn(1L, MAX_GESTURE_DURATION_MS)
                ?: DEFAULT_SWIPE_DURATION_MS
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            return KeyMapperAccessibilityService.performGesture(gesture)
        }

        /**
         * Two-finger pinch along the axis through (x1, y1) and (x2, y2). Needs Accessibility's
         * concurrent multi-stroke gestures; no shell `input` primitive exists for multi-touch, so
         * this has no bridge counterpart.
         */
        private fun pinchScreen(params: Map<String, String>): Boolean {
            val ax = params["x1"]?.toFloatOrNull() ?: return false
            val ay = params["y1"]?.toFloatOrNull() ?: return false
            val bx = params["x2"]?.toFloatOrNull() ?: return false
            val by = params["y2"]?.toFloatOrNull() ?: return false
            val duration = params["duration"]?.toLongOrNull()?.coerceIn(1L, MAX_GESTURE_DURATION_MS)
                ?: DEFAULT_PINCH_DURATION_MS
            val (first, second) = pinchStrokes(ax, ay, bx, by, closingIn = params["pinchType"] == "in")
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(first.toPath(), 0, duration))
                .addStroke(GestureDescription.StrokeDescription(second.toPath(), 0, duration))
                .build()
            return KeyMapperAccessibilityService.performGesture(gesture)
        }

        private fun GestureStroke.toPath(): Path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }

        private fun startActivitySafely(context: Context, intent: Intent): Boolean = try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }

        private fun launchApp(context: Context, packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return false
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            return startActivitySafely(context, intent)
        }

        private fun openUrl(context: Context, url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return startActivitySafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        /** "key=value" pairs separated by ';', each added as a String extra. */
        private fun parseExtras(raw: String): Map<String, String> = raw.split(';')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                pair.substring(0, index).trim() to pair.substring(index + 1).trim()
            }
            .toMap()

        private fun sendIntent(context: Context, params: Map<String, String>): Boolean {
            val action = params["action"]
            if (action.isNullOrBlank()) return false
            return try {
                val intent = Intent(action)
                params["uri"]?.takeIf { it.isNotBlank() }?.let { intent.data = Uri.parse(it) }
                params["extras"]?.takeIf { it.isNotBlank() }?.let { raw ->
                    parseExtras(raw).forEach { (key, value) -> intent.putExtra(key, value) }
                }
                context.sendBroadcast(intent)
                true
            } catch (e: Exception) {
                false
            }
        }

        private suspend fun httpRequest(method: String?, url: String?, body: String?): Boolean {
            if (method.isNullOrBlank() || url.isNullOrBlank()) return false
            return withContext(Dispatchers.IO) {
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = method.uppercase()
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    if (!body.isNullOrEmpty() && method.uppercase() in setOf("POST", "PUT")) {
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val code = connection.responseCode
                    connection.disconnect()
                    code in 200..399
                } catch (e: Exception) {
                    false
                }
            }
        }

        private suspend fun runShellCommand(command: String?): Boolean {
            if (command.isNullOrBlank()) return false
            return withContext(Dispatchers.IO) {
                try {
                    val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
                    process.waitFor() == 0
                } catch (e: Exception) {
                    false
                }
            }
        }

        private suspend fun playSound(context: Context, uri: String?): Boolean {
            if (uri.isNullOrBlank()) return false
            return withContext(Dispatchers.IO) {
                try {
                    val player = MediaPlayer()
                    player.setOnCompletionListener { it.release() }
                    player.setOnErrorListener { mp, _, _ -> mp.release(); true }
                    player.setDataSource(context, Uri.parse(uri))
                    player.prepare()
                    player.start()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        private fun vibrate(context: Context, durationParam: String?): Boolean {
            val duration = resolveVibrateDurationMs(durationParam, MAX_DELAY_MS) ?: return false
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return false
            return try {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                true
            } catch (e: Exception) {
                false
            }
        }

        /** Null toggles the current state; the bridge has no path to write this back, so it's app-only. */
        private fun setMappingEnabled(context: Context, enabled: Boolean?): Boolean {
            val repository = ScriptRepository.getInstance(context)
            repository.setGloballyEnabled(enabled ?: !repository.globallyEnabled.value)
            return true
        }
    }
}
