package com.example.surface

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.example.data.AppLocale
import com.example.data.ScriptRepository
import io.github.nicholasarda.keysnap.R

/**
 * Draws the one-tap shortcut widget. Exported because the system delivers widget broadcasts to it;
 * it only draws, and the run itself goes through [ScriptRunReceiver].
 */
class ShortcutWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // This receiver is exported, so any app can send APPWIDGET_DELETED. A widget the system
        // really removed is already gone from getAppWidgetIds; keep the binding of one that isn't.
        val live = AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, ShortcutWidgetProvider::class.java))
            ?: IntArray(0)
        appWidgetIds.filterNot { it in live }.forEach { SurfaceBindings.unbindWidget(context, it) }
    }

    companion object {
        /** The bound shortcut's name, or "Choose a shortcut" (and a tap that opens the picker) once it is gone. */
        fun render(context: Context, appWidgetId: Int) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val script = SurfaceBindings.widgetScript(context, appWidgetId)?.let { id ->
                ScriptRepository.getInstance(context).scripts.value.firstOrNull { it.id == id }
            }
            val label = script?.name ?: AppLocale.wrap(context).getString(R.string.surface_choose_shortcut)
            val click = if (script != null) {
                ScriptRunReceiver.pendingIntent(context, appWidgetId, script.id)
            } else {
                ShortcutPickerActivity.widgetPendingIntent(context, appWidgetId)
            }
            val views = RemoteViews(context.packageName, R.layout.widget_shortcut).apply {
                setTextViewText(R.id.widget_label, label)
                setContentDescription(R.id.widget_root, label)
                setOnClickPendingIntent(R.id.widget_root, click)
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        /** Called on every script-list change, so a rename or a delete shows on the home screen. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, ShortcutWidgetProvider::class.java))
                .forEach { render(context, it) }
        }
    }
}
