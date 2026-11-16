package com.example.surface

import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import io.github.nicholasarda.keysnap.R

/** A widget's background preset. [background] is what the widget draws; [ground]/[content] are for the preview. */
enum class WidgetColor(@DrawableRes val background: Int, @ColorRes val ground: Int, @ColorRes val content: Int) {
    BLUE(R.drawable.widget_background, R.color.keysnap_blue, R.color.white),

    /** Material You accent on API 31+ (resolved by the launcher, so it follows a wallpaper change); Blue on 30. */
    WALLPAPER(R.drawable.widget_background_wallpaper, R.color.widget_wallpaper, R.color.white),
    DARK(R.drawable.widget_background_dark, R.color.widget_dark, R.color.white),
    LIGHT(R.drawable.widget_background_light, R.color.widget_light, R.color.widget_on_light),
}

/**
 * Which phone's own Quick Settings language the widget is drawn in (see `pro/docs/research/widget-os`).
 * [CLASSIC] is the original rounded square in a [WidgetColor].
 */
enum class WidgetLook {
    CLASSIC, PIXEL, NOTHING, ONE_UI, HYPER_OS;

    companion object {
        /**
         * The look that matches this phone. OPPO, OnePlus and the rest get the Material one until a
         * ColorOS reference exists.
         */
        fun forPhone(manufacturer: String = Build.MANUFACTURER): WidgetLook = when (manufacturer.lowercase()) {
            "samsung" -> ONE_UI
            "xiaomi", "redmi", "poco" -> HYPER_OS
            "nothing" -> NOTHING
            else -> PIXEL
        }
    }
}

/**
 * One widget's look, chosen in [ShortcutPickerActivity]. A widget with none stored (placed before
 * styles existed) keeps the old look: Blue, opaque, the app glyph above the name. [accent] is the
 * "on" colour of a non-classic [look]; null takes that look's own. [form] is the small widget's shape
 * in a non-classic look; the list widget ignores it.
 */
data class WidgetStyle(
    val color: WidgetColor = WidgetColor.BLUE,
    val opacity: Int = 100,
    val showLabel: Boolean = true,
    val customLabel: String = "",
    val showIcon: Boolean = true,
    val look: WidgetLook = WidgetLook.CLASSIC,
    val accent: Int? = null,
    val form: WidgetForm = WidgetForm.TILE,
) {
    /** The custom text last, so a `|` typed into it survives the split. */
    fun encode(): String =
        "v3|${form.name}|${look.name}|${accent?.let { Integer.toHexString(it) } ?: "auto"}|${color.name}|$opacity|$showLabel|$showIcon|$customLabel"

    /** Which of the icon and the label a widget shows; never neither. */
    fun parts(compact: Boolean): Pair<Boolean, Boolean> {
        val label = !showIcon || (showLabel && !compact)
        return showIcon to label
    }

    companion object {
        /** Below this width (dp) the widget is one cell, and an icon leaves no room for a label. */
        const val COMPACT_WIDTH_DP = 100

        fun decode(raw: String?): WidgetStyle? {
            if (raw?.startsWith("v3|") == true) {
                val form = raw.split("|", limit = 3).getOrNull(1)?.let { f -> WidgetForm.entries.firstOrNull { it.name == f } }
                    ?: return null
                return decode("v2|" + raw.split("|", limit = 3).getOrElse(2) { "" })?.copy(form = form)
            }
            // v2 came before forms: those widgets take the first one.
            if (raw?.startsWith("v2|") == true) {
                val p = raw.split("|", limit = 8).takeIf { it.size == 8 } ?: return null
                val accent = if (p[2] == "auto") null else p[2].toLongOrNull(16)?.toInt() ?: return null
                return decodeV1(p.subList(3, 8))?.copy(
                    look = WidgetLook.entries.firstOrNull { it.name == p[1] } ?: return null,
                    accent = accent,
                )
            }
            return decodeV1(raw?.split("|", limit = 5))
        }

        /** The first format, before looks: always [WidgetLook.CLASSIC]. */
        private fun decodeV1(parts: List<String>?): WidgetStyle? {
            if (parts?.size != 5) return null
            return WidgetStyle(
                color = WidgetColor.entries.firstOrNull { it.name == parts[0] } ?: return null,
                opacity = parts[1].toIntOrNull()?.coerceIn(0, 100) ?: return null,
                showLabel = parts[2].toBooleanStrictOrNull() ?: return null,
                showIcon = parts[3].toBooleanStrictOrNull() ?: return null,
                customLabel = parts[4],
            )
        }
    }
}
