package com.example.data

import androidx.annotation.StringRes
import com.example.bridge.ActionPayloads
import io.github.nicholasarda.keysnap.R
import java.util.Locale

/**
 * Pure rules for the five privileged action editors. Nothing here logs a payload, and the only
 * rewrites are the ones the editor promises: omit `content_type` on GET/DELETE, drop an invalid
 * timeout or target on save, and prefix "Bearer " only when that chip is tapped.
 */
data class HeaderRow(val name: String, val value: String)

data class ExtraRow(val key: String, val value: String)

data class ReadyCommand(@StringRes val titleRes: Int, val command: String)

data class SettingSuggestion(val namespace: String, val key: String, @StringRes val labelRes: Int)

enum class UrlIssue { EMPTY, NOT_WEB, PLAIN }

enum class SettingProbe { APP, BRIDGE, BLOCKED }

object PrivilegedEditor {
    const val HTTP = "http_request"
    const val SHELL = "shell_command"
    const val INTENT = "send_intent"
    const val REPLY = "reply_to_notification"
    const val SETTING = "modify_setting"
    val TYPES = setOf(HTTP, SHELL, INTENT, REPLY, SETTING)

    val TIMEOUTS = listOf("5", "10", "30", "60")
    val CONTENT_TYPES = listOf("none", "json", "form", "text")
    val METHODS = listOf("GET", "POST", "PUT", "DELETE")
    val TARGETS = listOf("broadcast", "activity")
    val NAMESPACES = listOf("system", "secure", "global")
    const val BEARER = "Bearer "

    private val SAFE_KEY = Regex("[A-Za-z0-9._-]+")

    val suggestions: List<SettingSuggestion> = listOf(
        SettingSuggestion("system", "screen_off_timeout", R.string.editor_suggest_timeout),
        SettingSuggestion("system", "font_scale", R.string.editor_suggest_font),
        SettingSuggestion("system", "screen_brightness", R.string.editor_suggest_brightness),
        SettingSuggestion("global", "window_animation_scale", R.string.editor_suggest_window),
        SettingSuggestion("global", "transition_animation_scale", R.string.editor_suggest_transition),
        SettingSuggestion("global", "animator_duration_scale", R.string.editor_suggest_animator),
    )

    fun headerRows(raw: String?): List<HeaderRow> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            ActionPayloads.headers(raw).map { HeaderRow(it.key, it.value) }
        } catch (_: IllegalArgumentException) {
            // Keep the blob intact. Splitting a bad line would drop or rewrite part of it.
            listOf(HeaderRow("", raw))
        }
    }

    fun headerError(row: HeaderRow): Boolean {
        if (row.name.isEmpty() && row.value.isEmpty()) return false
        val line = "${row.name}:${row.value}"
        return try {
            val parsed = ActionPayloads.headers(line)
            parsed.size != 1 || parsed[0].key != row.name || parsed[0].value != row.value
        } catch (_: IllegalArgumentException) {
            true
        }
    }

    fun serializeHeaders(rows: List<HeaderRow>, validOnly: Boolean): String {
        val kept = rows.filter { it.name.isNotEmpty() || it.value.isNotEmpty() }
        val chosen = if (validOnly) kept.filter { !headerError(it) } else kept
        return chosen.joinToString("\n") { "${it.name}:${it.value}" }
    }

    /**
     * The string a Test sends. Null means the joined text would parse as a different header than
     * the row, so the caller must fail without opening a connection.
     */
    fun headersForRun(rows: List<HeaderRow>, original: String?): String? {
        val meaningful = rows.filter { it.name.isNotEmpty() || it.value.isNotEmpty() }
        if (meaningful.size == 1 && meaningful[0].name.isEmpty() && original != null && meaningful[0].value == original) {
            return original
        }
        val joined = meaningful.joinToString("\n") { "${it.name}:${it.value}" }
        if (meaningful.none { headerError(it) }) return joined
        return try {
            val parsed = ActionPayloads.headers(joined)
            val same = parsed.size == meaningful.size && parsed.indices.all { index ->
                parsed[index].key == meaningful[index].name && parsed[index].value == meaningful[index].value
            }
            if (same) joined else null
        } catch (_: IllegalArgumentException) {
            joined
        }
    }

    fun masksAuthorization(name: String): Boolean = name.equals("Authorization", ignoreCase = true)

    fun needsBearer(rows: List<HeaderRow>): Boolean = rows.any { row ->
        masksAuthorization(row.name) && row.value.isNotEmpty() && !row.value.startsWith(BEARER)
    }

    /** Prefixes only Authorization values that do not already start with "Bearer ". */
    fun withBearer(rows: List<HeaderRow>): List<HeaderRow> = rows.map { row ->
        if (masksAuthorization(row.name) && row.value.isNotEmpty() && !row.value.startsWith(BEARER)) {
            row.copy(value = BEARER + row.value)
        } else {
            row
        }
    }

    /** Trim is classification only. The stored URL stays byte for byte. */
    fun urlIssue(url: String): UrlIssue? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return UrlIssue.EMPTY
        val lower = trimmed.lowercase(Locale.US)
        return when {
            lower.startsWith("https://") -> null
            lower.startsWith("http://") -> UrlIssue.PLAIN
            else -> UrlIssue.NOT_WEB
        }
    }

    fun contentTypeConflicts(contentType: String, rows: List<HeaderRow>): Boolean {
        val mime = when (contentType) {
            "", "none" -> return false
            "json" -> "application/json"
            "form" -> "application/x-www-form-urlencoded"
            "text" -> "text/plain"
            else -> return false
        }
        return rows.any { row ->
            row.name.equals("Content-Type", ignoreCase = true) &&
                row.value.isNotEmpty() &&
                !row.value.trim().equals(mime, ignoreCase = true)
        }
    }

    fun contentTypeInvalid(value: String?): Boolean {
        val stored = value.orEmpty()
        return stored.isNotEmpty() && stored !in CONTENT_TYPES
    }

    /** Blank displays as 10 and is not a stored choice. An unknown value selects nothing. */
    fun timeoutSelection(stored: String?): String? {
        val value = stored.orEmpty()
        if (value.isEmpty()) return "10"
        return value.takeIf { it in TIMEOUTS }
    }

    fun timeoutInvalid(stored: String?): Boolean {
        val value = stored.orEmpty()
        return value.isNotEmpty() && value !in TIMEOUTS
    }

    fun shellReadyCommands(): List<ReadyCommand> {
        val seen = mutableSetOf<String>()
        return SuggestedShortcuts.all.mapNotNull { shortcut ->
            val action = shortcut.actions.singleOrNull() ?: return@mapNotNull null
            if (action.type != SHELL) return@mapNotNull null
            val command = action.params["command"] ?: return@mapNotNull null
            if (command !in SuggestedShortcuts.PREFILLED_PAYLOADS || !seen.add(command)) return@mapNotNull null
            ReadyCommand(shortcut.titleRes, command)
        }
    }

    fun extraRows(raw: String?): List<ExtraRow> {
        if (raw.isNullOrEmpty()) return emptyList()
        if (validateIntentExtras(raw) != null) return listOf(ExtraRow("", raw))
        return ActionPayloads.intentExtras(raw).map { ExtraRow(it.key, it.value) }
    }

    fun extraError(row: ExtraRow): String? {
        if (row.key.isEmpty() && row.value.isEmpty()) return null
        return validateIntentExtras(listOf(row.key to row.value))
    }

    fun serializeExtras(rows: List<ExtraRow>, validOnly: Boolean): String {
        val kept = rows.filter { it.key.isNotEmpty() || it.value.isNotEmpty() }
        val chosen = if (validOnly) kept.filter { extraError(it) == null } else kept
        return chosen.joinToString(";") { "${it.key}=${it.value}" }
    }

    fun extrasForRun(rows: List<ExtraRow>, original: String?): String {
        val meaningful = rows.filter { it.key.isNotEmpty() || it.value.isNotEmpty() }
        if (meaningful.size == 1 && meaningful[0].key.isEmpty() && original != null && meaningful[0].value == original) {
            return original
        }
        return meaningful.joinToString(";") { "${it.key}=${it.value}" }
    }

    fun targetInvalid(value: String?): Boolean {
        val stored = value.orEmpty()
        return stored.isNotEmpty() && stored !in TARGETS
    }

    fun settingProbe(namespace: String, advanced: Boolean): SettingProbe = when {
        namespace == "system" && advanced -> SettingProbe.BRIDGE
        namespace == "system" -> SettingProbe.APP
        advanced && namespace in NAMESPACES -> SettingProbe.BRIDGE
        else -> SettingProbe.BLOCKED
    }

    fun settingKeySafe(key: String): Boolean = SAFE_KEY.matches(key)

    /** Null unless both pieces are safe to place in `settings get`. Never built from a raw key. */
    fun bridgeGetCommand(namespace: String, key: String): String? {
        if (namespace !in NAMESPACES || !settingKeySafe(key)) return null
        return "settings get $namespace $key"
    }

    fun matchingSuggestions(namespace: String, query: String, label: (SettingSuggestion) -> String): List<SettingSuggestion> {
        val typed = query.trim()
        val pool = if (typed.isEmpty()) suggestions.filter { it.namespace == namespace } else suggestions
        if (typed.isEmpty()) return pool
        return pool.filter { suggestion ->
            suggestion.key.contains(typed, ignoreCase = true) || label(suggestion).contains(typed, ignoreCase = true)
        }
    }

    fun formatSeconds(durationMs: Long): String {
        val ms = durationMs.coerceAtLeast(0)
        if (ms >= 10_000) return "${ms / 1000} s"
        return String.format(Locale.US, "%.1f s", ms / 1000.0)
    }

    fun sendsBody(method: String): Boolean = method.equals("POST", true) || method.equals("PUT", true)

    fun saveEnabled(
        type: String,
        values: Map<String, String>,
        headerRows: List<HeaderRow> = emptyList(),
        extraRows: List<ExtraRow> = emptyList(),
        startsFromNotification: Boolean = false,
    ): Boolean {
        val form = ActionEditorForms.forType(type) ?: return false
        if (!ActionEditorForms.isComplete(form, values, startsFromNotification)) return false
        return when (type) {
            HTTP -> {
                val issue = urlIssue(values["url"].orEmpty())
                val methodOk = issue != UrlIssue.EMPTY && issue != UrlIssue.NOT_WEB && headerRows.none { headerError(it) }
                val bodyOk = !sendsBody(values["method"].orEmpty()) || (
                    !contentTypeInvalid(values["content_type"]) &&
                        !contentTypeConflicts(values["content_type"].orEmpty().ifEmpty { "none" }, headerRows)
                    )
                methodOk && bodyOk
            }
            SHELL -> !timeoutInvalid(values["timeout_sec"])
            INTENT -> extraRows.none { extraError(it) != null } && !targetInvalid(values["target"])
            SETTING -> values["namespace"].orEmpty().let { it.isEmpty() || it in NAMESPACES }
            else -> true
        }
    }

    fun confirmedParams(type: String, values: Map<String, String>): Map<String, String> {
        val out = values.filterValues { it.isNotEmpty() }.toMutableMap()
        if (type == HTTP && !sendsBody(out["method"].orEmpty())) out.remove("content_type")
        if (type == SHELL && out["timeout_sec"]?.let { it !in TIMEOUTS } == true) out.remove("timeout_sec")
        if (type == INTENT && out["target"]?.let { it !in TARGETS } == true) out.remove("target")
        return out
    }

    fun previewParams(type: String, values: Map<String, String>, headers: List<HeaderRow>, extras: List<ExtraRow>): Map<String, String> {
        val merged = values.toMutableMap()
        if (type == HTTP) {
            val raw = serializeHeaders(headers, validOnly = true)
            if (raw.isEmpty()) merged.remove("headers") else merged["headers"] = raw
        }
        if (type == INTENT) {
            val raw = serializeExtras(extras, validOnly = true)
            if (raw.isEmpty()) merged.remove("extras") else merged["extras"] = raw
        }
        return confirmedParams(type, merged)
    }

    fun savedParams(type: String, values: Map<String, String>, headers: List<HeaderRow>, extras: List<ExtraRow>): Map<String, String> {
        val merged = values.toMutableMap()
        if (type == HTTP) {
            val raw = serializeHeaders(headers, validOnly = false)
            if (raw.isEmpty()) merged.remove("headers") else merged["headers"] = raw
        }
        if (type == INTENT) {
            val raw = serializeExtras(extras, validOnly = false)
            if (raw.isEmpty()) merged.remove("extras") else merged["extras"] = raw
        }
        return confirmedParams(type, merged)
    }

    /** Null when a header row would be sent as a different header. The caller shows a failure. */
    fun testAction(
        type: String,
        values: Map<String, String>,
        headers: List<HeaderRow>,
        extras: List<ExtraRow>,
        headerOriginal: String?,
        extraOriginal: String?,
    ): ScriptAction? {
        val merged = values.toMutableMap()
        if (type == HTTP) {
            when (val raw = headersForRun(headers, headerOriginal)) {
                null -> return null
                "" -> merged.remove("headers")
                else -> merged["headers"] = raw
            }
            if (!sendsBody(merged["method"].orEmpty())) merged.remove("content_type")
        }
        if (type == INTENT) {
            val raw = extrasForRun(extras, extraOriginal)
            if (raw.isEmpty()) merged.remove("extras") else merged["extras"] = raw
        }
        return ScriptAction(type, merged.filterValues { it.isNotEmpty() })
    }
}
