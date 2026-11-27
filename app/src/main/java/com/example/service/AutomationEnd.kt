package com.example.service

import android.content.Context

/**
 * "When it ends": the automations that ran and still owe their end actions. Kept in prefs, like
 * [AutomationUndo], because the If can end hours later in a process that never saw it start.
 */
internal object AutomationEnd {
    private const val PREFS = "automation_end"
    private const val KEY = "started"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pendingIds(context: Context): Set<String> = prefs(context).getStringSet(KEY, null).orEmpty()

    fun start(context: Context, id: String) = write(context, pendingIds(context) + id)

    fun forget(context: Context, id: String) = write(context, pendingIds(context) - id)

    /** True once per run: the end is owed, and is now taken. */
    fun take(context: Context, id: String): Boolean {
        if (id !in pendingIds(context)) return false
        forget(context, id)
        return true
    }

    private fun write(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY, ids).commit()
    }
}
