package com.example.surface

import android.content.Context

/**
 * Which shortcut each widget and each tile runs or switches, by script id ([SurfaceToggle.ALL] for all of them). A file of its own so the script
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
        prefs(context).edit().remove("widget_$appWidgetId").remove("widget_style_$appWidgetId")
            .remove("widget_toggle_$appWidgetId").remove("list_live_$appWidgetId").remove("list_keys_$appWidgetId")
            .remove("list_header_$appWidgetId").remove("list_page_$appWidgetId")
            .remove("list_alt_$appWidgetId").apply()

    /** Whether a tap turns the bound shortcut on or off instead of running it; always for [SurfaceToggle.ALL]. */
    fun widgetToggles(context: Context, appWidgetId: Int): Boolean =
        widgetScript(context, appWidgetId) == SurfaceToggle.ALL || prefs(context).getBoolean("widget_toggle_$appWidgetId", false)

    fun setWidgetToggles(context: Context, appWidgetId: Int, toggles: Boolean) =
        prefs(context).edit().putBoolean("widget_toggle_$appWidgetId", toggles).apply()

    /** Null for a widget placed before styles existed, which keeps its original look. */
    fun widgetStyle(context: Context, appWidgetId: Int): WidgetStyle? =
        WidgetStyle.decode(prefs(context).getString("widget_style_$appWidgetId", null))

    fun setWidgetStyle(context: Context, appWidgetId: Int, style: WidgetStyle) =
        prefs(context).edit().putString("widget_style_$appWidgetId", style.encode()).apply()

    /** The list widget's [ListParts]. */
    fun listParts(context: Context, appWidgetId: Int): ListParts = prefs(context).let {
        ListParts(
            live = it.getBoolean("list_live_$appWidgetId", true),
            keys = it.getBoolean("list_keys_$appWidgetId", true),
            header = it.getBoolean("list_header_$appWidgetId", true),
        )
    }

    fun setListParts(context: Context, appWidgetId: Int, parts: ListParts) = prefs(context).edit()
        .putBoolean("list_live_$appWidgetId", parts.live).putBoolean("list_keys_$appWidgetId", parts.keys)
        .putBoolean("list_header_$appWidgetId", parts.header).apply()

    /** The list widget's page, unbounded: each size wraps it to its own page count. */
    fun listPage(context: Context, appWidgetId: Int): Int = prefs(context).getInt("list_page_$appWidgetId", 0)

    fun setListPage(context: Context, appWidgetId: Int, page: Int) =
        prefs(context).edit().putInt("list_page_$appWidgetId", page).apply()

    /** Flips and returns which copy of a page-turn layout the list widget draws next. */
    fun flipListAlt(context: Context, appWidgetId: Int): Boolean {
        val alt = !prefs(context).getBoolean("list_alt_$appWidgetId", false)
        prefs(context).edit().putBoolean("list_alt_$appWidgetId", alt).apply()
        return alt
    }

    fun tileScript(context: Context, slot: Int): String? = prefs(context).getString("tile_$slot", null)

    fun bindTile(context: Context, slot: Int, scriptId: String, toggles: Boolean = false, icon: String? = null) =
        prefs(context).edit().putString("tile_$slot", scriptId).putBoolean("tile_toggle_$slot", toggles)
            .putString("tile_icon_$slot", icon).apply()

    /** The action type whose icon the tile wears instead of its shortcut's own; null for the shortcut's. */
    fun tileIcon(context: Context, slot: Int): String? = prefs(context).getString("tile_icon_$slot", null)

    /** Whether the tile is in Quick Settings, as its add and remove callbacks reported. */
    fun tileAdded(context: Context, slot: Int): Boolean = prefs(context).getBoolean("tile_added_$slot", false)

    fun setTileAdded(context: Context, slot: Int, added: Boolean) =
        prefs(context).edit().putBoolean("tile_added_$slot", added).apply()

    /**
     * Runs once, for tiles placed before 1.3.1 reported adds: a tile given a shortcut counts as added,
     * so it is never hidden (that would remove it). A blank one counts as free; hiding it loses nothing.
     */
    fun initTileSlots(context: Context, slots: IntRange) {
        val p = prefs(context)
        if (p.getBoolean("tile_slots_init", false)) return
        val edit = p.edit().putBoolean("tile_slots_init", true)
        slots.filter { p.contains("tile_$it") }.forEach { edit.putBoolean("tile_added_$it", true) }
        edit.apply()
    }

    /** As [widgetToggles], for a Quick Settings tile. */
    fun tileToggles(context: Context, slot: Int): Boolean =
        tileScript(context, slot) == SurfaceToggle.ALL || prefs(context).getBoolean("tile_toggle_$slot", false)
}
