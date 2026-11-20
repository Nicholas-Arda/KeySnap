package com.example.service

import android.content.Context
import android.provider.Settings

/**
 * keep_screen_awake: the screen-off timeout set to "never", and the user's own timeout put back
 * when it is turned off. The previous timeout lives in the app's prefs, not with the bridge, whose
 * files go when Advanced Mode is switched off. The power button still turns the screen off.
 */
object KeepAwake {
    /** What Android stores for "never" (ColorOS 17 honours it, PLG110 2026-11-18). */
    const val NEVER = Int.MAX_VALUE
    private const val FALLBACK_MS = 30_000
    private const val PREFS = "keep_awake"
    private const val SAVED = "previous_timeout_ms"

    /** The timeout to write and the one to remember, or null when nothing changes. */
    internal data class Step(val write: Int, val remember: Int?)

    internal fun step(state: String?, current: Int, saved: Int?): Step? {
        val awake = current == NEVER
        val turnOn = when (state) {
            "on" -> true
            "off" -> false
            else -> !awake
        }
        return when {
            turnOn && !awake -> Step(NEVER, current)
            !turnOn && awake -> Step(saved?.takeIf { it != NEVER } ?: FALLBACK_MS, null)
            else -> null
        }
    }

    fun apply(context: Context, state: String?): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val resolver = context.contentResolver
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_MS)
        val saved = prefs.getInt(SAVED, -1).takeIf { it > 0 }
        val step = step(state, current, saved) ?: return true
        if (!Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, step.write)) return false
        prefs.edit().apply { if (step.remember != null) putInt(SAVED, step.remember) else remove(SAVED) }.apply()
        return true
    }
}
