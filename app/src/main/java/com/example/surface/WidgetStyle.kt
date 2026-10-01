package com.example.surface

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
 * One widget's look, chosen in [ShortcutPickerActivity]. A widget with none stored (placed before
 * styles existed) keeps the old look: Blue, opaque, the app glyph above the name.
 */
data class WidgetStyle(
    val color: WidgetColor = WidgetColor.BLUE,
    val opacity: Int = 100,
    val showLabel: Boolean = true,
    val customLabel: String = "",
    val showIcon: Boolean = true,
) {
    /** The custom text last, so a `|` typed into it survives the split. */
    fun encode(): String = "${color.name}|$opacity|$showLabel|$showIcon|$customLabel"

    /** Which of the icon and the label a widget shows; never neither. */
    fun parts(compact: Boolean): Pair<Boolean, Boolean> {
        val label = !showIcon || (showLabel && !compact)
        return showIcon to label
    }

    companion object {
        /** Below this width (dp) the widget is one cell, and an icon leaves no room for a label. */
        const val COMPACT_WIDTH_DP = 100

        fun decode(raw: String?): WidgetStyle? {
            val parts = raw?.split("|", limit = 5)?.takeIf { it.size == 5 } ?: return null
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
