package com.example.service

import android.content.ComponentName
import android.content.Context
import android.os.Process
import com.example.data.ScriptAction
import com.example.service.bridge.BridgeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

enum class ReconnectResult { CONNECTED, NO_BRIDGE, FAILED }

/**
 * Turns only KeySnap's own Accessibility entry off and on again, the same write Android's switch makes.
 * The whole sequence runs inside one shell on the bridge, so the app dying halfway cannot leave the
 * entry removed. It never adds an entry the user removed: no entry, no change (exit 3). Android
 * recomputes accessibility_enabled from the list itself, so the script never writes it.
 */
internal fun accessibilityReconnectCommand(component: ComponentName, userId: Int, enable: Boolean = false): String {
    val full = component.flattenToString()
    val short = component.flattenToShortString()
    // Only package/class characters reach the shell; anything else means no command at all.
    val safe = Regex("[A-Za-z0-9_.$/]+")
    require(full.matches(safe) && short.matches(safe) && userId >= 0)
    val get = "settings --user $userId get secure enabled_accessibility_services"
    val put = "settings --user $userId put secure enabled_accessibility_services"
    return listOf(
        "set -f",
        "C='$full'; S='$short'",
        // A failed read or anything that is not a component list is never written back.
        "ok() { case \"\$1\" in *[!A-Za-z0-9_.\$/:]*) return 1;; esac; }",
        "l=\$($get) && ok \"\$l\" || exit 6; n=; f=0; IFS=:",
        "for s in \$l; do if [ \"\$s\" = \"\$C\" ] || [ \"\$s\" = \"\$S\" ]; then f=1; else n=\"\${n:+\$n:}\$s\"; fi; done",
        // [enable] comes only from the user's own tap on the in-app disclosure: add the missing entry.
        if (enable) "unset IFS; if [ \$f = 0 ]; then [ \"\$l\" = null ] && l=; $put \"\${l:+\$l:}\$C\" || exit 4; exit 0; fi"
        else "unset IFS; [ \$f = 1 ] || exit 3",
        "if [ -n \"\$n\" ]; then $put \"\$n\"; else settings --user $userId delete secure enabled_accessibility_services; fi || exit 4",
        // Android must see the entry gone, or it never drops the crashed binding.
        "sleep 1",
        // Re-read: anything another app changed in that second is kept.
        "l=\$($get) && ok \"\$l\" || exit 5; [ \"\$l\" = null ] && l=",
        "case \":\$l:\" in *\":\$C:\"*|*\":\$S:\"*) ;; *) $put \"\${l:+\$l:}\$C\" || exit 5;; esac",
        "exit 0",
    ).joinToString("\n")
}

object AccessibilityReconnect {
    private val lock = Mutex()

    /** User-initiated only. Success means the framework connection came back, not the shell's exit 0. */
    suspend fun run(context: Context, enable: Boolean = false): ReconnectResult {
        if (!lock.tryLock()) return ReconnectResult.FAILED
        try {
            val command = accessibilityReconnectCommand(
                ComponentName(context, KeyMapperAccessibilityService::class.java),
                Process.myUid() / 100_000,
                enable,
            )
            // The script takes about a second; the cap only guards a bridge that stops answering.
            val result = withTimeoutOrNull(15_000) {
                runCatching { BridgeController.runActions(listOf(ScriptAction("shell_command", mapOf("command" to command)))) }.getOrNull()
            } ?: return ReconnectResult.NO_BRIDGE
            if (result.ran != 1) return ReconnectResult.FAILED
            repeat(20) {
                if (KeyMapperAccessibilityService.refreshConnectionStatus()) return ReconnectResult.CONNECTED
                delay(250)
            }
            return ReconnectResult.FAILED
        } finally {
            lock.unlock()
        }
    }
}

/** ColorOS (OPPO, OnePlus, realme) shows a "Turn off / Keep it on" dialog after the entry is re-added. */
internal fun reconnectWarnsAfterwards(manufacturer: String): Boolean =
    manufacturer.lowercase() in setOf("oppo", "oneplus", "realme")

/**
 * The same phones turn Accessibility off when KeySnap is swiped out of Recents.
 * Home shows a lock tip until the user dismisses it.
 */
internal fun showColorOsRecentsLockTip(manufacturer: String, dismissed: Boolean): Boolean =
    !dismissed && reconnectWarnsAfterwards(manufacturer)
