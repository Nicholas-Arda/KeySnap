package com.example.data

import android.content.res.Resources
import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R

/**
 * How one action's parameters are collected from the user. Deliberately separate from
 * [ScriptActionCatalog], which stays the source of truth for *which* params exist and which are
 * required: the persisted `ScriptAction.params` contract that `ScriptActionExecutor` and the
 * detached bridge both read must not change shape to suit the UI. [EditorField.Point] and
 * [EditorField.TwoPoints] are why this layer exists — one control that writes two or four params.
 */
sealed interface EditorField {

    data class Text(
        val key: String,
        @StringRes val labelRes: Int,
        @StringRes val hintRes: Int? = null,
        val multiline: Boolean = false,
        @StringRes val helperRes: Int? = null,
        /** Shown only while [visibleWhen]'s param holds one of its values, e.g. a body for POST. */
        val visibleWhen: Pair<String, Set<String>>? = null,
    ) : EditorField

    data class Number(
        val key: String,
        @StringRes val labelRes: Int,
        val min: Int,
        val max: Int,
        val default: Int,
        /** A unit symbol such as "ms". Not translated. */
        val suffix: String = "",
    ) : EditorField

    /** [options] is persisted-value to display-label, so labels can change without a migration. */
    data class Choice(
        val key: String,
        @StringRes val labelRes: Int,
        val options: List<Pair<String, Int>>,
    ) : EditorField

    data class KeyCodePick(val key: String, @StringRes val labelRes: Int) : EditorField

    data class AppPick(val key: String, @StringRes val labelRes: Int) : EditorField

    data class Point(val xKey: String, val yKey: String, @StringRes val labelRes: Int) : EditorField

    data class TwoPoints(
        val x1Key: String,
        val y1Key: String,
        @StringRes val labelARes: Int,
        val x2Key: String,
        val y2Key: String,
        @StringRes val labelBRes: Int,
    ) : EditorField

    /** A bonded Bluetooth device; writes its address and, for the summary, its name. */
    data class BluetoothDevicePick(
        val addressKey: String,
        val nameKey: String,
        @StringRes val labelRes: Int,
    ) : EditorField

    /** Two clock times persisted as `HH:MM`. */
    data class TimeRange(
        val startKey: String,
        val endKey: String,
        @StringRes val startLabelRes: Int,
        @StringRes val endLabelRes: Int,
        val defaultStart: String,
        val defaultEnd: String,
    ) : EditorField

    /** Days of the week persisted as comma-separated `mon,tue,…,sun`. */
    data class DaySet(val key: String, @StringRes val labelRes: Int) : EditorField
}

/** Every param key this field writes. A point writes two, a two-point field writes four. */
val EditorField.keys: List<String>
    get() = when (this) {
        is EditorField.Text -> listOf(key)
        is EditorField.Number -> listOf(key)
        is EditorField.Choice -> listOf(key)
        is EditorField.KeyCodePick -> listOf(key)
        is EditorField.AppPick -> listOf(key)
        is EditorField.Point -> listOf(xKey, yKey)
        is EditorField.TwoPoints -> listOf(x1Key, y1Key, x2Key, y2Key)
        is EditorField.BluetoothDevicePick -> listOf(addressKey, nameKey)
        is EditorField.TimeRange -> listOf(startKey, endKey)
        is EditorField.DaySet -> listOf(key)
    }

data class ActionEditorForm(
    val type: String,
    val fields: List<EditorField>,
    /** One line under the sheet title saying what the action does, for non-obvious actions. */
    @StringRes val helperRes: Int? = null,
    /** Badge text such as "Requires Advanced Mode"; informational, never a save gate. */
    @StringRes val requirementRes: Int? = null,
    /** The chain row's second line, e.g. "GET · https://example.com". */
    val summary: (Resources, Map<String, String>) -> String,
)

private val ADVANCED_MODE_BADGE = R.string.badge_expert_mode
@Suppress("unused") private val ACCESSIBILITY_BADGE = R.string.badge_accessibility
val NOTIFICATION_POLICY_BADGE = R.string.badge_notification_policy
val WRITE_SETTINGS_BADGE = R.string.badge_write_settings

private fun Map<String, String>.value(key: String): String = this[key].orEmpty().trim()

private fun Map<String, String>.pointText(xKey: String, yKey: String): String {
    val x = value(xKey)
    val y = value(yKey)
    return if (x.isEmpty() || y.isEmpty()) "" else "$x, $y"
}

private fun joinSummary(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" · ")

/** Resolves a persisted choice id to its localized label, falling back to the raw id. */
private fun Resources.choice(value: String, vararg pairs: Pair<String, Int>): String =
    pairs.firstOrNull { it.first == value }?.let { getString(it.second) } ?: value

/**
 * The single registry of editor forms. Kept in the same order as [ScriptActionCatalog.all] so the
 * two can be diffed by eye; [ActionEditorFormTest] enforces that they cover each other.
 */
object ActionEditorForms {

    private val forms: List<ActionEditorForm> = listOf(
        ActionEditorForm(
            type = "input_key_code",
            fields = listOf(EditorField.KeyCodePick("keyCode", R.string.field_key_to_send)),
            helperRes = R.string.helper_input_key_code,
            requirementRes = ADVANCED_MODE_BADGE,
            summary = { res, params ->
                params["keyCode"]?.toIntOrNull()?.let { getReadableKeyName(res, it) }.orEmpty()
            },
        ),
        ActionEditorForm(
            type = "input_text",
            fields = listOf(EditorField.Text("text", R.string.param_text, hintRes = R.string.hint_input_text)),
            summary = { _, params -> params.value("text") },
        ),
        ActionEditorForm(
            type = "tap_screen",
            fields = listOf(
                EditorField.Point("x", "y", R.string.field_tap_here),
                EditorField.Number("count", R.string.param_taps, min = 1, max = 20, default = 1),
                EditorField.Number("interval", R.string.field_time_between_taps, min = 0, max = 5000, default = 100, suffix = "ms"),
            ),
            summary = { _, params ->
                val count = params["count"]?.toIntOrNull() ?: 1
                joinSummary(params.pointText("x", "y"), if (count > 1) "×$count" else "")
            },
        ),
        ActionEditorForm(
            type = "swipe_screen",
            fields = listOf(
                EditorField.TwoPoints("x1", "y1", R.string.field_swipe_from, "x2", "y2", R.string.field_swipe_to),
                EditorField.Number("duration", R.string.field_duration, min = 1, max = 5000, default = 300, suffix = "ms"),
            ),
            summary = { _, params ->
                val from = params.pointText("x1", "y1")
                val to = params.pointText("x2", "y2")
                if (from.isEmpty() || to.isEmpty()) "" else "$from → $to"
            },
        ),
        ActionEditorForm(
            type = "pinch_screen",
            fields = listOf(
                EditorField.TwoPoints("x1", "y1", R.string.field_first_finger, "x2", "y2", R.string.field_second_finger),
                EditorField.Choice(
                    "pinchType", R.string.param_direction,
                    listOf("in" to R.string.choice_pinch_in, "out" to R.string.choice_pinch_out),
                ),
                EditorField.Number("duration", R.string.field_duration, min = 1, max = 5000, default = 300, suffix = "ms"),
            ),
            summary = { res, params ->
                val direction = res.getString(
                    if (params["pinchType"] == "out") R.string.summary_zoom_in else R.string.summary_zoom_out,
                )
                val from = params.pointText("x1", "y1")
                val to = params.pointText("x2", "y2")
                if (from.isEmpty() || to.isEmpty()) direction else "$direction · $from ↔ $to"
            },
        ),
        ActionEditorForm(
            type = "launch_app",
            fields = listOf(EditorField.AppPick("packageName", R.string.field_app_to_open)),
            summary = { _, params -> params.value("packageName") },
        ),
        ActionEditorForm(
            type = "open_url",
            fields = listOf(EditorField.Text("url", R.string.param_url, hintRes = R.string.hint_url_example)),
            summary = { _, params -> params.value("url") },
        ),
        ActionEditorForm(
            type = "http_request",
            fields = listOf(
                EditorField.Choice(
                    "method", R.string.param_method,
                    listOf(
                        "GET" to R.string.choice_http_get,
                        "POST" to R.string.choice_http_post,
                        "PUT" to R.string.choice_http_put,
                        "DELETE" to R.string.choice_http_delete,
                    ),
                ),
                EditorField.Text("url", R.string.param_url, hintRes = R.string.hint_webhook_url),
                EditorField.Text(
                    "body",
                    R.string.param_body,
                    hintRes = R.string.hint_http_body,
                    multiline = true,
                    visibleWhen = "method" to setOf("POST", "PUT"),
                ),
            ),
            helperRes = R.string.helper_http_request,
            summary = { _, params -> joinSummary(params.value("method"), params.value("url")) },
        ),
        ActionEditorForm(
            type = "shell_command",
            fields = listOf(EditorField.Text("command", R.string.param_command, hintRes = R.string.hint_shell_command)),
            helperRes = R.string.helper_shell_command,
            requirementRes = ADVANCED_MODE_BADGE,
            summary = { _, params -> params.value("command") },
        ),
        ActionEditorForm(
            type = "send_intent",
            fields = listOf(
                EditorField.Text("action", R.string.param_action, hintRes = R.string.hint_intent_action),
                EditorField.Text("uri", R.string.param_uri, hintRes = R.string.hint_optional),
                EditorField.Text("extras", R.string.field_extras, hintRes = R.string.hint_extras, helperRes = R.string.helper_extras),
            ),
            helperRes = R.string.helper_send_intent,
            summary = { _, params -> params.value("action") },
        ),
        ActionEditorForm(
            type = "force_stop_app",
            fields = listOf(EditorField.AppPick("packageName", R.string.field_app_to_stop)),
            requirementRes = ADVANCED_MODE_BADGE,
            summary = { _, params -> params.value("packageName") },
        ),
        ActionEditorForm(
            type = "modify_setting",
            fields = listOf(
                EditorField.Choice(
                    "namespace", R.string.param_namespace,
                    listOf(
                        "system" to R.string.choice_namespace_system,
                        "secure" to R.string.choice_namespace_secure,
                        "global" to R.string.choice_namespace_global,
                    ),
                ),
                EditorField.Text("key", R.string.field_setting_key, hintRes = R.string.hint_screen_brightness),
                EditorField.Text("value", R.string.param_value, hintRes = R.string.hint_brightness_value),
            ),
            helperRes = R.string.helper_modify_setting,
            requirementRes = ADVANCED_MODE_BADGE,
            summary = { _, params -> joinSummary(params.value("namespace"), params.value("key"), params.value("value")) },
        ),
        ActionEditorForm(
            type = "change_volume_stream",
            fields = listOf(
                EditorField.Choice(
                    "stream", R.string.param_stream,
                    listOf(
                        "ring" to R.string.choice_stream_ring,
                        "media" to R.string.choice_stream_media,
                        "alarm" to R.string.choice_stream_alarm,
                        "notification" to R.string.choice_stream_notification,
                    ),
                ),
                EditorField.Number("value", R.string.field_level, min = 0, max = 25, default = 0),
            ),
            summary = { res, params ->
                joinSummary(
                    res.choice(
                        params.value("stream"),
                        "ring" to R.string.choice_stream_ring,
                        "media" to R.string.choice_stream_media,
                        "alarm" to R.string.choice_stream_alarm,
                        "notification" to R.string.choice_stream_notification,
                    ),
                    params.value("value"),
                )
            },
        ),
        ActionEditorForm(
            type = "change_ringer_mode",
            fields = listOf(
                EditorField.Choice(
                    "mode", R.string.param_mode,
                    listOf(
                        "normal" to R.string.choice_ringer_normal,
                        "vibrate" to R.string.choice_ringer_vibrate,
                        "silent" to R.string.choice_ringer_silent,
                    ),
                ),
            ),
            requirementRes = NOTIFICATION_POLICY_BADGE,
            summary = { res, params ->
                res.choice(
                    params.value("mode"),
                    "normal" to R.string.choice_ringer_normal,
                    "vibrate" to R.string.choice_ringer_vibrate,
                    "silent" to R.string.choice_ringer_silent,
                )
            },
        ),
        // No params, so this exists only to surface NOTIFICATION_POLICY_BADGE — without it, a
        // missing Do Not Disturb grant made the action a silent no-op with no way to discover why.
        ActionEditorForm(
            type = "cycle_ringer_mode",
            fields = emptyList(),
            requirementRes = NOTIFICATION_POLICY_BADGE,
            summary = { _, _ -> "" },
        ),
        ActionEditorForm(
            type = "change_brightness",
            fields = listOf(EditorField.Number("value", R.string.field_brightness, min = 0, max = 255, default = 128)),
            requirementRes = WRITE_SETTINGS_BADGE,
            summary = { _, params -> params.value("value") },
        ),
        // Same trick as cycle_ringer_mode: no params, the form only exists so the sheet can show
        // the WRITE_SETTINGS grant. Home no longer nags about that permission; it is asked for
        // here, at the one place a user has just chosen an action that needs it.
        *listOf("toggle_auto_rotate", "portrait_mode", "landscape_mode", "cycle_rotations", "toggle_auto_brightness")
            .map { ActionEditorForm(type = it, fields = emptyList(), requirementRes = WRITE_SETTINGS_BADGE, summary = { _, _ -> "" }) }
            .toTypedArray(),
        ActionEditorForm(
            type = "play_sound",
            fields = listOf(EditorField.Text("uri", R.string.param_sound, hintRes = R.string.hint_sound_uri)),
            summary = { _, params -> params.value("uri") },
        ),
        ActionEditorForm(
            type = "text_to_speech",
            fields = listOf(EditorField.Text("text", R.string.field_text_to_speak, multiline = true)),
            summary = { _, params -> params.value("text") },
        ),
        ActionEditorForm(
            type = "vibrate",
            fields = listOf(EditorField.Number("duration", R.string.field_duration, min = 1, max = 5000, default = 200, suffix = "ms")),
            summary = { _, params -> params.value("duration").let { if (it.isEmpty()) "" else "$it ms" } },
        ),
        ActionEditorForm(
            type = "delay",
            fields = listOf(EditorField.Number("duration", R.string.field_wait, min = 0, max = 5000, default = 500, suffix = "ms")),
            summary = { _, params -> params.value("duration").let { if (it.isEmpty()) "" else "$it ms" } },
        ),
        ActionEditorForm(
            type = "repeat_previous",
            fields = listOf(EditorField.Number("count", R.string.param_times, min = 1, max = 20, default = 2)),
            helperRes = R.string.helper_repeat_previous,
            summary = { _, params -> params.value("count").let { if (it.isEmpty()) "" else "×$it" } },
        ),
    )

    private val byType: Map<String, ActionEditorForm> = forms.associateBy { it.type }

    fun forType(type: String): ActionEditorForm? = byType[type]

    fun isConfigurable(type: String): Boolean = byType.containsKey(type)

    /** True for the actions only the shell-UID bridge can run, so the editor can say so up front. */
    fun needsAdvancedMode(type: String): Boolean = byType[type]?.requirementRes == ADVANCED_MODE_BADGE

    /** Seed values for a freshly added action. Points and free text start empty on purpose. */
    fun defaults(form: ActionEditorForm): Map<String, String> = buildMap {
        form.fields.forEach { field ->
            when (field) {
                is EditorField.Number -> put(field.key, field.default.toString())
                is EditorField.Choice -> field.options.firstOrNull()?.let { put(field.key, it.first) }
                else -> Unit
            }
        }
    }

    /**
     * Hides fields gated on another field's current value, e.g. an HTTP body for GET.
     *
     * Only [EditorField.Text] is checked for `visibleWhen` here; a future gated [EditorField.Choice]
     * or [EditorField.Number] would be silently treated as always-visible. That matters because both
     * [isComplete] and [missingRequired] derive their visible-field set from this function — a
     * required param that is actually hidden-but-gated on such a field would never be excluded from
     * the required set, permanently disabling Save.
     */
    fun visibleFields(form: ActionEditorForm, values: Map<String, String>): List<EditorField> =
        form.fields.filter { field ->
            val gate = (field as? EditorField.Text)?.visibleWhen ?: return@filter true
            values[gate.first] in gate.second
        }

    /** True once every *required, visible* param holds a non-blank value. */
    fun isComplete(form: ActionEditorForm, values: Map<String, String>): Boolean {
        val required = ScriptActionCatalog.descriptor(form.type)
            ?.parameters.orEmpty()
            .filter { it.required }
            .map { it.key }
            .toSet()
        val visible = visibleFields(form, values).flatMap { it.keys }.toSet()
        return required.intersect(visible).all { !values[it].isNullOrBlank() }
    }

    /**
     * Required params the saved action is still missing. Non-empty means the editor shows
     * "Tap to configure" — the recovery path for scripts saved before forms existed.
     */
    fun missingRequired(action: ScriptAction): List<String> {
        val form = byType[action.type] ?: return emptyList()
        val visible = visibleFields(form, action.params).flatMap { it.keys }.toSet()
        return ScriptActionCatalog.descriptor(action.type)
            ?.parameters.orEmpty()
            .filter { it.required && it.key in visible && action.params[it.key].isNullOrBlank() }
            .map { it.key }
    }

    /** The chain row's second line, or null when the action takes no configuration at all. */
    fun summarize(action: ScriptAction, resources: Resources): String? =
        byType[action.type]?.summary?.invoke(resources, action.params)?.takeIf { it.isNotBlank() }
}
