package com.example.surface

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.example.data.AppLocale
import com.example.data.KeyMappingRepository
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import com.example.surface.ShortcutWidgetProvider.Companion.px
import com.example.surface.ShortcutWidgetProvider.Companion.toBitmap
import com.example.ui.scripts.actionIconFor
import com.example.ui.scripts.triggerSummaryText
import kotlin.math.ceil
import io.github.nicholasarda.keysnap.R

/**
 * The big widget: KeySnap's Live/Paused button over pages of shortcuts, each with its own switch.
 * Enabled on API 31+ only (`@bool/list_widget_enabled`): it needs RemoteViews switches and stable
 * views. Exported for the system's widget broadcasts; switching goes through [ScriptRunReceiver],
 * which is not.
 */
class ShortcutListWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) appWidgetIds.forEach { ShortcutListWidget.render(context, it) }
    }

    /** A resize can change the layout and how many rows a page holds. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ShortcutListWidget.render(context, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val live = AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, ShortcutListWidgetProvider::class.java)) ?: IntArray(0)
        appWidgetIds.filterNot { it in live }.forEach { SurfaceBindings.unbindWidget(context, it) }
    }
}

object ShortcutListWidget {

    fun refreshAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = AppWidgetManager.getInstance(context) ?: return
        manager.getAppWidgetIds(ComponentName(context, ShortcutListWidgetProvider::class.java))
            .forEach { render(context, it) }
    }

    /**
     * Draws the widget for its current size, which the launcher reports on every resize: about 2x2
     * is only the Live/Paused switch, three cells drop the title and row icons, and the rows are cut
     * into pages of as many as the height fits.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun render(context: Context, appWidgetId: Int) =
        draw(context, appWidgetId, SurfaceBindings.listPage(context, appWidgetId), from = null, back = false)

    /**
     * Turns the page by [step]: the old page slides out as the new one slides in, from the right
     * going forward and from the left going back.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun turnPage(context: Context, appWidgetId: Int, step: Int) {
        val from = SurfaceBindings.listPage(context, appWidgetId)
        SurfaceBindings.setListPage(context, appWidgetId, from + step)
        draw(context, appWidgetId, from + step, from, back = step < 0)
    }

    /** [from] is the page turned away from, null when nothing turns. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun draw(context: Context, appWidgetId: Int, page: Int, from: Int?, back: Boolean) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val style = SurfaceBindings.widgetStyle(context, appWidgetId) ?: WidgetStyle(look = WidgetLook.forPhone())
        val parts = SurfaceBindings.listParts(context, appWidgetId)
        val (width, height) = size(manager, appWidgetId)
        val repo = ScriptRepository.getInstance(context)
        // Key names (an OEM key the user named) live in the key repository; load it in a cold process.
        KeyMappingRepository.getInstance(context)
        val live = repo.globallyEnabled.value
        val rows = repo.scripts.value.map { it to it.enabled }
        val views = if (width < NARROW_DP) {
            tiny(context, appWidgetId, style, live, rows.count { it.second }, rows.size)
        } else {
            val layout = when {
                from == null -> R.layout.widget_list
                // Two turns in a row would share a layout id and the launcher would only reapply it.
                else -> SurfaceBindings.flipListAlt(context, appWidgetId).let { alt ->
                    if (back) (if (alt) R.layout.widget_list_prev_alt else R.layout.widget_list_prev)
                    else (if (alt) R.layout.widget_list_next_alt else R.layout.widget_list_next)
                }
            }
            build(context, appWidgetId, style, parts, live, rows, narrow = width < FULL_DP,
                perPage = rowsPerPage(height, rows.size, parts), page = page, from = from, layout = layout)
        }
        manager.updateAppWidget(appWidgetId, views)
    }

    /** Portrait size in dp: the narrowest width and the tallest height (0 before the first layout). */
    private fun size(manager: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(appWidgetId)
        return (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: FULL_DP) to
            (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP)
    }

    /** Rows that fit [heightDp] between the header and, when they need more than one page, the arrows. */
    internal fun rowsPerPage(heightDp: Int, total: Int, parts: ListParts): Int {
        val room = heightDp - PADDING_DP - (if (parts.header || parts.live) HEADER_DP else 0) + ROW_GAP_DP
        fun fit(footer: Boolean) = ((room - if (footer) FOOTER_DP else 0) / ROW_PITCH_DP).coerceAtLeast(1)
        return if (total <= fit(footer = false)) fit(footer = false) else fit(footer = true)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun tiny(context: Context, appWidgetId: Int, style: WidgetStyle, live: Boolean, on: Int, total: Int) =
        RemoteViews(context.packageName, R.layout.widget_list_tiny).apply {
            val spec = listSpec(context, style.look, style.accent)
            val res = AppLocale.wrap(context).resources
            tint(R.id.list_ground, spec.ground, spec.groundAlpha * style.opacity / 100f)
            tint(R.id.tiny_circle, if (live) spec.live else spec.paused)
            setImageViewBitmap(R.id.tiny_icon, Icons.Outlined.PowerSettingsNew.toBitmap(context.px(22), if (live) spec.onLive else spec.onPaused))
            val state = res.getString(if (live) R.string.home_status_live else R.string.home_status_paused)
            setTextViewText(R.id.tiny_state, state)
            setTextColor(R.id.tiny_state, spec.title)
            // A bare count reads the same in every language.
            setTextViewText(R.id.tiny_count, "$on / $total")
            setTextColor(R.id.tiny_count, spec.title)
            setContentDescription(R.id.tiny_root, "${res.getString(R.string.app_name)}, $state")
            setOnClickPendingIntent(R.id.tiny_root, ScriptRunReceiver.listIntent(context, appWidgetId, SurfaceToggle.ALL))
        }

    /**
     * The style screen's preview: the same views with stand-in states and no actions, so a tap there
     * only changes the picture. [PREVIEW_ROWS] a page keeps it short; the screen wires its arrows and
     * draws a turn from [from] the way the widget does.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun preview(
        context: Context, style: WidgetStyle, parts: ListParts, live: Boolean, rows: List<Pair<ShortcutScript, Boolean>>,
        page: Int, from: Int? = null, back: Boolean = false,
    ) = build(context, null, style, parts, live, rows, narrow = false, perPage = PREVIEW_ROWS, page = page, from = from,
        layout = if (from == null) R.layout.widget_list else if (back) R.layout.widget_list_prev else R.layout.widget_list_next)

    /** [appWidgetId] null is the preview. [layout] is widget_list, or a turn's when [from] is set. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun build(
        context: Context,
        appWidgetId: Int?,
        style: WidgetStyle,
        parts: ListParts,
        live: Boolean,
        scripts: List<Pair<ShortcutScript, Boolean>>,
        narrow: Boolean,
        perPage: Int,
        page: Int,
        from: Int?,
        layout: Int,
    ): RemoteViews {
        val res = AppLocale.wrap(context).resources
        val spec = listSpec(context, style.look, style.accent)
        val pages = scripts.chunked(perPage)
        val shown = if (pages.isEmpty()) 0 else page.mod(pages.size)

        return RemoteViews(context.packageName, layout).apply {
            tint(R.id.list_ground, spec.ground, spec.groundAlpha * style.opacity / 100f)
            setInt(R.id.list_logo, "setColorFilter", spec.logo)
            setTextColor(R.id.list_title, spec.title)
            tint(R.id.list_live_pill, if (live) spec.live else spec.paused)
            val onPill = if (live) spec.onLive else spec.onPaused
            setInt(R.id.list_live_dot, "setColorFilter", onPill)
            setTextColor(R.id.list_live_text, onPill)
            val state = res.getString(if (live) R.string.home_status_live else R.string.home_status_paused)
            setTextViewText(R.id.list_live_text, state)
            setContentDescription(R.id.list_live, "${res.getString(R.string.app_name)}, $state")
            setViewVisibility(R.id.list_live, if (parts.live) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.list_logo, if (parts.header) View.VISIBLE else View.GONE)
            // Invisible, not gone: its weight keeps a lone Live button on the right.
            setViewVisibility(R.id.list_title, if (parts.header && !narrow) View.VISIBLE else View.INVISIBLE)
            setViewVisibility(R.id.list_header, if (parts.header || parts.live) View.VISIBLE else View.GONE)

            setViewVisibility(R.id.list_footer, if (pages.size > 1) View.VISIBLE else View.GONE)
            val arrow = ColorUtils.setAlphaComponent(spec.title, ARROW_ALPHA)
            setImageViewBitmap(R.id.list_prev, Icons.AutoMirrored.Filled.KeyboardArrowLeft.toBitmap(context.px(22), arrow))
            setImageViewBitmap(R.id.list_next, Icons.AutoMirrored.Filled.KeyboardArrowRight.toBitmap(context.px(22), arrow))
            setContentDescription(R.id.list_prev, res.getString(R.string.list_widget_previous_page))
            setContentDescription(R.id.list_next, res.getString(R.string.list_widget_next_page))
            dots(spec, shown, pages.size)
            if (appWidgetId != null) {
                setOnClickPendingIntent(R.id.list_live, ScriptRunReceiver.listIntent(context, appWidgetId, SurfaceToggle.ALL))
                setOnClickPendingIntent(R.id.list_prev, ScriptRunReceiver.pageIntent(context, appWidgetId, -1))
                setOnClickPendingIntent(R.id.list_next, ScriptRunReceiver.pageIntent(context, appWidgetId, +1))
            }

            setTextColor(R.id.list_empty, spec.nameOff)
            setViewVisibility(R.id.list_empty, if (scripts.isEmpty()) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.list_stage, if (scripts.isEmpty()) View.GONE else View.VISIBLE)
            fun fill(id: Int, chunk: List<Pair<ShortcutScript, Boolean>>, actions: Boolean) {
                // Reapplied onto the same layout, the old rows must go first.
                removeAllViews(id)
                chunk.forEach { (script, on) ->
                    val name = script.name.ifBlank { res.getString(R.string.scripts_unnamed) }
                    addView(id, row(context, spec, name, triggerSummaryText(res, script).first.takeIf { parts.keys },
                        actionIconFor(script.actions.firstOrNull()?.type.orEmpty()).takeUnless { narrow }, on, live,
                        appWidgetId?.takeIf { actions }?.let { ScriptRunReceiver.listIntent(context, it, script.id) },
                        res.getString(if (on) R.string.scripts_toggle_state_on else R.string.scripts_toggle_state_off)))
                }
            }
            pages.getOrNull(shown)?.let { fill(R.id.list_in, it, actions = true) }
            if (from != null && pages.isNotEmpty()) fill(R.id.list_out, pages[from.mod(pages.size)], actions = false)
        }
    }

    /** The dots under the page, the shown one long and in the accent; past eight, a count instead. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun RemoteViews.dots(spec: ListSpec, shown: Int, pages: Int) {
        val dots = pages <= DOTS.size
        DOTS.forEachIndexed { i, id ->
            setViewVisibility(id, if (dots && i < pages) View.VISIBLE else View.GONE)
            setViewLayoutWidth(id, if (i == shown) DOT_ON_DP else DOT_DP, TypedValue.COMPLEX_UNIT_DIP)
            tint(id, if (i == shown) spec.live else ColorUtils.setAlphaComponent(spec.title, DOT_OFF_ALPHA))
        }
        setViewVisibility(R.id.list_page, if (dots) View.GONE else View.VISIBLE)
        setTextViewText(R.id.list_page, "${shown + 1}/$pages")
        setTextColor(R.id.list_page, spec.title)
    }

    // The list widget is enabled from API 31 only (list_widget_enabled).
    @SuppressLint("RemoteViewLayout")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun row(
        context: Context,
        spec: ListSpec,
        name: String,
        sub: String?,
        icon: androidx.compose.ui.graphics.vector.ImageVector?,
        on: Boolean,
        live: Boolean,
        toggle: PendingIntent?,
        state: String,
    ) = RemoteViews(context.packageName, R.layout.widget_list_row).apply {
        tint(R.id.row_bg, if (on) spec.rowOn else spec.rowOff)
        tint(R.id.row_chip, if (on) spec.chipOn else spec.chipOff)
        if (icon != null) setImageViewBitmap(R.id.row_icon, icon.toBitmap(context.px(20), if (on) spec.glyphOn else spec.glyphOff))
        setViewVisibility(R.id.row_chip_box, if (icon != null) View.VISIBLE else View.GONE)
        val text = if (on) spec.nameOn else spec.nameOff
        setTextViewText(R.id.row_name, name)
        setTextColor(R.id.row_name, text)
        setTextViewText(R.id.row_sub, sub.orEmpty())
        setTextColor(R.id.row_sub, text)
        setViewVisibility(R.id.row_sub, if (sub != null) View.VISIBLE else View.GONE)
        setImageViewBitmap(R.id.row_switch, switchBitmap(context, spec, on))
        setContentDescription(R.id.row_root, "$name, $state")
        // A paused KeySnap still lets each shortcut be switched, as in the app; the rows only dim.
        setFloat(R.id.row_root, "setAlpha", if (live) 1f else PAUSED_ALPHA)
        if (toggle != null) setOnClickPendingIntent(R.id.row_root, toggle)
    }

    private fun RemoteViews.tint(id: Int, color: Int, alpha: Float = 1f) {
        setInt(id, "setColorFilter", color or 0xFF000000.toInt())
        setInt(id, "setImageAlpha", (alpha * (color ushr 24)).toInt())
    }

    /** A Material switch, 52×32 dp: a small knob at the start when off, a large one at the end when on. */
    private fun switchBitmap(context: Context, spec: ListSpec, on: Boolean): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = SWITCH_W_DP * d
        val h = SWITCH_H_DP * d
        val bitmap = createBitmap(ceil(w).toInt(), ceil(h).toInt())
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = if (on) spec.trackOn else spec.trackOff
        canvas.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, paint)
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        paint.color = if (on) spec.thumbOn else spec.thumbOff
        canvas.drawCircle(if (on != rtl) w - h / 2 else h / 2, h / 2, (if (on) 12 else 8) * d, paint)
        return bitmap
    }

    private const val PAUSED_ALPHA = 0.5f
    internal const val PREVIEW_ROWS = 2

    // Widths in dp: under NARROW about 2x2, under FULL three cells, then four and up.
    private const val NARROW_DP = 230
    private const val FULL_DP = 300
    private const val DEFAULT_HEIGHT_DP = 290

    // Mirrors widget_list.xml, its includes and widget_list_row.xml: padding 10+10, header 40+6, arrows 4+30, row 46+4.
    private const val PADDING_DP = 20
    private const val HEADER_DP = 46
    private const val FOOTER_DP = 34
    private const val ROW_PITCH_DP = 50
    private const val ROW_GAP_DP = 4

    private val DOTS = intArrayOf(
        R.id.list_dot_0, R.id.list_dot_1, R.id.list_dot_2, R.id.list_dot_3,
        R.id.list_dot_4, R.id.list_dot_5, R.id.list_dot_6, R.id.list_dot_7,
    )
    private const val DOT_DP = 6f
    private const val SWITCH_W_DP = 52
    private const val SWITCH_H_DP = 32
    private const val DOT_ON_DP = 16f
    private const val DOT_OFF_ALPHA = 0x55
    private const val ARROW_ALPHA = 0xD0
}

/** Which parts of the list widget show; every one by default, like the capsule's options. */
data class ListParts(val live: Boolean = true, val keys: Boolean = true, val header: Boolean = true)

/** Every colour the list widget draws, for one [WidgetLook]. Values follow the approved mockup. */
internal data class ListSpec(
    val ground: Int, val groundAlpha: Float, val logo: Int, val title: Int,
    val rowOff: Int, val rowOn: Int, val nameOff: Int, val nameOn: Int,
    val chipOff: Int, val chipOn: Int, val glyphOff: Int, val glyphOn: Int,
    val trackOff: Int, val trackOn: Int, val thumbOff: Int, val thumbOn: Int,
    val live: Int, val onLive: Int, val paused: Int, val onPaused: Int,
)

private fun c(argb: Long) = argb.toInt()

internal fun listSpec(context: Context, look: WidgetLook, picked: Int?): ListSpec = when (look) {
    // Material You: the whole row fills with the wallpaper accent when on.
    WidgetLook.PIXEL, WidgetLook.CLASSIC -> {
        val a = picked ?: context.getColor(R.color.widget_pixel_accent)
        val surface = context.getColor(R.color.widget_pixel_surface)
        val chip = context.getColor(R.color.widget_pixel_chip)
        val onS = context.getColor(R.color.widget_pixel_on_surface)
        val onA = glyphOn(a)
        ListSpec(
            ground = surface, groundAlpha = 1f, logo = a, title = onS,
            rowOff = chip, rowOn = a, nameOff = onS, nameOn = onA,
            chipOff = surface, chipOn = ColorUtils.blendARGB(a, c(0xFFFFFFFF), 0.5f), glyphOff = onS, glyphOn = onA,
            trackOff = ColorUtils.setAlphaComponent(onS, 0x33), trackOn = onA, thumbOff = ColorUtils.setAlphaComponent(onS, 0x99), thumbOn = a,
            live = a, onLive = onA, paused = chip, onPaused = onS,
        )
    }
    // Monochrome rows that turn white, with Nothing's red for Live and the switch knob.
    WidgetLook.NOTHING -> {
        val a = picked ?: c(0xFFFFFFFF)
        val red = c(0xFFD71921)
        ListSpec(
            ground = c(0xFF000000), groundAlpha = 1f, logo = c(0xFFFFFFFF), title = c(0xFFFFFFFF),
            rowOff = c(0xFF141414), rowOn = a, nameOff = c(0xFFFFFFFF), nameOn = glyphOn(a),
            chipOff = c(0xFF262626), chipOn = c(0xFF000000), glyphOff = c(0xFFBBBBBB), glyphOn = c(0xFFFFFFFF),
            trackOff = c(0xFF2A2A2A), trackOn = c(0xFF000000), thumbOff = c(0xFF777777), thumbOn = red,
            live = red, onLive = c(0xFFFFFFFF), paused = c(0xFF1F1F1F), onPaused = c(0xFF999999),
        )
    }
    // Frosted white card; only the icon chip and the switch turn blue.
    WidgetLook.ONE_UI -> {
        val a = picked ?: c(0xFF3E91FF)
        ListSpec(
            ground = c(0xFFFFFFFF), groundAlpha = 0.82f, logo = a, title = c(0xFF111111),
            rowOff = c(0xFFFFFFFF), rowOn = c(0xFFFFFFFF), nameOff = c(0xFF111111), nameOn = c(0xFF111111),
            chipOff = c(0xFFEEF1F6), chipOn = a, glyphOff = c(0xFF555555), glyphOn = glyphOn(a),
            trackOff = c(0xFF8A8F98), trackOn = a, thumbOff = c(0xFFFFFFFF), thumbOn = c(0xFFFFFFFF),
            live = a, onLive = glyphOn(a), paused = c(0xFFE6E8EC), onPaused = c(0xFF555555),
        )
    }
    // Clean white card with soft grey rows, HyperOS blue for on.
    WidgetLook.HYPER_OS -> {
        val a = picked ?: c(0xFF3482FF)
        ListSpec(
            ground = c(0xFFFFFFFF), groundAlpha = 1f, logo = a, title = c(0xFF1A1A1A),
            rowOff = c(0xFFF4F5F7), rowOn = c(0xFFF4F5F7), nameOff = c(0xFF1A1A1A), nameOn = c(0xFF1A1A1A),
            chipOff = c(0xFFE6E8EC), chipOn = a, glyphOff = c(0xFF666666), glyphOn = glyphOn(a),
            trackOff = c(0xFF8A8F98), trackOn = a, thumbOff = c(0xFFFFFFFF), thumbOn = c(0xFFFFFFFF),
            live = a, onLive = glyphOn(a), paused = c(0xFFF2F3F5), onPaused = c(0xFF777777),
        )
    }
}
