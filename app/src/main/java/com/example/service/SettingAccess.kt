package com.example.service

import android.content.Context
import android.provider.Settings
import com.example.bridge.ActionTestResult
import com.example.data.PrivilegedEditor
import com.example.data.ScriptAction
import com.example.data.SettingProbe

/**
 * Live setting reads and the editor's System-namespace Test write. Values stay in memory for the
 * open sheet. This file never logs a key or a value.
 */
object SettingAccess {
    fun readSystem(context: Context, key: String): String? {
        if (key.isEmpty()) return null
        return try {
            Settings.System.getString(context.contentResolver, key) ?: ""
        } catch (_: SecurityException) {
            null
        }
    }

    fun writeSystem(context: Context, key: String, value: String): ActionTestResult {
        val start = System.nanoTime()
        if (key.isEmpty() || !Settings.System.canWrite(context)) return ActionTestResult.outcome(false, start)
        val ok = try {
            Settings.System.putString(context.contentResolver, key, value)
        } catch (_: SecurityException) {
            false
        }
        return ActionTestResult.outcome(ok, start)
    }

    /**
     * [advanced] selects the bridge. Secure and global without it return null, meaning unknown,
     * so the sheet shows no value. "" means the read succeeded and the setting is absent.
     * A bridge read only runs for a key [PrivilegedEditor.bridgeGetCommand] accepts.
     */
    suspend fun read(
        context: Context,
        namespace: String,
        key: String,
        advanced: Boolean,
        run: suspend (ScriptAction) -> ActionTestResult,
    ): String? {
        if (key.isEmpty()) return null
        return when (PrivilegedEditor.settingProbe(namespace, advanced)) {
            SettingProbe.BLOCKED -> null
            SettingProbe.APP -> readSystem(context, key)
            SettingProbe.BRIDGE -> {
                val command = PrivilegedEditor.bridgeGetCommand(namespace, key)
                    ?: return if (namespace == "system") readSystem(context, key) else null
                val result = run(ScriptAction(PrivilegedEditor.SHELL, mapOf("command" to command, "timeout_sec" to "5")))
                if (!result.ok) return null
                val line = result.output.firstOrNull().orEmpty()
                if (line == "null") "" else line
            }
        }
    }
}
