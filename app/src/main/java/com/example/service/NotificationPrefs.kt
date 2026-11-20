package com.example.service

import android.content.Context

/**
 * Settings → Notifications: which of KeySnap's own notifications it may post, and the default words
 * for the two whose words are the user's. A switch here means "KeySnap does not send it at all";
 * Android's per-channel settings still apply on top. `{name}` stands for the shortcut's name.
 */
object NotificationPrefs {
    enum class Kind(val key: String) { FAILURES("failures"), AUTOMATION_RUNS("automation_runs"), SHORTCUT_ACTION("shortcut_action"), AUTOSTART("autostart") }

    const val RUN_TITLE = "run_title"
    const val RUN_TEXT = "run_text"
    const val UNDO_TITLE = "undo_title"
    const val UNDO_TEXT = "undo_text"
    const val PLACE_TITLE = "place_title"
    const val PLACE_TEXT = "place_text"
    const val NAME = "{name}"

    private fun prefs(context: Context) = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    fun enabled(context: Context, kind: Kind): Boolean = prefs(context).getBoolean("on_${kind.key}", true)

    fun setEnabled(context: Context, kind: Kind, on: Boolean) = prefs(context).edit().putBoolean("on_${kind.key}", on).apply()

    /** The user's default words for [key], trimmed; empty when they kept the built-in ones. */
    fun text(context: Context, key: String): String = prefs(context).getString(key, null)?.trim().orEmpty()

    fun setText(context: Context, key: String, value: String) = prefs(context).edit().putString(key, value.trim()).apply()
}
