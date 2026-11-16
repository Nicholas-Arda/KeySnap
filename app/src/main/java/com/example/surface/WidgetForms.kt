package com.example.surface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.example.surface.ShortcutWidgetProvider.Companion.px
import com.example.surface.ShortcutWidgetProvider.Companion.toBitmap
import io.github.nicholasarda.keysnap.R
import kotlin.math.ceil

/** The small widget's shape, picked per widget; the [WidgetLook] colours it. */
enum class WidgetForm(@StringRes val titleRes: Int) {
    /** A square tile like a Quick Settings one: icon on top, name below, filled with the accent when on. */
    TILE(R.string.widget_form_tile),

    /** The state is the headline, over a faint icon, with a bar that fills when on. */
    STATE(R.string.widget_form_state),

    /** The key capsule's family: round at one cell, a pill at two, a ring and a dot that light up when on. */
    CAPSULE(R.string.widget_form_capsule),
}

/** Every colour a [WidgetForm] draws in one [WidgetLook]; the same palette as the list widget's. */
internal data class FormSpec(
    val off: Int,
    val offText: Int,
    val on: Int,
    val onText: Int,
    /** The quieter surface: a chip on a tile, the bar's track. */
    val chip: Int,
    /** The capsule's core when off. */
    val core: Int,
    /** What says "on" in the small marks: a ring, a dot, the state word, a bar. */
    val mark: Int,
    val dim: Int,
    val shell: Int,
    val shellText: Int,
    /** The Nothing look's dotted type. */
    val dotted: Boolean,
)

/** A dark glyph on a light fill, white on a dark one. */
fun glyphOn(fill: Int): Int =
    if (ColorUtils.calculateLuminance(fill) > 0.45) ColorUtils.blendARGB(fill, 0xFF000000.toInt(), 0.78f) else 0xFFFFFFFF.toInt()

private fun c(argb: Long) = argb.toInt()

internal fun formSpec(context: Context, look: WidgetLook, picked: Int?): FormSpec = when (look) {
    WidgetLook.PIXEL, WidgetLook.CLASSIC -> {
        val a = picked ?: context.getColor(R.color.widget_pixel_accent)
        val surface = context.getColor(R.color.widget_pixel_surface)
        val chip = context.getColor(R.color.widget_pixel_chip)
        val text = context.getColor(R.color.widget_pixel_on_surface)
        FormSpec(
            off = chip, offText = text, on = a, onText = glyphOn(a), chip = surface, core = chip,
            mark = a, dim = ColorUtils.blendARGB(text, chip, 0.6f), shell = surface, shellText = text, dotted = false,
        )
    }
    WidgetLook.NOTHING -> {
        val a = picked ?: c(0xFFFFFFFF)
        FormSpec(
            off = c(0xFF141414), offText = c(0xFFFFFFFF), on = a, onText = glyphOn(a), chip = c(0xFF262626), core = c(0xFF262626),
            mark = c(0xFFD71921), dim = c(0xFF5A5A5A), shell = c(0xFF000000), shellText = c(0xFFFFFFFF), dotted = true,
        )
    }
    WidgetLook.ONE_UI -> {
        val a = picked ?: c(0xFF3E91FF)
        FormSpec(
            off = c(0xFFFFFFFF), offText = c(0xFF111111), on = a, onText = glyphOn(a), chip = c(0xFFE6EAF1), core = c(0xFFE6EAF1),
            mark = a, dim = c(0xFFB9BEC8), shell = c(0xFFFFFFFF), shellText = c(0xFF111111), dotted = false,
        )
    }
    WidgetLook.HYPER_OS -> {
        val a = picked ?: c(0xFF3482FF)
        FormSpec(
            off = c(0xFFF4F5F8), offText = c(0xFF111111), on = a, onText = glyphOn(a), chip = c(0xFFDFE3EA), core = c(0xFFDFE3EA),
            mark = a, dim = c(0xFFB9BEC8), shell = c(0xFFFFFFFF), shellText = c(0xFF111111), dotted = false,
        )
    }
}

/**
 * The small widget in [style]'s form and look. [toggles] decides whether it shows a state at all; a
 * run widget is drawn on. [sub] is the key that runs the shortcut, shown at two cells.
 */
internal fun formViews(
    context: Context,
    style: WidgetStyle,
    compact: Boolean,
    on: Boolean,
    toggles: Boolean,
    label: String,
    sub: String?,
    state: String?,
    icon: ImageVector?,
): RemoteViews {
    val s = formSpec(context, style.look, style.accent)
    val name = label.takeIf { style.showLabel || icon == null }
    val body = (style.opacity * 255 / 100)
    return when (style.form) {
        WidgetForm.TILE -> tile(context, s, compact, on, toggles, name, sub, state, icon, body)
        WidgetForm.STATE -> big(context, s, compact, on, toggles, name ?: label, sub, state, icon, body)
        WidgetForm.CAPSULE -> capsule(context, s, compact, on, toggles, name, sub, icon, body)
    }
}

private fun tile(
    context: Context, s: FormSpec, compact: Boolean, on: Boolean, toggles: Boolean,
    name: String?, sub: String?, state: String?, icon: ImageVector?, alpha: Int,
) = RemoteViews(context.packageName, if (compact) R.layout.widget_form_tile_compact else R.layout.widget_form_tile_wide).apply {
    val fill = if (on) s.on else s.off
    val text = if (on) s.onText else s.offText
    tint(R.id.widget_body, fill, alpha)
    // On a light fill the chip goes dark and the glyph takes the fill; on a dark one, a light veil.
    val lightFill = ColorUtils.calculateLuminance(fill) > LIGHT
    val chip = when {
        !on -> s.chip
        lightFill -> s.onText
        else -> ColorUtils.setAlphaComponent(s.onText, VEIL)
    }
    val glyph = when {
        on && s.dotted -> s.mark
        compact || !on -> text
        lightFill -> fill
        else -> s.onText
    }
    icon(context, icon, glyph, if (compact) GLYPH_SMALL else GLYPH)
    text(context, R.id.widget_label, R.id.widget_label_dot, name, text, s.dotted, if (compact) Dots(11f, 64, 2) else Dots(13f, 110))
    if (!compact) {
        tint(R.id.widget_chip, chip)
        setViewVisibility(R.id.widget_chip, if (icon != null) View.VISIBLE else View.GONE)
        text(context, R.id.widget_sub, R.id.widget_sub_dot, sub, text, s.dotted, Dots(10f, 110))
        tint(R.id.widget_pill, chip)
        text(context, R.id.widget_state, R.id.widget_state_dot, state, if (on) glyph else text, s.dotted, Dots(10f, 56))
        setViewVisibility(R.id.widget_state_box, if (toggles && state != null) View.VISIBLE else View.GONE)
    }
}

private fun big(
    context: Context, s: FormSpec, compact: Boolean, on: Boolean, toggles: Boolean,
    name: String, sub: String?, state: String?, icon: ImageVector?, alpha: Int,
) = RemoteViews(context.packageName, if (compact) R.layout.widget_form_state_compact else R.layout.widget_form_state_wide).apply {
    tint(R.id.widget_body, s.off, alpha)
    if (icon != null) {
        val size = context.px(if (compact) WATERMARK_SMALL else WATERMARK)
        setImageViewBitmap(R.id.widget_icon, icon.toBitmap(size, ColorUtils.setAlphaComponent(s.offText, WATERMARK_ALPHA)))
    }
    setViewVisibility(R.id.widget_icon, if (icon != null) View.VISIBLE else View.GONE)
    // A run widget has no state, so its name takes the headline.
    val headline = state.takeIf { toggles } ?: name
    val size = when {
        toggles -> if (compact) HEADLINE_SMALL else HEADLINE
        else -> if (compact) RUN_HEADLINE_SMALL else RUN_HEADLINE
    }.let { if (compact) fitWords(context, headline, it, HEADLINE_SMALL_DP, s.dotted) else it }
    setTextViewTextSize(R.id.widget_headline, TypedValue.COMPLEX_UNIT_SP, size)
    val headlineDots = if (compact) Dots(size, HEADLINE_SMALL_DP, 2) else Dots(size, 120)
    text(context, R.id.widget_headline, R.id.widget_headline_dot, headline, if (toggles && on) s.mark else s.offText, s.dotted, headlineDots)
    text(context, R.id.widget_label, R.id.widget_label_dot, name.takeIf { toggles }, s.offText, s.dotted, if (compact) Dots(9.5f, 60) else Dots(11.5f, 110))
    if (compact) {
        setViewVisibility(R.id.widget_bar, if (toggles) View.VISIBLE else View.GONE)
        tint(R.id.widget_bar_track, s.chip)
        // Two fills rather than one resized: a layout width can only be set from API 31.
        tint(R.id.widget_bar_fill, s.mark)
        tint(R.id.widget_bar_stub, s.dim)
        setViewVisibility(R.id.widget_bar_fill, if (on) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_bar_stub, if (on) View.GONE else View.VISIBLE)
    } else {
        text(context, R.id.widget_sub, R.id.widget_sub_dot, sub, s.offText, s.dotted, Dots(9.5f, 110))
    }
}

private fun capsule(
    context: Context, s: FormSpec, compact: Boolean, on: Boolean, toggles: Boolean,
    name: String?, sub: String?, icon: ImageVector?, alpha: Int,
) = RemoteViews(context.packageName, if (compact) R.layout.widget_form_capsule_compact else R.layout.widget_form_capsule_wide).apply {
    tint(R.id.widget_body, s.shell, alpha)
    val lit = on && toggles
    tint(R.id.widget_chip, if (on) s.on else s.core)
    icon(context, icon, if (on) s.onText else s.shellText, GLYPH)
    setViewVisibility(R.id.widget_chip_box, if (icon != null) View.VISIBLE else View.GONE)
    if (compact) {
        tint(R.id.widget_ring, s.mark)
        setViewVisibility(R.id.widget_ring, if (lit) View.VISIBLE else View.INVISIBLE)
        // With no icon, the name sits in the middle.
        text(context, R.id.widget_label, R.id.widget_label_dot, name.takeIf { icon == null }, s.shellText, s.dotted, Dots(10f, 52, 2, center = true))
    } else {
        text(context, R.id.widget_label, R.id.widget_label_dot, name, s.shellText, s.dotted, Dots(13f, 100))
        text(context, R.id.widget_sub, R.id.widget_sub_dot, sub, s.shellText, s.dotted, Dots(10f, 100))
        tint(R.id.widget_dot, if (lit) s.mark else s.dim)
        tint(R.id.widget_glow, s.mark, if (lit) GLOW else 0)
        setViewVisibility(R.id.widget_dot_box, if (toggles) View.VISIBLE else View.GONE)
    }
}

private fun RemoteViews.tint(id: Int, color: Int, alpha: Int = 255) {
    setInt(id, "setColorFilter", color or 0xFF000000.toInt())
    setInt(id, "setImageAlpha", alpha * (color ushr 24) / 255)
}

private fun RemoteViews.icon(context: Context, icon: ImageVector?, color: Int, dp: Int) {
    if (icon != null) setImageViewBitmap(R.id.widget_icon, icon.toBitmap(context.px(dp), color))
    setViewVisibility(R.id.widget_icon, if (icon != null) View.VISIBLE else View.GONE)
}

/** How the Nothing look's dotted twin of a text is drawn: its size, widest run and lines. */
private data class Dots(val sp: Float, val maxDp: Int, val lines: Int = 1, val center: Boolean = false)

/**
 * Sets [value] on the plain text view, or draws it in Doto on its dotted twin, and hides the other;
 * null hides both. The launcher inflates a widget in a restricted context, where a TextView skips a
 * font resource, so the dotted type reaches the home screen only as a picture.
 */
private fun RemoteViews.text(
    context: Context, plain: Int, dotted: Int, value: CharSequence?, color: Int, useDots: Boolean, dots: Dots,
) {
    val (shown, hidden) = if (useDots) dotted to plain else plain to dotted
    setViewVisibility(hidden, View.GONE)
    setViewVisibility(shown, if (value != null) View.VISIBLE else View.GONE)
    when {
        value == null -> Unit
        useDots -> setImageViewBitmap(dotted, dottedText(context, value, color, dots))
        else -> {
            setTextViewText(plain, value)
            setTextColor(plain, color)
        }
    }
}

/**
 * The largest size from [sp] down to [MIN_HEADLINE] at which every word of [text] fits [maxDp], so a
 * one-cell headline wraps between words instead of breaking one in the middle.
 */
private fun fitWords(context: Context, text: String, sp: Float, maxDp: Int, dotted: Boolean): Float {
    val metrics = context.resources.displayMetrics
    val paint = Paint().apply {
        typeface = if (dotted) ResourcesCompat.getFont(context, R.font.keysnap_doto) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val words = text.split(' ').filter { it.isNotEmpty() }
    var size = sp
    while (size > MIN_HEADLINE) {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, metrics)
        if (words.all { paint.measureText(it) <= context.px(maxDp) }) break
        size -= 0.5f
    }
    return size
}

private fun dottedText(context: Context, value: CharSequence, color: Int, dots: Dots): Bitmap {
    val metrics = context.resources.displayMetrics
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.keysnap_doto)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, dots.sp, metrics)
        this.color = color
    }
    val width = context.px(dots.maxDp)
    val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
        .setAlignment(if (dots.center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
        .setMaxLines(dots.lines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setIncludePad(false)
        .build()
    // Left-aligned text is cropped to its longest line, so it sits where a TextView's would.
    val drawn = if (dots.center) width else (0 until layout.lineCount).maxOf { ceil(layout.getLineWidth(it)).toInt() }
    return Bitmap.createBitmap(drawn.coerceAtLeast(1), layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
        layout.draw(Canvas(it))
    }
}

private const val LIGHT = 0.45
private const val VEIL = 0x40
private const val GLOW = 0x55
private const val WATERMARK_ALPHA = 0x29
private const val GLYPH = 20
private const val GLYPH_SMALL = 22
private const val WATERMARK = 64
private const val WATERMARK_SMALL = 52
private const val HEADLINE = 22f
private const val HEADLINE_SMALL = 18f

/** The one-cell headline's width: the 68dp body less its 10dp and 8dp side padding. */
private const val HEADLINE_SMALL_DP = 50
private const val MIN_HEADLINE = 8f
private const val RUN_HEADLINE = 16f
private const val RUN_HEADLINE_SMALL = 13f
