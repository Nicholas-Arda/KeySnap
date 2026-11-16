package com.example.surface

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.TouchApp
import com.example.surface.ShortcutWidgetProvider.Companion.px
import com.example.surface.ShortcutWidgetProvider.Companion.toBitmap
import io.github.nicholasarda.keysnap.R

/**
 * Debug-only look lab: widgets and a tile that do nothing but show the native looks.
 * Each new widget takes the next look; a tap flips on/off through the flipper's animation.
 */
class WidgetLabProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        ids.forEach { render(context, manager, it) }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        render(context, manager, id)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TAP) return super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val on = !prefs(context).getBoolean("on_$id", false)
        prefs(context).edit().putBoolean("on_$id", on).apply()
        // Only the flipper child changes, so the launcher keeps the views and plays the animation.
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(
            id,
            RemoteViews(context.packageName, R.layout.widget_lab).apply {
                setDisplayedChild(R.id.lab_flipper, if (on) 1 else 0)
            },
        )
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val p = prefs(context)
        val look = WidgetLook.entries[p.getInt("look_$id", -1).takeIf { it >= 0 } ?: run {
            val next = p.getInt("next", 0)
            p.edit().putInt("look_$id", LOOKS[next % LOOKS.size].ordinal).putInt("next", next + 1).apply()
            LOOKS[next % LOOKS.size].ordinal
        }]
        val compact = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) <
            WidgetStyle.COMPACT_WIDTH_DP
        val style = WidgetStyle(look = look)
        val name = look.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        fun face(on: Boolean) =
            formViews(
                context, style.copy(form = WidgetForm.entries[id % WidgetForm.entries.size]), compact, on, toggles = true,
                label = name, sub = "Vol down · hold", state = if (on) "On" else "Off", icon = Icons.Outlined.Bolt,
            )
        val tap = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, WidgetLabProvider::class.java).setAction(TAP).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.updateAppWidget(
            id,
            RemoteViews(context.packageName, R.layout.widget_lab).apply {
                removeAllViews(R.id.lab_flipper)
                addView(R.id.lab_flipper, face(false))
                addView(R.id.lab_flipper, face(true))
                setDisplayedChild(R.id.lab_flipper, if (p.getBoolean("on_$id", false)) 1 else 0)
                setOnClickPendingIntent(R.id.lab_root, tap)
            },
        )
    }

    private companion object {
        const val TAP = "com.example.surface.LAB_TAP"
        val LOOKS = WidgetLook.entries - WidgetLook.CLASSIC
        fun prefs(context: Context) = context.getSharedPreferences("widget_lab", Context.MODE_PRIVATE)
    }
}

/** Debug-only tile: each tap flips the state and shows the next candidate icon. */
class WidgetLabTile : TileService() {
    private var step = 0

    override fun onStartListening() = draw()

    override fun onClick() {
        step++
        draw()
    }

    private fun draw() {
        val tile = qsTile ?: return
        val icon = ICONS[step / 2 % ICONS.size]
        tile.icon = Icon.createWithBitmap(icon.second.toBitmap(px(24), Color.WHITE))
        tile.state = if (step % 2 == 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "KeySnap"
        tile.subtitle = "Lab · ${icon.first}"
        tile.updateTile()
    }

    private companion object {
        val ICONS = listOf(
            "power" to Icons.Outlined.PowerSettingsNew,
            "bolt" to Icons.Outlined.Bolt,
            "keyboard" to Icons.Outlined.Keyboard,
            "tap" to Icons.Outlined.TouchApp,
        )
    }
}
