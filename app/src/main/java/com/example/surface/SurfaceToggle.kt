package com.example.surface

import android.content.Context
import com.example.data.AppLocale
import com.example.data.EntitlementManager
import com.example.data.KeyMappingRepository
import com.example.data.ScriptRepository
import com.example.service.bridge.BridgeController
import io.github.nicholasarda.keysnap.R

/**
 * Turns a shortcut, or all of KeySnap, on or off from a widget or a Quick Settings tile, with the
 * same rules as the switches in the app.
 */
object SurfaceToggle {
    /** The binding that stands for every shortcut: the global switch on Home. */
    const val ALL = "keysnap:all"

    /** Null when the bound shortcut was deleted. */
    fun isOn(context: Context, target: String): Boolean? {
        val repo = ScriptRepository.getInstance(context)
        return if (target == ALL) repo.globallyEnabled.value else repo.scripts.value.firstOrNull { it.id == target }?.enabled
    }

    /** Flips [target]. Returns what to tell the user when it could not, or null. */
    fun flip(context: Context, target: String): String? {
        val app = context.applicationContext
        val repo = ScriptRepository.getInstance(app)
        val res = AppLocale.wrap(app).resources
        if (target == ALL) {
            val on = !repo.globallyEnabled.value
            KeyMappingRepository.getInstance(app).setEnabled(on)
            repo.setGloballyEnabled(on)
        } else {
            val script = repo.scripts.value.firstOrNull { it.id == target } ?: return res.getString(R.string.surface_missing)
            if (!script.enabled && !EntitlementManager.getInstance(app).mayEnableAnother()) {
                return res.getString(R.string.free_limit_toggle_blocked, EntitlementManager.FREE_SCRIPT_LIMIT)
            }
            repo.setScriptEnabled(target, !script.enabled)
        }
        // ponytail: reaches a running bridge only while this process has initialized BridgeController
        // (the Accessibility service does); otherwise the change lands when the app next publishes.
        BridgeController.publishConfig()
        ShortcutWidgetProvider.refreshAll(app)
        return null
    }
}
