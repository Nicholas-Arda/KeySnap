package com.example.data

import android.content.res.Resources
import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * How one constraint's parameters are collected, mirroring [ActionEditorForm]. Constraints with no
 * form here take no parameters and are added straight from the picker. [ScriptConstraintCatalog]
 * declares no parameter metadata, so [required] is the form's own contract with `ConstraintEvaluator`.
 */
data class ConstraintEditorForm(
    val type: String,
    val fields: List<EditorField>,
    @StringRes val helperRes: Int? = null,
    /** Keys that must be non-blank before Save enables; defaults to every key the fields write. */
    val required: Set<String> = fields.flatMap { it.keys }.toSet(),
    /** A check on top of [required], e.g. an https-only address. False keeps Save disabled. */
    val valid: (Map<String, String>) -> Boolean = { true },
    /** The card row's second line. Gets the installed-apps list so a package can show as its label. */
    val summary: (Resources, Map<String, String>, List<InstalledApp>) -> String,
)

private fun Map<String, String>.value(key: String): String = this[key].orEmpty().trim()

private val DAY_CODES: Map<DayOfWeek, String> = DayOfWeek.entries.associateWith { it.name.take(3).lowercase() }

object ConstraintEditorForms {

    private val appField = EditorField.AppPick("packageName", R.string.field_constraint_app)
    private val appSummary: (Resources, Map<String, String>, List<InstalledApp>) -> String = { _, params, apps ->
        val pkg = params.value("packageName")
        apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    }

    private val ssidField = EditorField.Text("ssid", R.string.field_wifi_ssid, hintRes = R.string.hint_wifi_ssid_any)
    private val ssidSummary: (Resources, Map<String, String>, List<InstalledApp>) -> String = { res, params, _ ->
        params.value("ssid").ifEmpty { res.getString(R.string.constraint_summary_any_wifi) }
    }

    private val bluetoothField = EditorField.BluetoothDevicePick("address", "name", R.string.field_bluetooth_device)
    private val bluetoothSummary: (Resources, Map<String, String>, List<InstalledApp>) -> String = { _, params, _ ->
        params.value("name").ifEmpty { params.value("address") }
    }

    private val percentField = EditorField.Number("percent", R.string.field_battery_percent, min = 0, max = 100, default = 20, suffix = "%")
    private val percentSummary: (Resources, Map<String, String>, List<InstalledApp>) -> String = { res, params, _ ->
        params["percent"]?.toIntOrNull()?.let { res.getString(R.string.constraint_summary_percent, it) }.orEmpty()
    }

    private val forms: List<ConstraintEditorForm> = listOf(
        ConstraintEditorForm("app_in_foreground", listOf(appField), summary = appSummary),
        ConstraintEditorForm("app_not_in_foreground", listOf(appField), summary = appSummary),
        ConstraintEditorForm(
            type = ScriptConstraintCatalog.NOTIFICATION_RECEIVED,
            fields = listOf(
                appField,
                EditorField.Text("contact", R.string.field_notification_contact, hintRes = R.string.hint_notification_contact_any, pickContact = true),
                EditorField.Text("contains", R.string.field_notification_contains, hintRes = R.string.hint_notification_contains_any),
            ),
            helperRes = R.string.helper_notification_received,
            required = setOf("packageName"),
            summary = { res, params, apps ->
                val contains = params.value("contains")
                listOf(appSummary(res, params, apps), params.value("contact"), if (contains.isEmpty()) "" else "“$contains”")
                    .filter { it.isNotEmpty() }
                    .joinToString(" · ")
            },
        ),
        ConstraintEditorForm(
            type = ScriptConstraintCatalog.RSS_NEW_ITEM,
            fields = listOf(EditorField.Text("url", R.string.field_feed_url, hintRes = R.string.hint_url_example)),
            helperRes = R.string.helper_rss_new_item,
            // https only: Android blocks cleartext, and the address is never rewritten to make it work.
            valid = { it.value("url").startsWith("https://", ignoreCase = true) },
            // Shown verbatim so the card says exactly which address is fetched.
            summary = { _, params, _ -> params["url"].orEmpty() },
        ),
        ConstraintEditorForm(
            type = ScriptConstraintCatalog.CALENDAR_EVENT_ACTIVE,
            fields = listOf(
                EditorField.Text("calendar", R.string.field_calendar_name, hintRes = R.string.hint_calendar_any),
                EditorField.Text("title", R.string.field_event_title, hintRes = R.string.hint_event_any),
            ),
            helperRes = R.string.helper_calendar_event_active,
            // Blank filters mean any calendar and any event.
            required = emptySet(),
            summary = { res, params, _ ->
                listOf(params.value("calendar"), params.value("title")).filter(String::isNotEmpty).joinToString(" · ")
                    .ifEmpty { res.getString(R.string.constraint_summary_any_event) }
            },
        ),
        ConstraintEditorForm(
            type = "screen_orientation",
            fields = listOf(
                EditorField.Choice(
                    "orientation", R.string.field_orientation,
                    listOf("portrait" to R.string.orientation_portrait, "landscape" to R.string.orientation_landscape),
                ),
            ),
            summary = { res, params, _ ->
                when (params.value("orientation")) {
                    "portrait" -> res.getString(R.string.orientation_portrait)
                    "landscape" -> res.getString(R.string.orientation_landscape)
                    else -> ""
                }
            },
        ),
        // Blank SSID means any network, so nothing is required.
        ConstraintEditorForm("connected_to_wifi_network", listOf(ssidField), required = emptySet(), summary = ssidSummary),
        ConstraintEditorForm("disconnected_from_wifi_network", listOf(ssidField), required = emptySet(), summary = ssidSummary),
        ConstraintEditorForm("bluetooth_device_connected", listOf(bluetoothField), required = setOf("address"), summary = bluetoothSummary),
        ConstraintEditorForm("bluetooth_device_disconnected", listOf(bluetoothField), required = setOf("address"), summary = bluetoothSummary),
        ConstraintEditorForm("battery_above", listOf(percentField), summary = percentSummary),
        ConstraintEditorForm("battery_below", listOf(percentField), summary = percentSummary),
        ConstraintEditorForm(
            type = "time",
            fields = listOf(
                EditorField.TimeRange("start", "end", R.string.field_time_start, R.string.field_time_end, defaultStart = "22:00", defaultEnd = "07:00"),
            ),
            helperRes = R.string.helper_time_window_start,
            summary = { res, params, _ ->
                val start = params.value("start")
                val end = params.value("end")
                if (start.isEmpty() || end.isEmpty()) {
                    ""
                } else {
                    val locale = res.locale()
                    res.getString(R.string.constraint_summary_time_range, formatTime(start, locale), formatTime(end, locale))
                }
            },
        ),
        ConstraintEditorForm(
            type = "day_of_week",
            fields = listOf(EditorField.DaySet("days", R.string.field_days)),
            summary = { res, params, _ -> daysSummary(res, params.value("days")) },
        ),
        ConstraintEditorForm(
            type = ScriptConstraintCatalog.AT_TIME,
            fields = listOf(
                EditorField.Time("time", R.string.field_at_time, default = "07:00"),
                EditorField.DaySet("days", R.string.field_days),
            ),
            // Blank days mean every day.
            required = setOf("time"),
            summary = { res, params, _ ->
                val time = formatTime(params.value("time"), res.locale())
                val days = params.value("days")
                if (days.isBlank()) {
                    res.getString(R.string.constraint_summary_at_time_every_day, time)
                } else {
                    res.getString(R.string.constraint_summary_at_time_days, time, daysSummary(res, days))
                }
            },
        ),
        ConstraintEditorForm(
            type = ScriptConstraintCatalog.INTERVAL,
            fields = listOf(EditorField.Interval("every", "unit", R.string.field_interval)),
            // Out-of-range numbers are fine here: the sheet clamps them on Save.
            valid = { params -> params.value("every").toIntOrNull() != null && intervalRange(params["unit"]) != null },
            summary = { res, params, _ ->
                val n = clampInterval(params.value("every"), params.value("unit")).toInt()
                when (params.value("unit")) {
                    "s" -> res.getString(R.string.constraint_summary_interval_s, n)
                    "h" -> res.getString(R.string.constraint_summary_interval_h, n)
                    else -> res.getString(R.string.constraint_summary_interval_m, n)
                }
            },
        ),
        ConstraintEditorForm(
            type = ScriptConstraintCatalog.COOLDOWN,
            fields = listOf(EditorField.Number("minutes", R.string.field_cooldown_minutes, min = 1, max = 1440, default = 10)),
            helperRes = R.string.helper_cooldown,
            summary = { res, params, _ ->
                params["minutes"]?.trim()?.toIntOrNull()?.let { res.getString(R.string.constraint_summary_cooldown, it) }.orEmpty()
            },
        ),
    )

    private val byType: Map<String, ConstraintEditorForm> = forms.associateBy { it.type }

    fun form(type: String): ConstraintEditorForm? = byType[type]

    fun needsInstalledApps(type: String): Boolean = byType[type]?.fields?.any { it is EditorField.AppPick } == true

    /** Seed values for a freshly added constraint. Free text, apps, devices and days start empty. */
    fun defaults(form: ConstraintEditorForm): Map<String, String> = buildMap {
        form.fields.forEach { field ->
            when (field) {
                is EditorField.Number -> put(field.key, field.default.toString())
                is EditorField.Choice -> field.options.firstOrNull()?.let { put(field.key, it.first) }
                is EditorField.TimeRange -> {
                    put(field.startKey, field.defaultStart)
                    put(field.endKey, field.defaultEnd)
                }
                is EditorField.Time -> put(field.key, field.default)
                is EditorField.Interval -> {
                    put(field.valueKey, "30")
                    put(field.unitKey, "m")
                }
                else -> Unit
            }
        }
    }

    /** True when the type has no form, or every required key holds a non-blank value and the form's own check passes. */
    fun isComplete(type: String, params: Map<String, String>): Boolean {
        val form = byType[type] ?: return true
        return form.required.all { !params[it].isNullOrBlank() } && form.valid(params)
    }

    /** The card row's second line, or null when the constraint takes no configuration. */
    fun summarize(constraint: ScriptConstraint, resources: Resources, installedApps: List<InstalledApp> = emptyList()): String? =
        byType[constraint.type]?.summary?.invoke(resources, constraint.params, installedApps)?.takeIf { it.isNotBlank() }

    /** Canonical `mon,tue,…` order regardless of which days were picked first. */
    fun formatDays(days: Set<DayOfWeek>): String = DayOfWeek.entries.filter { it in days }.joinToString(",") { DAY_CODES.getValue(it) }

    fun parseDays(value: String): Set<DayOfWeek> {
        val codes = value.split(',').map { it.trim().lowercase() }.toSet()
        return DAY_CODES.filterValues { it in codes }.keys
    }

    /** Monday-first or Sunday-first, as the locale expects. */
    fun weekOrder(locale: Locale): List<DayOfWeek> {
        val first = WeekFields.of(locale).firstDayOfWeek
        return List(7) { first.plus(it.toLong()) }
    }

    fun formatTime(value: String, locale: Locale): String =
        runCatching { LocalTime.parse(value).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
            .getOrDefault(value)

    fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(Locale.ROOT, hour, minute)

    /** `HH:MM` to hour and minute, or null when the value is not one. */
    fun parseTime(value: String): Pair<Int, Int>? =
        runCatching { LocalTime.parse(value).let { it.hour to it.minute } }.getOrNull()

    /** Allowed `every` values for an interval `unit` (s|m|h), or null for an unknown unit. */
    fun intervalRange(unit: String?): IntRange? = when (unit?.trim()) {
        "s" -> 5..59
        "m" -> 1..59
        "h" -> 1..24
        else -> null
    }

    /** [every] clamped into [unit]'s range, as typed text; blank or junk becomes the minimum. An unknown unit reads as minutes. */
    fun clampInterval(every: String, unit: String): String {
        val range = intervalRange(unit) ?: intervalRange("m")!!
        return (every.trim().toIntOrNull() ?: range.first).coerceIn(range).toString()
    }

    /** True when the period divides its parent (a minute, an hour, a day), so it lands on the same clock marks each time. */
    fun intervalDividesClock(every: String, unit: String): Boolean {
        if (every.trim().toIntOrNull() == null) return false
        val n = clampInterval(every, unit).toInt()
        return (if (unit.trim() == "h") 24 else 60) % n == 0
    }

    /**
     * The first few runs of an interval as clock marks, e.g. ":00, :15, :30, :45" or, for a period that does not
     * divide its parent, "00:00, 00:07, 00:14, 00:21, …" counted from midnight. Null when malformed.
     */
    fun clockExample(every: String, unit: String): String? {
        val u = unit.trim()
        if (intervalRange(u) == null || every.trim().toIntOrNull() == null) return null
        val n = clampInterval(every, u).toInt()
        val unitSeconds = when (u) { "s" -> 1; "m" -> 60; else -> 3600 }
        val divides = intervalDividesClock(every, u)
        val span = if (divides) (if (u == "h") 24 else 60) * unitSeconds else 86_400
        val step = n * unitSeconds
        val count = (span + step - 1) / step
        val ticks = (0 until minOf(count, 4)).map { k ->
            val t = k * step
            when {
                divides && u == "s" -> ":%02d".format(Locale.ROOT, t)
                divides && u == "m" -> ":%02d".format(Locale.ROOT, t / 60)
                u == "s" -> "%02d:%02d:%02d".format(Locale.ROOT, t / 3600, t / 60 % 60, t % 60)
                else -> "%02d:%02d".format(Locale.ROOT, t / 3600, t / 60 % 60)
            }
        }
        return (if (count > 4) ticks + "…" else ticks).joinToString(", ")
    }

    /** Picked days in the locale's week order, e.g. "Mon, Fri". */
    private fun daysSummary(res: Resources, days: String): String {
        val locale = res.locale()
        val chosen = parseDays(days)
        return weekOrder(locale).filter { it in chosen }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    private fun Resources.locale(): Locale = configuration.locales[0] ?: Locale.getDefault()
}
