package com.example.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import io.github.nicholasarda.keysnap.R

/**
 * Phone makers whose own "Auto launch" / "Autostart" switch decides whether a killed app may be
 * started again, and the screens that hold that switch. Only ColorOS was checked on a device
 * (2026-11-17): with the switch off, a swipe from Recents leaves the Accessibility service dead
 * ("prevent restart service") and alarms are dropped; with it on, the service is back in a second.
 * It gets no direct screen and goes straight to Settings search: its page is guarded by a signature
 * permission (PLG110), and on some builds a page opens and closes itself at once, which counts as a
 * successful start and so never reached the search (Reddit report, 2026-11-20). The others come from
 * public OEM lists and are tried best-effort.
 */
private val AUTO_LAUNCH_SCREENS: Map<String, List<String>> = run {
    val colorOs = emptyList<String>()
    val miui = listOf("com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity")
    val vivo = listOf(
        "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity",
        "com.iqoo.secure/.ui.phoneoptimize.BgStartUpManager",
    )
    mapOf(
        "oppo" to colorOs, "oneplus" to colorOs, "realme" to colorOs,
        "xiaomi" to miui, "redmi" to miui, "poco" to miui,
        "vivo" to vivo, "iqoo" to vivo,
        "huawei" to listOf("com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity"),
        "honor" to listOf("com.hihonor.systemmanager/.startupmgr.ui.StartupNormalAppListActivity"),
        "asus" to listOf("com.asus.mobilemanager/.autostart.AutoStartActivity"),
    )
}

internal fun hasAutoLaunchSwitch(manufacturer: String): Boolean = manufacturer.lowercase() in AUTO_LAUNCH_SCREENS

/** The OEM screen if this app may open it, else Settings search with a hint of what to type. */
internal fun openAutoLaunchSettings(context: Context, manufacturer: String) {
    val screens = AUTO_LAUNCH_SCREENS[manufacturer.lowercase()].orEmpty()
    for (screen in screens) {
        val intent = Intent().setComponent(ComponentName.unflattenFromString(screen)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // ActivityNotFoundException or SecurityException: a guarded or missing screen, try the next.
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    val search = Intent(Settings.ACTION_APP_SEARCH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(search) }.isSuccess ||
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (opened) Toast.makeText(context, R.string.auto_launch_search_hint, Toast.LENGTH_LONG).show()
}
