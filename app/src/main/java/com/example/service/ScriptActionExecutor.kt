package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.bridge.AlarmIntents
import com.example.bridge.RingerBuzz
import com.example.data.AppLocale
import com.example.data.OemActions
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.data.TriggerPressType
import com.example.bridge.BridgeProtocol
import com.example.bridge.BrightnessSettings
import com.example.bridge.ActionPayloads
import com.example.bridge.ActionTestResult
import com.example.bridge.HttpAction
import com.example.bridge.ShellKeyInjector
import com.example.data.ScriptRepository
import com.example.mode.ModeHooks
import com.example.service.bridge.BridgeController
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

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
 * rather than flipping to priority. [type] is the action's "filter" param ("alarms", "none", else
 * priority), mirrored by `BridgeMain.dndArgument`. Pure so it can be unit-tested without a live
 * NotificationManager.
 */
internal fun dndFilterFor(current: Int, enabled: Boolean?, type: String? = null): Int {
    val turnOn = enabled ?: (current == NotificationManager.INTERRUPTION_FILTER_ALL)
    if (!turnOn) return NotificationManager.INTERRUPTION_FILTER_ALL
    return when (type) {
        "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
        "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
        else -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
    }
}

/**
 * Kept apart from the setup, pairing and run-failure channels so the user can mute shortcut
 * notifications alone.
 */
internal const val SHORTCUT_NOTIFICATION_CHANNEL_ID = "shortcut_notifications"

/**
 * Every shortcut notification is posted under this tag, so its ids can never replace the app's
 * untagged setup/pairing notifications or [RunFailureNotifier]'s tagged ones.
 */
private const val SHORTCUT_NOTIFICATION_TAG = "shortcut_notification"

/**
 * True when a shortcut's notification can be seen: the app's notifications are on (POST_NOTIFICATIONS
 * granted on 13+) and the user has not switched the shortcut channel off. Shared with the run log,
 * so a failed "Show notification" says why.
 */
internal fun shortcutNotificationsAllowed(manager: NotificationManager): Boolean =
    manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(SHORTCUT_NOTIFICATION_CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

/**
 * Portrait -> landscape -> reverse portrait -> reverse landscape -> portrait, matching the bridge's
 * `(user_rotation + 1) % 4`. Pure so it can be unit-tested without a live ContentResolver.
 */
internal fun nextUserRotation(current: Int): Int = (current + 1) % 4

/**
 * A `set_wallpaper` "target" param to [WallpaperManager]'s which-flags, or `null` for an unknown
 * value. Pure so it can be unit-tested without a live WallpaperManager.
 */
internal fun wallpaperFlagsFor(target: String?): Int? = when (target) {
    "home" -> WallpaperManager.FLAG_SYSTEM
    "lock" -> WallpaperManager.FLAG_LOCK
    "both" -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    else -> null
}

/**
 * The param a reply carries for one run only: the key of the notification that started the run.
 * Never persisted, never shared, and any stored copy is replaced, so a saved or imported shortcut
 * cannot aim a reply at a notification of its choosing.
 */
internal const val TRIGGER_NOTIFICATION_PARAM = "_triggerNotificationKey"

internal fun ScriptAction.withTriggerNotification(key: String?): ScriptAction {
    if (type != "reply_to_notification") return this
    val clean = params - TRIGGER_NOTIFICATION_PARAM
    return copy(params = if (key == null) clean else clean + (TRIGGER_NOTIFICATION_PARAM to key))
}

/** Apps that open a chat from a wa.me link with the text in it. Package names only; never shown. */
private val LINK_MESSAGING_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * How `send_message` opens the chat: [action] on [uri] inside [packageName], with the text as the
 * [textExtra] extra when there is one. The text is passed as written; only the link URL-encodes it.
 * Pure, so the editor can print it before it runs and tests can pin every branch.
 */
internal data class MessageLaunch(
    val action: String,
    val uri: String?,
    val textExtra: String?,
    val text: String,
    val packageName: String,
    /** The normalized number this opens a chat with; empty when the app picks the person itself. */
    val recipient: String = "",
) {
    /**
     * True when this opens one person's chat. A launch without a number, or a plain text share,
     * opens the app's own picker instead: there is no chat to send into, so "Send automatically"
     * never applies to it.
     */
    val opensChat: Boolean get() = recipient.isNotEmpty()

    fun toIntent(): Intent = Intent(action).setPackage(packageName).apply {
        if (uri != null) data = Uri.parse(uri) else type = "text/plain"
        if (textExtra != null) putExtra(textExtra, text)
    }

    /** The editor's "What will run" line: the literal link or intent, before it ever runs. */
    fun describe(): String = buildString {
        append(action.removePrefix("android.intent.action."))
        append(' ').append(uri ?: "text/plain")
        if (textExtra != null) append(" · ").append(textExtra.removePrefix("android.intent.extra.")).append("=“").append(text).append('”')
        append(" → ").append(packageName)
    }
}

/** E.164 when Android can parse it for the SIM's country, else the digits, keeping a leading +. */
internal fun normalizePhoneNumber(number: String, countryIso: String?): String {
    val trimmed = number.trim()
    if (trimmed.isEmpty()) return ""
    val e164 = countryIso?.takeIf { it.isNotBlank() }?.let { PhoneNumberUtils.formatNumberToE164(trimmed, it.uppercase()) }
    return e164 ?: ((if (trimmed.startsWith("+")) "+" else "") + trimmed.filter { it.isDigit() })
}

/**
 * A wa.me link for the apps that take one, an `smsto:` chat for an app that opens those, and a plain
 * text share otherwise (the recipient is then chosen inside the app). An empty [number] leaves the
 * recipient to the app in every branch. Null for a link app given a number without its country code:
 * the link reads the leading digits as one, which could open a stranger's chat.
 */
internal fun messageLaunch(packageName: String, number: String, text: String, countryIso: String?, opensSmsTo: Boolean): MessageLaunch? {
    val to = normalizePhoneNumber(number, countryIso)
    return when {
        packageName in LINK_MESSAGING_PACKAGES -> {
            if (to.isNotEmpty() && !to.startsWith("+")) return null
            val encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
            MessageLaunch(Intent.ACTION_VIEW, "https://wa.me/${to.filter { it.isDigit() }}?text=$encoded", null, text, packageName, to)
        }
        opensSmsTo -> MessageLaunch(Intent.ACTION_SENDTO, "smsto:$to", "sms_body", text, packageName, to)
        else -> MessageLaunch(Intent.ACTION_SEND, null, Intent.EXTRA_TEXT, text, packageName)
    }
}

/** Whether this send_message asks the bridge to tap Send: switched on, and [launch] opens a chat. */
internal fun autoSends(params: Map<String, String>, launch: MessageLaunch): Boolean =
    params["autoSend"] == "true" && launch.opensChat

/** The [MessageLaunch] for a `send_message` action on this phone, or null while its app or text is missing. */
internal fun messageLaunchFor(context: Context, params: Map<String, String>): MessageLaunch? {
    val packageName = params["packageName"]?.trim().orEmpty()
    val text = params["text"].orEmpty()
    if (packageName.isEmpty() || text.isBlank()) return null
    val countryIso = runCatching { context.getSystemService(TelephonyManager::class.java)?.simCountryIso }.getOrNull()
    val opensSmsTo = packageName == runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull() ||
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).setPackage(packageName), 0).isNotEmpty()
    return messageLaunch(packageName, params["number"].orEmpty(), text, countryIso, opensSmsTo)
}

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

/**
 * Whether Android lets this app open an activity right now. Since Android 10 a start from the
 * background is dropped silently (BAL_BLOCK, no exception), so without one of the exemptions the
 * app can see -- a visible KeySnap screen, its bound Accessibility service, or "Display over other
 * apps", or a home-screen widget tap still inside its grant ([WidgetLaunchGrant]) -- the action must
 * report failure, not success.
 */
internal fun canStartActivityFromBackground(
    appVisible: Boolean,
    accessibilityBound: Boolean,
    canDrawOverlays: Boolean,
    widgetGrant: Boolean = false,
): Boolean = appVisible || accessibilityBound || canDrawOverlays || widgetGrant

/**
 * A widget tap's broadcast lets this process open activities (logcat BAL_ALLOW_TOKEN), a grant the
 * app cannot query; a Quick Settings tile tap carries none (BAL_BLOCK). The grant is the broadcast's
 * background-start token on this process: Android 14+ (BroadcastQueueModernImpl.dispatchReceivers,
 * MSG_BG_ACTIVITY_START_TIMEOUT) adds it when it hands the broadcast to us and removes it
 * ALLOW_BG_ACTIVITY_START_TIMEOUT later, whether or not the receiver has finished; Android 11-13's
 * BroadcastQueueImpl.finishReceiverLocked removes it at the same moment when the receiver finishes
 * sooner, as [com.example.surface.ScriptRunReceiver] does. That timeout (BroadcastConstants) is
 * 10 s times ro.hw_timeout_multiplier, which is 1 on phones and higher on some emulators, and the
 * app sees neither the multiplier nor the hand-over time, so the window is the shortest one: 10 s
 * from onReceive, less [DISPATCH_MARGIN_MS] for the hand-over (longer on a cold start). A launch
 * after it reports failure even where the token lasts longer (an emulator with a multiplier), never
 * success where Android blocks it.
 * ponytail: process-wide, like the grant itself; a key run launching while a widget run's window is
 * open is reported OK too, which is what Android lets through.
 */
internal object WidgetLaunchGrant {
    private const val BAL_TOKEN_TIMEOUT_MS = 10_000L
    private const val DISPATCH_MARGIN_MS = 1_000L
    const val WINDOW_MS = BAL_TOKEN_TIMEOUT_MS - DISPATCH_MARGIN_MS

    @Volatile private var until = 0L

    fun open(now: Long) {
        until = maxOf(until, now + WINDOW_MS)
    }

    fun active(now: Long): Boolean = now < until
}

/**
 * [first] is null only when its start threw, so nothing opened; a start that went through, even one
 * judged blocked, never runs [fallback], or a grant the app cannot see opens two screens.
 */
internal inline fun startWithFallback(first: () -> Boolean?, fallback: () -> Boolean): Boolean = first() ?: fallback()

/** The field's new text after typing, and where the cursor lands. */
internal data class TypedText(val text: String, val cursor: Int)

/**
 * Types [typed] into [current] the way a keyboard does: over the selection, or at the cursor, or at
 * the end when no selection is reported (-1). Pure so it can be unit-tested without a live node.
 */
internal fun typeAtSelection(current: String, selectionStart: Int, selectionEnd: Int, typed: String): TypedText {
    val start = if (selectionStart < 0 || selectionEnd < 0) current.length else minOf(selectionStart, selectionEnd).coerceIn(0, current.length)
    val end = if (selectionStart < 0 || selectionEnd < 0) current.length else maxOf(selectionStart, selectionEnd).coerceIn(0, current.length)
    return TypedText(current.substring(0, start) + typed + current.substring(end), start + typed.length)
}

class ScriptActionExecutor(
    private val singleActionTest: (suspend (ScriptAction) -> ActionTestResult)? = null,
    private val handler: ScriptActionHandler,
) {

    /** A real execution, without chain expansion, counters, haptics, run logs or diagnostics. */
    suspend fun runSingleActionForTest(action: ScriptAction): ActionTestResult {
        val start = System.nanoTime()
        return try {
            singleActionTest?.invoke(action) ?: ActionTestResult.outcome(handler.execute(action), start)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ActionTestResult.outcome(false, start)
        }
    }

    /**
     * "repeat_previous" has no meaning as a standalone action; it replays whichever real action
     * immediately preceded it in this same list, so it's expanded here rather than passed to
     * [handler]. A leading "repeat_previous" (no previous action yet) fails safe as `false`.
     *
     * [shouldRun] lets a caller run only part of the list while still tracking "previous" from the
     * full, unfiltered list — used when the privileged bridge already owns some of a script's
     * actions and the app must run only the rest, without shifting what "repeat_previous" repeats.
     */
    suspend fun execute(
        actions: List<ScriptAction>,
        notificationKey: String? = null,
        diagnosticScriptId: Int = 0,
        diagnosticPressType: TriggerPressType? = null,
        shouldRun: (ScriptAction) -> Boolean = { true },
    ): List<Boolean> = runEach(actions, notificationKey, { _, action -> shouldRun(action) }, diagnosticScriptId, diagnosticPressType).flatMap { it ?: listOf(false) }

    /**
     * One entry per action in [actions], for the run log: true when it (every repetition, for a
     * "repeat_previous") succeeded, false when it failed, null when [shouldRun] left it to the bridge.
     *
     * [notificationKey] is the notification whose "Notification received" condition started this
     * run, if one did; a reply with no chat named answers that one and nothing else.
     * [shouldRun] receives the configured chain index, also when an entry repeats a previous action.
     */
    suspend fun executeEach(
        actions: List<ScriptAction>,
        notificationKey: String? = null,
        diagnosticScriptId: Int = 0,
        diagnosticPressType: TriggerPressType? = null,
        shouldRun: (Int, ScriptAction) -> Boolean = { _, _ -> true },
    ): List<Boolean?> = runEach(actions, notificationKey, shouldRun, diagnosticScriptId, diagnosticPressType).map { runs -> runs?.all { it } }

    /** Each configured action's repetition results, or null when [shouldRun] skipped it. */
    private suspend fun runEach(
        actions: List<ScriptAction>,
        notificationKey: String?,
        shouldRun: (Int, ScriptAction) -> Boolean,
        diagnosticScriptId: Int = 0,
        diagnosticPressType: TriggerPressType? = null,
    ): List<List<Boolean>?> {
        val out = mutableListOf<List<Boolean>?>()
        var previous: ScriptAction? = null
        for ((index, action) in actions.withIndex()) {
            // A cycle_mode returns before its pulses finish. The next action, including a repeat, waits.
            ModeHooks.awaitPulseReservation()
            if (action.type == REPEAT_PREVIOUS_TYPE) {
                val target = previous
                val count = action.params["count"]?.toIntOrNull()?.coerceIn(0, MAX_REPEAT_COUNT) ?: 0
                out.add(
                    when {
                        target == null || count <= 0 -> {
                            if (DiagnosticRecorder.recording) {
                                DiagnosticRecorder.action(diagnosticScriptId, diagnosticPressType, ScriptActionCatalog.all.indexOfFirst { it.type == action.type }, false)
                            }
                            listOf(false)
                        }
                        !shouldRun(index, target) -> null
                        else -> List(count) { executeObserved(target.withTriggerNotification(notificationKey), diagnosticScriptId, diagnosticPressType) }
                    },
                )
            } else {
                out.add(if (shouldRun(index, action)) listOf(executeObserved(action.withTriggerNotification(notificationKey), diagnosticScriptId, diagnosticPressType)) else null)
                previous = action
            }
        }
        return out
    }

    private suspend fun executeObserved(action: ScriptAction, scriptId: Int, pressType: TriggerPressType?): Boolean {
        val ok = try {
            handler.execute(action)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (DiagnosticRecorder.recording) {
                DiagnosticRecorder.action(scriptId, pressType, ScriptActionCatalog.all.indexOfFirst { it.type == action.type }, false, DiagnosticRecorder.Failure.EXCEPTION)
            }
            throw error
        }
        if (DiagnosticRecorder.recording) {
            DiagnosticRecorder.action(scriptId, pressType, ScriptActionCatalog.all.indexOfFirst { it.type == action.type }, ok)
        }
        return ok
    }

    companion object {
        private const val MAX_DELAY_MS = 5_000L
        private const val MAX_REPEAT_COUNT = 20
        private const val REPEAT_PREVIOUS_TYPE = "repeat_previous"

        /**
         * Every catalog entry that is not AVAILABLE is deliberately inert here rather than
         * throwing, so an old persisted script referencing a not-yet-implemented action fails safe.
         */
        fun android(context: Context): ScriptActionExecutor {
            val handler = androidHandler(context)
            return ScriptActionExecutor(singleActionTest = { action ->
                val start = System.nanoTime()
                // Once sent to the bridge, a lost reply must never run the action a second time.
                val viaBridge = BridgeController.state.value == com.example.service.bridge.BridgeState.RUNNING &&
                    action.type !in ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE
                val reply = if (viaBridge) BridgeController.runSingleActionForTest(action) else null
                val result = when {
                    viaBridge -> reply ?: ActionTestResult.outcome(false, start)
                    action.type == "shell_command" -> withContext(Dispatchers.IO) { ShellKeyInjector.shellCommand(action.params, true) }
                    action.type == "http_request" -> withContext(Dispatchers.IO) { HttpAction.execute(action.params) }
                    else -> ActionTestResult.outcome(handler.execute(action), start)
                }
                if (DiagnosticRecorder.recording) {
                    DiagnosticRecorder.test(
                        ScriptActionCatalog.all.indexOfFirst { it.type == action.type }, result.ok, result.exitCode, result.durationMs,
                        if (viaBridge) DiagnosticRecorder.ActionPath.ADVANCED_MODE_BRIDGE else DiagnosticRecorder.ActionPath.APP,
                        bridgeReplied = !viaBridge || reply != null,
                    )
                }
                result
            }, handler = handler)
        }

        private fun androidHandler(context: Context) = ScriptActionHandler { action ->
            val ctx = context.applicationContext
            when (action.type) {
                "cycle_mode" -> ModeHooks.cycle(ctx, action.params)
                "toggle_flashlight" -> FlashlightController.toggle(ctx)
                "enable_flashlight" -> FlashlightController.setTorchMode(ctx, true)
                "disable_flashlight" -> FlashlightController.setTorchMode(ctx, false)
                "change_flashlight_brightness" -> FlashlightController.setStrength(ctx, action.params["strength"]?.toIntOrNull() ?: 100)
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
                "cycle_ringer_mode" -> cycleRingerMode(ctx, action.params)
                "change_ringer_mode" -> changeRingerMode(ctx, action.params["mode"])
                "go_back" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "go_home" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "open_recents" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                // Recents pressed twice switches to the previous app; the gap lets the overview open first.
                "go_last_app" -> KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) &&
                    run { delay(GO_LAST_APP_GAP_MS); KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }
                "hide_keyboard" -> KeyMapperAccessibilityService.hideKeyboard()
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
                "toggle_mobile_data", "enable_mobile_data", "disable_mobile_data", "toggle_airplane_mode", "toggle_location" -> false
                // BluetoothAdapter.enable()/disable() have been no-ops for non-privileged apps since
                // Android 13; only reachable through Advanced Mode/the bridge's shell `svc bluetooth`.
                "toggle_bluetooth", "enable_bluetooth", "disable_bluetooth" -> false
                // PowerManager.setPowerSaveMode needs DEVICE_POWER; only the bridge's `cmd power`.
                "toggle_battery_saver", "enable_battery_saver", "disable_battery_saver" -> false
                // Secure/brand settings from the bridge's known-keys list (PresetSettings).
                "always_on_display", "grayscale", "charge_limit", "bedtime", "learned_setting" -> false
                // UiModeManager.setNightMode, the secure display keys and NFC power need the shell UID.
                "dark_mode", "night_light", "extra_dim", "screen_timeout", "toggle_nfc", "enable_nfc", "disable_nfc" -> false
                // Whether the shade is open is only readable from the shell (`dumpsys window`).
                "toggle_notification_drawer", "toggle_quick_settings" -> false
                "dismiss_all_notifications" -> NotificationEventListener.dismissAll()
                // The WALLET role / nfc_payment_default_component need the shell UID (PaymentApp).
                "set_payment_app" -> false
                // The tethering binder and strong auth + lockNow need the shell UID (Hotspot, SecureLock).
                "toggle_hotspot", "secure_lock_device", OemActions.MIND_SPACE_CAPTURE, OemActions.OPEN_MIND_SPACE,
                OemActions.TRANSLATE_SCREEN, OemActions.RECORD_AUDIO -> false
                "lock_device" ->
                    KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                "power_dialog" ->
                    KeyMapperAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                "toggle_dnd" -> setDndEnabled(ctx, null, action.params["filter"])
                "enable_dnd" -> setDndEnabled(ctx, true, action.params["filter"])
                "disable_dnd" -> setDndEnabled(ctx, false)
                "show_notification" -> showNotification(ctx, action.params)
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
                "launch_voice_assistant" -> com.example.assistant.AssistantIntegration.launch(ctx, Intent.ACTION_VOICE_COMMAND)
                "launch_device_assistant" -> com.example.assistant.AssistantIntegration.launch(ctx)
                // The screen a long press of power opens on OPPO/OnePlus/realme China builds (OemActions).
                OemActions.BUILT_IN_ASSISTANT -> startActivitySafely(
                    ctx,
                    Intent(OemActions.ASSISTANT_ACTION).setClassName(OemActions.ASSISTANT_PACKAGE, OemActions.ASSISTANT_ACTIVITY),
                )
                OemActions.OPEN_OSEE -> startActivitySafely(ctx, Intent(OemActions.OSEE_ACTION).setPackage(OemActions.OSEE_PACKAGE))
                "open_camera" -> startActivitySafely(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                "open_settings" -> startActivitySafely(ctx, Intent(Settings.ACTION_SETTINGS))
                "set_timer" -> AlarmIntents.timerSeconds(action.params)?.let { seconds ->
                    startActivitySafely(
                        ctx,
                        Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                    )
                } ?: false
                "set_alarm" -> AlarmIntents.alarmTime(action.params)?.let { (hour, minute) ->
                    startActivitySafely(
                        ctx,
                        Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                    )
                } ?: false
                // User-typed text, often a password: never logged.
                "copy_to_clipboard" -> action.params["text"]?.takeIf { it.isNotEmpty() }?.let { text ->
                    runCatching {
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("KeySnap", text))
                    }.isSuccess
                } ?: false
                // The AOSP hotspot page; only a phone without it (the start throws) gets the network settings.
                "open_hotspot_settings" -> startWithFallback(
                    { tryStartActivity(ctx, Intent("com.android.settings.WIFI_TETHER_SETTINGS")) },
                    { startActivitySafely(ctx, Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                )
                "send_intent" -> sendIntent(ctx, action.params)
                "reply_to_notification" -> NotificationEventListener.reply(ctx, action.params)
                // The chat opens with the text typed in; the user taps Send, or the bridge does.
                // Number and text are user data: never log them.
                "send_message" -> sendMessage(ctx, action.params)
                "http_request" -> withContext(Dispatchers.IO) { HttpAction.execute(action.params).ok }
                "shell_command" -> withContext(Dispatchers.IO) { ShellKeyInjector.shellCommand(action.params, false).ok }
                // Neither killing another app's process nor writing system/secure/global settings
                // has an app-UID API since Android 10+ (FORCE_STOP_PACKAGES and WRITE_SECURE_SETTINGS
                // are signature permissions); only reachable through Advanced Mode/the bridge's shell
                // `am force-stop`/`settings put`.
                "force_stop_app", "close_and_clear_app", "launch_app_shortcut", "modify_setting" -> false
                // Forcing the screen on or off needs privilege an app never holds (there is no
                // public "turn screen off" API short of Device Admin); only reachable through
                // Advanced Mode/the bridge's shell `input keyevent`.
                "screen_on", "screen_off", "toggle_screen" -> false
                // Answering or ending a call from an app needs ANSWER_PHONE_CALLS, a permission the app
                // does not hold; only reachable through Advanced Mode/the bridge's `input keyevent`.
                "answer_call", "end_call" -> false
                "toggle_auto_rotate" -> toggleSystemFlag(ctx, Settings.System.ACCELEROMETER_ROTATION)
                "portrait_mode" -> lockRotation(ctx, 0)
                "landscape_mode" -> lockRotation(ctx, 1)
                "cycle_rotations" -> lockRotation(ctx, nextUserRotation(systemInt(ctx, Settings.System.USER_ROTATION, 0)))
                "change_brightness" -> setBrightness(ctx, action.params["value"]?.trim()?.toIntOrNull())
                "toggle_auto_brightness" -> toggleSystemFlag(ctx, Settings.System.SCREEN_BRIGHTNESS_MODE)
                "keep_screen_awake" -> KeepAwake.apply(ctx, action.params["state"])
                "increase_brightness" -> adjustBrightness(ctx, 1)
                "decrease_brightness" -> adjustBrightness(ctx, -1)
                "set_wallpaper" -> setWallpaper(ctx, action.params)
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
        private fun setDndEnabled(context: Context, enabled: Boolean?, filter: String? = null): Boolean {
            if (!hasNotificationPolicyAccess(context)) return false
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val current = manager.currentInterruptionFilter
            val target = dndFilterFor(current, enabled, filter)
            // Alarms/none straight to another type strands the ringer on Silent; see BridgeMain.setDnd.
            if (current != NotificationManager.INTERRUPTION_FILTER_ALL && target != NotificationManager.INTERRUPTION_FILTER_ALL && current != target) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            manager.setInterruptionFilter(target)
            return true
        }

        /** Unique per process and unlikely to repeat across restarts, so back-to-back notifications
         *  neither replace each other nor share a PendingIntent. */
        private val notificationIds = AtomicInteger(System.currentTimeMillis().toInt())

        /**
         * Title, text and tap target are user payload: never log them. The URL is passed through
         * exactly as written. An app that is no longer installed leaves the notification without a
         * tap target instead of dropping it.
         */
        private fun showNotification(context: Context, params: Map<String, String>): Boolean {
            val title = params["title"]?.takeIf { it.isNotBlank() } ?: return false
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val localized = AppLocale.wrap(context)
            manager.createNotificationChannel(
                NotificationChannel(
                    SHORTCUT_NOTIFICATION_CHANNEL_ID,
                    localized.getString(R.string.notif_channel_shortcuts),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = localized.getString(R.string.notif_channel_shortcuts_description) },
            )
            // POST_NOTIFICATIONS denied (13+), the app's notifications off, or this channel muted.
            if (!shortcutNotificationsAllowed(manager) ||
                !NotificationPrefs.enabled(context, NotificationPrefs.Kind.SHORTCUT_ACTION)
            ) return false
            val id = notificationIds.incrementAndGet()
            val tap = when (params["onTap"]) {
                "url" -> params["url"]?.takeIf { it.isNotBlank() }?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
                "app" -> params["packageName"]?.takeIf { it.isNotBlank() }?.let { context.packageManager.getLaunchIntentForPackage(it) }
                else -> null
            }
            val builder = NotificationCompat.Builder(context, SHORTCUT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setAutoCancel(true)
            params["text"]?.takeIf { it.isNotBlank() }?.let {
                builder.setContentText(it).setStyle(NotificationCompat.BigTextStyle().bigText(it))
            }
            tap?.let {
                builder.setContentIntent(
                    PendingIntent.getActivity(context, id, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE),
                )
            }
            manager.notify(SHORTCUT_NOTIFICATION_TAG, id, builder.build())
            return true
        }

        private fun changeRingerMode(context: Context, mode: String?): Boolean {
            if (!hasNotificationPolicyAccess(context)) return false
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            val target = ringerModeFor(mode) ?: return false
            audio.ringerMode = target
            return true
        }

        // AudioManager's RINGER_MODE_* values are RingerBuzz's SILENT/VIBRATE/NORMAL (0/1/2).
        private fun cycleRingerMode(context: Context, params: Map<String, String>): Boolean {
            if (!hasNotificationPolicyAccess(context)) return false
            val audio = context.getSystemService(AudioManager::class.java) ?: return false
            val target = RingerBuzz.next(audio.ringerMode, params["modes"])
            audio.ringerMode = target
            RingerBuzz.waveform(params, target).takeIf { it.isNotEmpty() }?.let { HardwareKeyTriggerCoordinator.vibratePhone(context, it) }
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
            return writeBrightness(context, BrightnessSettings.scale(context).fromLegacy(value))
        }

        private fun writeBrightness(context: Context, raw: Int): Boolean {
            val scale = BrightnessSettings.scale(context)
            if (!scale.contains(systemInt(context, Settings.System.SCREEN_BRIGHTNESS, -1))) return false
            val resolver = context.contentResolver
            return Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            ) && Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, raw)
        }

        private fun adjustBrightness(context: Context, direction: Int): Boolean {
            if (!canWriteSettings(context)) return false
            val scale = BrightnessSettings.scale(context)
            val current = systemInt(context, Settings.System.SCREEN_BRIGHTNESS, -1)
            return scale.contains(current) && writeBrightness(context, scale.step(current, direction))
        }

        private fun showInputMethodPicker(context: Context): Boolean {
            val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java) ?: return false
            imm.showInputMethodPicker()
            // Android shows the picker only for the focused app ("Ignoring showInputMethodPickerFromClient"
            // otherwise, with no error), so from the background this is a failure.
            return MainActivity.isResumed
        }

        private const val TAP_DURATION_MS = 50L
        private const val GO_LAST_APP_GAP_MS = 350L
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

        private fun startActivitySafely(context: Context, intent: Intent): Boolean = tryStartActivity(context, intent) ?: false

        /**
         * Null when the start threw (no such screen), so nothing opened; otherwise whether Android
         * lets it through. Still tries when no exemption is visible: a start the system lets through
         * anyway must not be lost.
         */
        private fun tryStartActivity(context: Context, intent: Intent): Boolean? {
            val allowed = canStartActivityFromBackground(
                appVisible = MainActivity.isResumed,
                accessibilityBound = KeyMapperAccessibilityService.connectedOverlayContext() != null,
                canDrawOverlays = Settings.canDrawOverlays(context),
                widgetGrant = WidgetLaunchGrant.active(SystemClock.elapsedRealtime()),
            )
            return try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                allowed
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Opens the chat as always. With "Send automatically" the bridge then taps Send; with
         * Advanced Mode off that fails (the run log says why) and the chat stays open, typed. The
         * bridge also checks the chat shows this contact's name or number before it taps.
         */
        private suspend fun sendMessage(context: Context, params: Map<String, String>): Boolean {
            lastAutoSendOutcome = null
            val launch = messageLaunchFor(context, params) ?: return false
            val opened = tryStartActivity(context, launch.toIntent()) ?: return false
            // A start judged blocked may still have opened the chat; the bridge checks it before tapping.
            if (!autoSends(params, launch)) return opened
            val outcome = BridgeController.pressSend(
                launch.packageName,
                launch.text,
                params["contactName"].orEmpty(),
                launch.recipient.filter { it.isDigit() },
            )
            lastAutoSendOutcome = outcome
            return outcome == BridgeProtocol.PRESS_SEND_OK
        }

        /**
         * How the last "Send automatically" ended (see [BridgeController.pressSend]), or null when the
         * last message run did not try; [autoSendFailure] turns it into the reason the run log, the
         * failure notification and the editor's Run show.
         */
        // ponytail: one process-wide value, read when the run is recorded; two auto-sends in one run,
        // or two runs at once, can mix up which one it describes. Return a reason from the handler if
        // that ever matters.
        @Volatile
        internal var lastAutoSendOutcome: String? = null

        private fun launchApp(context: Context, packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return false
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            return startActivitySafely(context, intent)
        }

        private fun openUrl(context: Context, url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return startActivitySafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        private fun sendIntent(context: Context, params: Map<String, String>): Boolean {
            return try {
                val intent = userIntent(params)
                if (ActionPayloads.isActivity(params)) startActivitySafely(context, intent) else {
                    context.sendBroadcast(intent)
                    true
                }
            } catch (e: Exception) {
                false
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

        /**
         * Everything is inside the try: a deleted photo, a backup restored onto another phone, a
         * lost grant or a dead wallpaper service must fail the action, not the whole chain. The
         * image URI is user data: never log it.
         */
        private suspend fun setWallpaper(context: Context, params: Map<String, String>): Boolean {
            val uri = params["imageUri"]?.takeIf { it.isNotBlank() } ?: return false
            val flags = wallpaperFlagsFor(params["target"]) ?: return false
            return withContext(Dispatchers.IO) {
                try {
                    val manager = WallpaperManager.getInstance(context)
                    // A work profile or device policy can forbid it; setStream would then quietly do nothing.
                    if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return@withContext false
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { manager.setStream(it, null, true, flags) != 0 } ?: false
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
