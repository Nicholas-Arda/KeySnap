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
            summary = { res, params, _ ->
                val locale = res.locale()
                val chosen = parseDays(params.value("days"))
                weekOrder(locale).filter { it in chosen }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
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
                else -> Unit
            }
        }
    }

    /** True when the type has no form, or every required key holds a non-blank value. */
    fun isComplete(type: String, params: Map<String, String>): Boolean =
        byType[type]?.required.orEmpty().all { !params[it].isNullOrBlank() }

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

    private fun Resources.locale(): Locale = configuration.locales[0] ?: Locale.getDefault()
}
