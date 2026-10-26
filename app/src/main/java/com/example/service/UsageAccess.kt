package com.example.service

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import com.example.data.ShortcutScript

/** One permission check for consumption, the bridge config and permission prompts. */
fun hasUsageAccess(context: Context): Boolean = try {
    context.getSystemService(AppOpsManager::class.java)?.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    ) == AppOpsManager.MODE_ALLOWED
} catch (_: Exception) {
    false
}

internal fun needsUsageAccess(scripts: List<ShortcutScript>): Boolean = scripts.any { script ->
    script.enabled && script.triggers.isNotEmpty() && script.constraints.any {
        it.type == "no_app_open" || it.type == "app_in_foreground" || it.type == "app_not_in_foreground"
    }
}
