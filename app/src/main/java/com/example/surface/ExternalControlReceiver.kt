package com.example.surface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.ScriptRepository
import com.example.service.bridge.BridgeController
import io.github.nicholasarda.keysnap.R

/**
 * "Control from other apps": another app or adb pauses, resumes or toggles the global mapping.
 * Exported without a permission because automation apps cannot declare one of ours; the off-by-default
 * switch is the gate, and the only reachable state is the one "Resume/Toggle mapping" and the app
 * already change. It never runs a shortcut.
 */
class ExternalControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isEnabled(context)) return
        val repository = ScriptRepository.getInstance(context)
        val enabled = when (intent.action) {
            action(context, PAUSE) -> false
            action(context, RESUME) -> true
            action(context, TOGGLE) -> !repository.globallyEnabled.value
            else -> return
        }
        repository.setGloballyEnabled(enabled)
        // A cold process (no Activity) has no observer pushing the change to a running bridge.
        BridgeController.publishConfigIfInstalled(context)
        Toast.makeText(
            context,
            if (enabled) R.string.external_control_resumed else R.string.external_control_paused,
            Toast.LENGTH_SHORT,
        ).show()
    }

    companion object {
        const val PAUSE = "PAUSE"
        const val RESUME = "RESUME"
        const val TOGGLE = "TOGGLE"
        private const val PREFS = "external_control"
        private const val KEY_ENABLED = "enabled"

        /** The broadcast action; the manifest declares the same `${applicationId}.<verb>` names. */
        fun action(context: Context, verb: String) = "${context.packageName}.$verb"

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}
