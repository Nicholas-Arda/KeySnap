package com.example.surface

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.data.RunRecord
import com.example.service.HardwareKeyTriggerCoordinator
import com.example.service.SurfaceRun
import com.example.service.showSurfaceRunToast
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The only way a widget tap reaches a shortcut. Not exported: the widget provider has to be, and a
 * run action there would let any installed app fire the user's shortcuts, shell commands and HTTP
 * requests included.
 */
class ScriptRunReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID) ?: return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val app = context.applicationContext
        // Past the free widget limit (Pro refunded after it was placed): refuse and say why.
        if (!SurfaceBindings.widgetAllowed(app, widgetId)) {
            showSurfaceRunToast(app, SurfaceRun.WidgetOverLimit)
            return
        }
        val pending = goAsync()
        val released = AtomicBoolean(false)
        val release = Runnable { if (released.compareAndSet(false, true)) pending.finish() }
        // ponytail: keeps the broadcast, and so the process, protected for at most 9 s; a longer
        // chain keeps running in the coordinator's scope unprotected. A short foreground service is
        // the upgrade if long widget chains are reported cut off.
        Handler(Looper.getMainLooper()).postDelayed(release, BROADCAST_BUDGET_MS)
        HardwareKeyTriggerCoordinator.runFromSurface(app, scriptId, RunRecord.SOURCE_WIDGET) { outcome ->
            if (outcome == SurfaceRun.Missing && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Redrawn as "Choose a shortcut", so the next tap opens the picker.
                ShortcutWidgetProvider.render(app, widgetId)
            }
            showSurfaceRunToast(app, outcome)
            release.run()
        }
    }

    companion object {
        internal const val EXTRA_SCRIPT_ID = "com.example.surface.SCRIPT_ID"
        private const val BROADCAST_BUDGET_MS = 9_000L

        /** The widget id is the request code: with a shared one, every widget would run the last-bound shortcut. */
        fun pendingIntent(context: Context, appWidgetId: Int, scriptId: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, ScriptRunReceiver::class.java)
                    .putExtra(EXTRA_SCRIPT_ID, scriptId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
