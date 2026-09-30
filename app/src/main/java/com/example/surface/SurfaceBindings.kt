package com.example.surface

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.data.EntitlementManager

/**
 * Which shortcut each widget and each tile runs, by script id. A file of its own so the script
 * store's schema (`shortcut_scripts_v1`) is untouched. Backed up like the scripts; widget ids change
 * on a restore, so a restored widget simply asks for its shortcut again.
 */
object SurfaceBindings {
    private const val PREFS = "arda_mapper_surfaces"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun widgetScript(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("widget_$appWidgetId", null)

    fun bindWidget(context: Context, appWidgetId: Int, scriptId: String) =
        prefs(context).edit().putString("widget_$appWidgetId", scriptId).apply()

    fun unbindWidget(context: Context, appWidgetId: Int) =
        prefs(context).edit().remove("widget_$appWidgetId").apply()

    fun tileScript(context: Context, slot: Int): String? = prefs(context).getString("tile_$slot", null)

    fun bindTile(context: Context, slot: Int, scriptId: String) =
        prefs(context).edit().putString("tile_$slot", scriptId).apply()

    /**
     * The free widget limit for [appWidgetId], see [EntitlementManager.widgetAllowed]. Only widgets
     * still on the home screen count: a restore can leave stale ids in this file.
     */
    fun widgetAllowed(context: Context, appWidgetId: Int): Boolean {
        val live = AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, ShortcutWidgetProvider::class.java))
            ?: intArrayOf()
        val bound = live.filter { widgetScript(context, it) != null }
        return EntitlementManager.getInstance(context).widgetAllowed(appWidgetId, bound)
    }
}
