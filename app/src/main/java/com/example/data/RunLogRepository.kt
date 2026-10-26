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
        internal const val SUCCESS_COUNT_KEY = "lifetime_successful_runs"
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

    // Existing history is a conservative lower bound; never infer successes from session triggers.
    private val _successfulRuns = MutableStateFlow(
        prefs.getLong(SUCCESS_COUNT_KEY, _runs.value.count { it.succeeded() }.toLong()),
    )
    val successfulRuns: StateFlow<Long> = _successfulRuns.asStateFlow()

    private fun RunRecord.succeeded(): Boolean = actions.isNotEmpty() && actions.all { it.ok }

    private fun load(): List<RunRecord> = prefs.getString(STORE_KEY, null)
        ?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull()?.runs }
        .orEmpty()

    @Synchronized
    fun append(record: RunRecord) {
        _runs.update { appendRun(it, record) }
        if (record.succeeded() && _successfulRuns.value < Long.MAX_VALUE) _successfulRuns.value += 1L
        persist()
    }

    @Synchronized
    fun forget(scriptId: String) {
        _runs.update { runs -> runs.filterNot { it.scriptId == scriptId } }
        persist()
    }

    private fun persist() {
        prefs.edit()
            .putString(STORE_KEY, adapter.toJson(PersistedRunLog(_runs.value)))
            .putLong(SUCCESS_COUNT_KEY, _successfulRuns.value)
            .apply()
    }
}
