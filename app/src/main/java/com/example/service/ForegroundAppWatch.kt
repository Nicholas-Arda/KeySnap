package com.example.service

import com.example.data.ShortcutScript
import com.example.data.isLive
import kotlinx.coroutines.delay

/**
 * The foreground app has no broadcast, so an automation reading it was re-checked only when some
 * unrelated broadcast happened to arrive. [HardwareKeyTriggerCoordinator] runs [poll] only while
 * [shouldRun] holds (screen on, and a live automation reads the foreground), and re-evaluates
 * automations when the foreground changes. What is read stays in memory: never logged or stored.
 */
internal object ForegroundAppWatch {
    /**
     * ponytail: a 2 s poll = 30 usage-events queries a minute while it runs, none while the screen
     * is off or no automation reads the foreground; an app switch fires up to 2 s late.
     */
    const val POLL_MS = 2_000L

    /** All three read the same usage-events foreground state, so one watch covers them. */
    private val FOREGROUND_TYPES = setOf("app_in_foreground", "app_not_in_foreground", "no_app_open")

    /** Paused (or disabled), an automation still tracks its edge silently and is re-seeded on resume, so it needs no watch. */
    fun shouldRun(screenOn: Boolean, scripts: List<ShortcutScript>, globallyEnabled: Boolean): Boolean =
        screenOn && scripts.any { script ->
            script.automation && script.isLive(globallyEnabled) && script.constraints.any { it.type in FOREGROUND_TYPES }
        }

    /** Calls [changed] each time [read] returns something other than the previous read. Runs until cancelled. */
    suspend fun poll(read: suspend () -> Any?, changed: () -> Unit) {
        var last = read()
        while (true) {
            delay(POLL_MS)
            val now = read()
            if (now != last) {
                last = now
                changed()
            }
        }
    }
}
