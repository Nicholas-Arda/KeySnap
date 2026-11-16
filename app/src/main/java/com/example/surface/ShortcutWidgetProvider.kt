package com.example.surface

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.graphics.createBitmap
import com.example.data.AppLocale
import com.example.data.KeyMappingRepository
import com.example.data.ScriptRepository
import com.example.ui.scripts.actionIconFor
import com.example.ui.scripts.triggerSummaryText
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
         * opens the picker) once it is gone. A toggle widget shows the shortcut's on/off state, and
         * an [SurfaceToggle.ALL] one the global switch.
         */
        fun render(context: Context, appWidgetId: Int) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            // Key names (an OEM key the user named) live in the key repository; load it in a cold process.
            KeyMappingRepository.getInstance(context)
            val target = SurfaceBindings.widgetScript(context, appWidgetId)
            val all = target == SurfaceToggle.ALL
            val script = target?.takeUnless { all }?.let { id ->
                ScriptRepository.getInstance(context).scripts.value.firstOrNull { it.id == id }
            }
            val boundTo = target?.takeIf { all || script != null }
            val toggles = boundTo != null && SurfaceBindings.widgetToggles(context, appWidgetId)
            // A run button has no off state, so it wears the on colour; only "Choose a shortcut" stays plain.
            val on = if (toggles) SurfaceToggle.isOn(context, boundTo!!) == true else boundTo != null
            val storedStyle = SurfaceBindings.widgetStyle(context, appWidgetId)
            val style = storedStyle ?: WidgetStyle()
            val res = AppLocale.wrap(context).resources
            val name = if (all) res.getString(R.string.app_name) else script?.name
            val label = name?.let { style.customLabel.ifBlank { it } } ?: res.getString(R.string.surface_choose_shortcut)
            val click = if (boundTo != null) {
                ScriptRunReceiver.pendingIntent(context, appWidgetId, boundTo, toggles)
            } else {
                ShortcutPickerActivity.widgetPendingIntent(context, appWidgetId)
            }
            // A widget whose shortcut is gone shows only "Choose a shortcut".
            val icon = when {
                all -> Icons.Outlined.PowerSettingsNew
                script != null -> actionIconFor(script.actions.firstOrNull()?.type.orEmpty())
                else -> null
            }?.takeIf { style.showIcon }
            val widthDp = manager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val compact = widthDp < WidgetStyle.COMPACT_WIDTH_DP
            val state = stateText(res, toggles, all, on)
            // The key that runs it, under the name at two cells.
            val sub = script?.let { triggerSummaryText(res, it).first }
            val views = if (storedStyle == null) {
                classicViews(context, null, style, compact, toggles && !on, label, icon)
            } else {
                styleViews(context, style, compact, on, toggles, label, sub, state, icon)
            }
            views.setContentDescription(R.id.widget_root, listOfNotNull(label, state).joinToString(", "))
            views.setOnClickPendingIntent(R.id.widget_root, click)
            manager.updateAppWidget(appWidgetId, views)
        }

        /** The widget in a stored [style]: the classic square, or a [WidgetForm] in a phone look. Also the preview's. */
        internal fun styleViews(
            context: Context,
            style: WidgetStyle,
            compact: Boolean,
            on: Boolean,
            toggles: Boolean,
            label: String,
            sub: String?,
            state: String?,
            icon: ImageVector?,
        ): RemoteViews = if (style.look == WidgetLook.CLASSIC) {
            classicViews(context, style, style, compact, toggles && !on, label, icon)
        } else {
            formViews(context, style, compact, on, toggles, label, sub, state, icon)
        }

        /** The original rounded square in a [WidgetColor]; an off toggle fades to [OFF_ALPHA]. */
        private fun classicViews(
            context: Context,
            storedStyle: WidgetStyle?,
            style: WidgetStyle,
            compact: Boolean,
            dimmed: Boolean,
            label: String,
            icon: ImageVector?,
        ) = RemoteViews(context.packageName, R.layout.widget_shortcut).apply {
            val content = context.getColor(style.color.content)
            setImageViewResource(R.id.widget_background, style.color.background)
            val alpha = style.opacity * 255 / 100
            setInt(R.id.widget_background, "setImageAlpha", if (dimmed) (alpha * OFF_ALPHA).toInt() else alpha)
            setTextViewText(R.id.widget_label, label)
            setTextColor(R.id.widget_label, content)
            if (storedStyle == null) {
                setImageViewResource(R.id.widget_icon, R.drawable.ic_launcher_foreground)
                setViewVisibility(R.id.widget_icon, View.VISIBLE)
                setViewVisibility(R.id.widget_label, View.VISIBLE)
            } else {
                val (showIcon, showLabel) = style.copy(showIcon = icon != null).parts(compact)
                if (icon != null) setImageViewBitmap(R.id.widget_icon, icon.toBitmap(context.px(ICON_DP), content))
                setViewVisibility(R.id.widget_icon, if (showIcon) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_label, if (showLabel) View.VISIBLE else View.GONE)
            }
        }

        /** What a toggle widget says it is: KeySnap's own switch reads Live or Paused. */
        internal fun stateText(res: Resources, toggles: Boolean, all: Boolean, on: Boolean): String? = when {
            !toggles -> null
            all -> res.getString(if (on) R.string.home_status_live else R.string.home_status_paused)
            else -> res.getString(if (on) R.string.scripts_toggle_state_on else R.string.scripts_toggle_state_off)
        }

        internal fun Context.px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

        private const val ICON_DP = 36
        private const val OFF_ALPHA = 0.45f

        /** RemoteViews cannot take an [ImageVector], so the editor's action icon is painted into a bitmap. */
        internal fun ImageVector.toBitmap(sizePx: Int, color: Int): Bitmap {
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
            ShortcutListWidget.refreshAll(context)
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, ShortcutWidgetProvider::class.java))
                .forEach { render(context, it) }
        }
    }
}
