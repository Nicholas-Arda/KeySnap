package com.example.service

import android.content.Context
import android.content.res.Resources
import android.widget.Toast
import com.example.data.AppLocale
import com.example.data.ShortcutScript
import com.example.data.isLive
import io.github.nicholasarda.keysnap.R

/**
 * What a tap on a home-screen widget or a Quick Settings tile (a "surface") came to. Everything but
 * a complete [Ran] is shown to the user, because a tap that silently does nothing reads as broken.
 */
sealed interface SurfaceRun {
    data object Missing : SurfaceRun
    data object GloballyPaused : SurfaceRun
    data class ScriptPaused(val name: String) : SurfaceRun
    data class ConditionsUnmet(val name: String) : SurfaceRun

    data class Ran(val name: String, val ran: Int, val total: Int) : SurfaceRun
}

/**
 * The key path's rules, applied to a tap: a global pause stops every script but one that can resume
 * mapping ([isLive]), a paused script stops only itself (which is also how the free limit shows up,
 * see `EntitlementManager.enforceLimit`),
 * and an unmet "Only if" list stops it, except on an automation, whose conditions are its trigger
 * rather than a filter. The "At most once every N minutes" condition ([cooldownAllows]) is a rate
 * limit rather than a trigger, so it stops a tap on an automation too. Null means run it.
 */
internal fun surfaceGate(
    script: ShortcutScript,
    globallyEnabled: Boolean,
    cooldownAllows: Boolean = true,
    conditionsMet: (ShortcutScript) -> Boolean,
): SurfaceRun? = when {
    !globallyEnabled && !script.isLive(globallyEnabled) -> SurfaceRun.GloballyPaused
    !script.enabled -> SurfaceRun.ScriptPaused(script.name)
    !cooldownAllows -> SurfaceRun.ConditionsUnmet(script.name)
    !script.automation && !conditionsMet(script) -> SurfaceRun.ConditionsUnmet(script.name)
    else -> null
}

/** Null when every action ran: a successful tap stays as quiet as a key press. */
fun SurfaceRun.message(res: Resources): String? = when (this) {
    SurfaceRun.Missing -> res.getString(R.string.surface_missing)
    SurfaceRun.GloballyPaused -> res.getString(R.string.surface_paused_global)
    is SurfaceRun.ScriptPaused -> res.getString(R.string.surface_paused_script, name)
    is SurfaceRun.ConditionsUnmet -> res.getString(R.string.surface_conditions_unmet, name)
    is SurfaceRun.Ran -> when {
        ran >= total -> null
        ran == 0 -> res.getString(R.string.run_nothing_ran)
        else -> res.getString(R.string.run_partial, ran, total)
    }
}

/** Android 11/12 have no per-app locale in the platform, so the text goes through [AppLocale.wrap]. */
fun showSurfaceRunToast(context: Context, run: SurfaceRun) {
    val text = run.message(AppLocale.wrap(context).resources) ?: return
    Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
}
