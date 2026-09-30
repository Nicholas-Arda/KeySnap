package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Every shortcut's recent runs, newest first, bounded by [appendRun]. Its own preferences file, so
 * a write here never rewrites the shortcuts. A new store rather than a change to
 * `shortcut_scripts_v1`, so there is nothing to migrate: missing or unreadable starts empty.
 */
class RunLogRepository internal constructor(
    private val prefs: SharedPreferences,
    moshi: Moshi,
) {
    companion object {
        internal const val PREFS_NAME = "keysnap_run_log"
        internal const val STORE_KEY = "runs_v1"
        @Volatile private var instance: RunLogRepository? = null

        fun getInstance(context: Context): RunLogRepository = instance ?: synchronized(this) {
            instance ?: RunLogRepository(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                Moshi.Builder().build(),
            ).also { instance = it }
        }
    }

    private val adapter = moshi.adapter(PersistedRunLog::class.java)
    private val _runs = MutableStateFlow(load())
    val runs: StateFlow<List<RunRecord>> = _runs.asStateFlow()

    private fun load(): List<RunRecord> = prefs.getString(STORE_KEY, null)
        ?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull()?.runs }
        .orEmpty()

    fun append(record: RunRecord) {
        _runs.update { appendRun(it, record) }
        persist()
    }

    fun forget(scriptId: String) {
        _runs.update { runs -> runs.filterNot { it.scriptId == scriptId } }
        persist()
    }

    private fun persist() {
        prefs.edit().putString(STORE_KEY, adapter.toJson(PersistedRunLog(_runs.value))).apply()
    }
}
