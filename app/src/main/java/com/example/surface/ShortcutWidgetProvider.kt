package com.example.surface

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.graphics.createBitmap
import com.example.data.AppLocale
import com.example.data.ScriptRepository
import com.example.ui.scripts.actionIconFor
import io.github.nicholasarda.keysnap.R

/**
 * Draws the one-tap shortcut widget. Exported because the system delivers widget broadcasts to it;
 * it only draws, and the run itself goes through [ScriptRunReceiver].
 */
class ShortcutWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, it) }
    }

    /** A resize can cross the one-cell width, where an icon-and-name widget becomes icon only. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, appWidgetId)
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
        /**
         * The bound shortcut in the widget's [WidgetStyle], or "Choose a shortcut" (and a tap that
         * opens the picker) once it is gone.
         */
        fun render(context: Context, appWidgetId: Int) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val script = SurfaceBindings.widgetScript(context, appWidgetId)?.let { id ->
                ScriptRepository.getInstance(context).scripts.value.firstOrNull { it.id == id }
            }
            val storedStyle = SurfaceBindings.widgetStyle(context, appWidgetId)
            val style = storedStyle ?: WidgetStyle()
            val label = script?.let { style.customLabel.ifBlank { it.name } }
                ?: AppLocale.wrap(context).getString(R.string.surface_choose_shortcut)
            val click = if (script != null) {
                ScriptRunReceiver.pendingIntent(context, appWidgetId, script.id)
            } else {
                ShortcutPickerActivity.widgetPendingIntent(context, appWidgetId)
            }
            val content = context.getColor(style.color.content)
            val views = RemoteViews(context.packageName, R.layout.widget_shortcut).apply {
                setImageViewResource(R.id.widget_background, style.color.background)
                setInt(R.id.widget_background, "setImageAlpha", style.opacity * 255 / 100)
                setTextViewText(R.id.widget_label, label)
                setTextColor(R.id.widget_label, content)
                setContentDescription(R.id.widget_root, label)
                setOnClickPendingIntent(R.id.widget_root, click)
                if (storedStyle == null) {
                    setImageViewResource(R.id.widget_icon, R.drawable.ic_launcher_foreground)
                    setViewVisibility(R.id.widget_icon, View.VISIBLE)
                    setViewVisibility(R.id.widget_label, View.VISIBLE)
                } else {
                    val widthDp = manager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    // A widget whose shortcut is gone shows only "Choose a shortcut".
                    val iconScript = script?.takeIf { style.showIcon }
                    val (showIcon, showLabel) = style.copy(showIcon = iconScript != null)
                        .parts(compact = widthDp < WidgetStyle.COMPACT_WIDTH_DP)
                    if (iconScript != null) {
                        val icon = actionIconFor(iconScript.actions.firstOrNull()?.type.orEmpty())
                        val px = (ICON_DP * context.resources.displayMetrics.density).toInt()
                        setImageViewBitmap(R.id.widget_icon, icon.toBitmap(px, content))
                    }
                    setViewVisibility(R.id.widget_icon, if (showIcon) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.widget_label, if (showLabel) View.VISIBLE else View.GONE)
                }
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private const val ICON_DP = 36

        /** RemoteViews cannot take an [ImageVector], so the editor's action icon is painted into a bitmap. */
        private fun ImageVector.toBitmap(sizePx: Int, color: Int): Bitmap {
            val bitmap = createBitmap(sizePx, sizePx)
            val canvas = Canvas(bitmap)
            canvas.scale(sizePx / viewportWidth, sizePx / viewportHeight)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            fun draw(group: VectorGroup) {
                group.forEach { node ->
                    when (node) {
                        is VectorGroup -> draw(node)
                        is VectorPath -> canvas.drawPath(
                            node.pathData.toPath().apply { fillType = node.pathFillType }.asAndroidPath(),
                            paint,
                        )
                    }
                }
            }
            draw(root)
            return bitmap
        }

        /** Called on every script-list change, so a rename or a delete shows on the home screen. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, ShortcutWidgetProvider::class.java))
                .forEach { render(context, it) }
        }
    }
}
