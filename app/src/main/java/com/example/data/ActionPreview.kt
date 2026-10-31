package com.example.data

import com.example.bridge.ActionPayloads

enum class ShellContext { ADVANCED, APP_LIMITED }

/** The current execution policy, not a permission or a selectable mode. */
fun shellRunsIn(advancedActive: Boolean): ShellContext =
    if (advancedActive) ShellContext.ADVANCED else ShellContext.APP_LIMITED

/** Error codes only: never echo a user's values into an error or telemetry. */
fun validateIntentExtras(raw: String): String? = try {
    ActionPayloads.intentExtras(raw)
    null
} catch (_: IllegalArgumentException) {
    "invalid_extras"
}

/** Use BEFORE joining editor rows: serialized `a=b;c=d` cannot reveal an intended `b;c=d` value. */
fun validateIntentExtras(entries: List<Pair<String, String>>): String? =
    entries.firstNotNullOfOrNull { (key, value) -> ActionPayloads.validateIntentExtra(key, value) }

/** Invalid/incomplete forms have no executable preview. Header values never appear. */
fun previewCommand(action: ScriptAction): String = try {
    ActionPayloads.previewCommand(action.type, action.params)
} catch (_: IllegalArgumentException) {
    ""
}
