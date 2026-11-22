package com.example.surface

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.example.data.RunRecord
import com.example.service.HardwareKeyTriggerCoordinator
import com.example.service.SurfaceRun
import com.example.service.WidgetLaunchGrant
import com.example.service.showSurfaceRunToast
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The only way a widget tap reaches a shortcut, to run it or to switch it on or off. Not exported: the widget provider has to be, and a
 * run action there would let any installed app fire the user's shortcuts, shell commands and HTTP
 * requests included.
 */
class ScriptRunReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LIST_PAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val app = context.applicationContext
            ShortcutListWidget.turnPage(app, widgetId, intent.getIntExtra(EXTRA_PAGE_STEP, 0))
            return
        }
        val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID) ?: return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val app = context.applicationContext
        // The list widget only switches, never runs.
        if (intent.action == ACTION_LIST_TOGGLE) {
            SurfaceToggle.flip(app, scriptId)?.let {
                Toast.makeText(app, it, Toast.LENGTH_SHORT).show()
                // The switch already moved on the home screen; put it back.
                ShortcutListWidget.refreshAll(app)
            }
            return
        }
        if (intent.getBooleanExtra(EXTRA_TOGGLE, false)) {
            SurfaceToggle.flip(app, scriptId)?.let { Toast.makeText(app, it, Toast.LENGTH_SHORT).show() }
            return
        }
        // This tap's broadcast lets the run open screens for about 10 s, held or not; a tile tap gets none.
        WidgetLaunchGrant.open(SystemClock.elapsedRealtime())
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
        private const val EXTRA_TOGGLE = "com.example.surface.TOGGLE"
        // Under the 10 s foreground-broadcast timeout, so a held broadcast never ANRs.
        private const val BROADCAST_BUDGET_MS = 9_000L

        private const val ACTION_LIST_TOGGLE = "com.example.surface.LIST_TOGGLE"

        /** The list widget's Live/Paused button, or one row's switch. */
        fun listIntent(context: Context, appWidgetId: Int, scriptId: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            Intent(context, ScriptRunReceiver::class.java).setAction(ACTION_LIST_TOGGLE).setIdentifier("fixed:$scriptId")
                .putExtra(EXTRA_SCRIPT_ID, scriptId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private const val ACTION_LIST_PAGE = "com.example.surface.LIST_PAGE"
        private const val EXTRA_PAGE_STEP = "com.example.surface.PAGE_STEP"

        /** The list widget's arrows: [step] is -1 or +1; the page wraps around. */
        fun pageIntent(context: Context, appWidgetId: Int, step: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            Intent(context, ScriptRunReceiver::class.java).setAction(ACTION_LIST_PAGE).setIdentifier("page:$step")
                .putExtra(EXTRA_PAGE_STEP, step)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** The widget id is the request code: with a shared one, every widget would run the last-bound shortcut. */
        fun pendingIntent(context: Context, appWidgetId: Int, scriptId: String, toggle: Boolean = false): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, ScriptRunReceiver::class.java)
                    .putExtra(EXTRA_SCRIPT_ID, scriptId)
                    .putExtra(EXTRA_TOGGLE, toggle)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
