package com.example.service

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.example.data.ScriptActionCatalog
import com.example.data.TriggerPressType
import io.github.nicholasarda.keysnap.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Memory only. Callers must check [recording] before preparing diagnostic fields. */
object DiagnosticRecorder {
    enum class InputPath { ACCESSIBILITY, ACTIVITY, ADVANCED_MODE_BRIDGE }
    enum class Stage { ENTRY, COORDINATOR }
    enum class ActionPath { APP, ADVANCED_MODE_BRIDGE }
    enum class Failure { ACTION_FAILED, BRIDGE_ACTION_FAILED, EXCEPTION }
    enum class StopReason { USER, TIME_LIMIT, LINE_LIMIT }

    const val MAX_LINES = 500
    const val MAX_DURATION_MS = 10 * 60 * 1_000L

    @Volatile
    var recording: Boolean = false
        private set

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()
    private val _hasRecording = MutableStateFlow(false)
    val hasRecording = _hasRecording.asStateFlow()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val timeout = Runnable { synchronized(this) { if (recording) finish(StopReason.TIME_LIMIT) } }
    private var startedAt = 0L
    private var header = ""
    private val lines = mutableListOf<String>()
    private var stopReason: StopReason? = null

    // No script, arbitrary string, exception message, or action parameter can enter this API.
    @Synchronized
    fun start(accessibility: Boolean, advancedMode: Boolean, paused: Boolean, scriptCount: Int, consumeOriginal: Boolean) {
        clear()
        startedAt = SystemClock.elapsedRealtime()
        header = """
            KeySnap problem recording
            device=${Build.MANUFACTURER} ${Build.MODEL}
            android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}
            oemBuild=${Build.DISPLAY}
            app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            accessibility=$accessibility advancedMode=$advancedMode paused=$paused scripts=$scriptCount consumeOriginal=$consumeOriginal
            scriptId is a numeric fingerprint of the stored id; key names are Android constants.
        """.trimIndent()
        recording = true
        _hasRecording.value = true
        _isRecording.value = true
        handler.postDelayed(timeout, MAX_DURATION_MS)
    }

    @Synchronized
    fun stop() {
        if (recording) finish(if (elapsed() >= MAX_DURATION_MS) StopReason.TIME_LIMIT else StopReason.USER)
    }

    @Synchronized
    fun checkTimeout() {
        if (recording && elapsed() >= MAX_DURATION_MS) finish(StopReason.TIME_LIMIT)
    }

    @Synchronized
    fun clear() {
        recording = false
        _isRecording.value = false
        handler.removeCallbacks(timeout)
        header = ""
        lines.clear()
        stopReason = null
        _hasRecording.value = false
    }

    fun keyEvent(
        path: InputPath,
        stage: Stage,
        keyCode: Int,
        isDown: Boolean,
        repeatCount: Int? = null,
        flags: Int? = null,
        source: Int? = null,
        deviceId: Int? = null,
        deduplicated: Boolean? = null,
        matched: Boolean? = null,
        consumed: Boolean? = null,
    ) {
        if (!recording) return
        synchronized(this) {
            append(buildString {
                // Typing keys are masked: a recording left on while the user types elsewhere on a
                // hardware keyboard must not spell out a password.
                val key = if (isTextKey(keyCode)) "code=TEXT_KEY" else "code=$keyCode name=${KeyEvent.keyCodeToString(keyCode)}"
                append("key path=$path stage=$stage $key ${if (isDown) "DOWN" else "UP"}")
                repeatCount?.let { append(" repeat=$it") }
                flags?.let { append(" flags=0x${it.toUInt().toString(16)}") }
                source?.let { append(" source=0x${it.toUInt().toString(16)}") }
                deviceId?.let { append(" deviceId=$it") }
                deduplicated?.let { append(" deduplicated=$it") }
                matched?.let { append(" matched=$it") }
                consumed?.let { append(" consumed=$it") }
            })
        }
    }

    fun gesture(scriptId: Int?, keyCode: Int, pressType: TriggerPressType?, path: InputPath, matched: Boolean) {
        if (!recording) return
        synchronized(this) {
            append("gesture path=$path scriptId=$scriptId code=$keyCode press=$pressType matched=$matched")
        }
    }

    /** [actionType] indexes the fixed catalog; an unknown id never renders caller text. */
    fun action(scriptId: Int, pressType: TriggerPressType?, actionType: Int, ok: Boolean, failure: Failure? = null, path: ActionPath = ActionPath.APP) {
        if (!recording) return
        synchronized(this) {
            val type = ScriptActionCatalog.all.getOrNull(actionType)?.type ?: "UNKNOWN"
            append("action scriptId=$scriptId press=$pressType type=$type result=${if (ok) "ok" else "failed"} failure=${if (ok) "NONE" else failure ?: Failure.ACTION_FAILED} path=$path")
        }
    }

    @Synchronized
    fun render(): String {
        checkTimeout()
        if (header.isEmpty()) return ""
        return buildString {
            appendLine(header)
            lines.forEach { appendLine(it) }
            append("status=${if (recording) "RECORDING" else "STOPPED"}")
            stopReason?.let { append(" reason=$it") }
        }
    }

    private val TEXT_EDIT_KEYS = setOf(
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_NUMPAD_ENTER,
    )

    // Fixed ranges rather than KeyEvent.isPrintingKey, whose answer depends on the device's key map.
    internal fun isTextKey(keyCode: Int): Boolean =
        keyCode in TEXT_EDIT_KEYS ||
            keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_POUND ||
            keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_PERIOD ||
            keyCode in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_AT ||
            keyCode == KeyEvent.KEYCODE_PLUS ||
            keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN

    private fun elapsed() = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)

    private fun append(line: String) {
        if (!recording) return
        val elapsed = elapsed()
        if (elapsed >= MAX_DURATION_MS) {
            finish(StopReason.TIME_LIMIT)
            return
        }
        lines.add("+${elapsed}ms $line")
        if (lines.size >= MAX_LINES) finish(StopReason.LINE_LIMIT)
    }

    private fun finish(reason: StopReason) {
        recording = false
        stopReason = reason
        handler.removeCallbacks(timeout)
        _isRecording.value = false
    }
}
