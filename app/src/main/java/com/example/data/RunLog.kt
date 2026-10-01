package com.example.data

import androidx.annotation.StringRes
import com.squareup.moshi.JsonClass
import io.github.nicholasarda.keysnap.R

/**
 * One run of one shortcut, for its "Recent runs" card and the failure notification. Holds action
 * type ids and outcomes only — never an action's params, which routinely carry URLs, commands,
 * tokens and typed text.
 */
@JsonClass(generateAdapter = true)
data class RunRecord(
    val scriptId: String,
    val at: Long,
    /** One of the `SOURCE_` constants; a plain string so a newer source still reads back. */
    val source: String,
    val keyCode: Int? = null,
    /** A [TriggerPressType] name, for key runs. */
    val pressType: String? = null,
    val actions: List<ActionResult>,
) {
    companion object {
        const val SOURCE_KEY = "key"
        const val SOURCE_AUTOMATION = "automation"
        const val SOURCE_WIDGET = "widget"
        const val SOURCE_TILE = "tile"
    }
}

/** [reason] is a [RunFailure] name, set only when [ok] is false. A string, so an unknown one reads back. */
@JsonClass(generateAdapter = true)
data class ActionResult(val type: String, val ok: Boolean, val reason: String? = null)

@JsonClass(generateAdapter = true)
internal data class PersistedRunLog(val runs: List<RunRecord> = emptyList())

/** Why an action failed, worded with the same strings as the action editor's requirement badges. */
enum class RunFailure(@StringRes val labelRes: Int) {
    ADVANCED_MODE_OFF(R.string.badge_expert_mode),
    ACCESSIBILITY_OFF(R.string.badge_accessibility),
    DND_ACCESS_MISSING(R.string.badge_notification_policy),
    WRITE_SETTINGS_MISSING(R.string.badge_write_settings),
    NOTIFICATIONS_OFF(R.string.run_failure_notifications_off),
    NOTIFICATION_ACCESS_MISSING(R.string.badge_notification_access),
    FAILED(R.string.run_failure_generic),
    ;

    companion object {
        fun from(name: String?): RunFailure = entries.firstOrNull { it.name == name } ?: FAILED
    }
}

/**
 * What the app process could see when an action it ran failed. [advancedMode] is whether the bridge
 * was up: the app can still end up running a bridge action then (a RUN that fell through), and
 * "Requires Advanced Mode" would be wrong.
 */
data class RunEnvironment(
    val accessibility: Boolean,
    val dndAccess: Boolean,
    val writeSettings: Boolean,
    val advancedMode: Boolean = false,
    /** Whether a shortcut's own notification could be seen; see `shortcutNotificationsAllowed`. */
    val notifications: Boolean = true,
    /** Whether KeySnap holds notification access, which "Reply to notification" needs. */
    val notificationAccess: Boolean = true,
)

// Which prerequisite explains an app-side failure. These mirror ScriptActionExecutor.android:
// ADVANCED_MODE_TYPES are the branches it hard-returns false for, plus shell_command, which runs
// there only with app privileges; the others are the branches that check a grant or need the
// Accessibility service. RunLogTest pins them against the catalog and the editor's badges.
// ponytail: the reason is inferred from the missing prerequisite, not reported by the action; a
// failure with everything granted reads "Didn't complete". Return a reason from
// ScriptActionHandler if users find that too vague.
internal val ADVANCED_MODE_TYPES = setOf(
    "input_key_code", "open_menu",
    "toggle_wifi", "enable_wifi", "disable_wifi", "toggle_mobile_data", "toggle_airplane_mode", "toggle_location",
    "toggle_bluetooth", "enable_bluetooth", "disable_bluetooth",
    "switch_keyboard", "force_stop_app", "modify_setting", "shell_command",
    "screen_on", "screen_off", "toggle_screen",
)
internal val ACCESSIBILITY_TYPES = setOf(
    "input_text", "tap_screen", "swipe_screen", "pinch_screen",
    "go_back", "go_home", "open_recents", "expand_notification_drawer", "expand_quick_settings",
    "collapse_status_bar", "take_screenshot", "toggle_split_screen", "move_cursor_to_end",
    "select_word_at_cursor", "lock_device", "power_dialog", "text_cut", "text_copy", "text_paste",
)
internal val DND_ACCESS_TYPES = setOf(
    "toggle_dnd", "enable_dnd", "disable_dnd", "cycle_ringer_mode", "change_ringer_mode", "change_volume_stream",
)
internal val WRITE_SETTINGS_TYPES = setOf(
    "toggle_auto_rotate", "portrait_mode", "landscape_mode", "cycle_rotations",
    "change_brightness", "toggle_auto_brightness", "increase_brightness", "decrease_brightness",
)

internal val NOTIFICATION_TYPES = setOf("show_notification")

/** Actions that need notification access; one with access on and nothing to reply to reads FAILED. */
internal val NOTIFICATION_ACCESS_TYPES = setOf("reply_to_notification")

internal const val MAX_RUNS_PER_SCRIPT = 20
internal const val MAX_RUNS = 200

internal fun failureReason(type: String, env: RunEnvironment): RunFailure = when {
    type in ADVANCED_MODE_TYPES -> if (env.advancedMode) RunFailure.FAILED else RunFailure.ADVANCED_MODE_OFF
    type in ACCESSIBILITY_TYPES && !env.accessibility -> RunFailure.ACCESSIBILITY_OFF
    type in DND_ACCESS_TYPES && !env.dndAccess -> RunFailure.DND_ACCESS_MISSING
    type in WRITE_SETTINGS_TYPES && !env.writeSettings -> RunFailure.WRITE_SETTINGS_MISSING
    type in NOTIFICATION_TYPES && !env.notifications -> RunFailure.NOTIFICATIONS_OFF
    type in NOTIFICATION_ACCESS_TYPES && !env.notificationAccess -> RunFailure.NOTIFICATION_ACCESS_MISSING
    else -> RunFailure.FAILED
}

/**
 * [appResults] is `ScriptActionExecutor.executeEach`'s output, where null marks an action the bridge
 * ran. [bridgeReport] is the bridge's own per-action report. It is used only when it describes this
 * exact action list: the bridge re-reads its config once a second, so a press right after an edit
 * can report the old list.
 */
fun buildRunRecord(
    scriptId: String,
    at: Long,
    source: String,
    keyCode: Int?,
    pressType: String?,
    actions: List<ScriptAction>,
    appResults: List<Boolean?>,
    bridgeReport: List<Pair<String, Boolean>>?,
    env: RunEnvironment,
): RunRecord {
    val report = bridgeReport?.takeIf { r -> r.map { it.first } == actions.map { it.type } }
    val results = actions.mapIndexed { index, action ->
        val app = appResults.getOrNull(index)
        // ponytail: with no usable report the bridge's share counts as done — its TRIGGER says it ran.
        val ok = app ?: report?.get(index)?.second ?: true
        val reason = when {
            ok -> null
            app != null -> failureReason(action.type, env).name
            else -> RunFailure.FAILED.name
        }
        ActionResult(action.type, ok, reason)
    }
    return RunRecord(scriptId, at, source, keyCode, pressType, results)
}

/** Newest first; at most [MAX_RUNS_PER_SCRIPT] per shortcut and [MAX_RUNS] overall. */
internal fun appendRun(log: List<RunRecord>, record: RunRecord): List<RunRecord> {
    val trimmed = if (log.count { it.scriptId == record.scriptId } >= MAX_RUNS_PER_SCRIPT) {
        val oldest = log.indexOfLast { it.scriptId == record.scriptId }
        log.filterIndexed { index, _ -> index != oldest }
    } else {
        log
    }
    return (listOf(record) + trimmed).take(MAX_RUNS)
}

/** Shortcuts whose most recent run had a failed action. */
fun latestFailedScriptIds(log: List<RunRecord>): Set<String> =
    log.distinctBy { it.scriptId }.filter { run -> run.actions.any { !it.ok } }.mapTo(mutableSetOf()) { it.scriptId }
