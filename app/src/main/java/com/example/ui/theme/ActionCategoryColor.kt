package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.data.ActionCategory

/**
 * Fixed per-category swatch used for action glyph tiles and suggestion tiles, independent of
 * [ThemeMode], so a category always reads the same way regardless of the active appearance.
 *
 * The twelve values are solved rather than picked: each sits in one narrow luminance band so a
 * white glyph clears 4.5:1 on every swatch (4.62-5.38), while the swatch itself still clears 3:1
 * against all four theme surfaces (3.10 against the darkest, navy). Hue and saturation are then
 * spread to keep the closest pair 23.3 apart in CIELAB, so no two categories read as the same
 * colour. Retune the whole set together - changing one value in isolation breaks the band.
 */
fun colorFor(category: ActionCategory): Color = when (category) {
    ActionCategory.FLASHLIGHT -> Color(0xFF907006)
    ActionCategory.INPUT -> Color(0xFF4158F3)
    ActionCategory.APPS -> Color(0xFF167A45)
    ActionCategory.NAVIGATION -> Color(0xFF9651DB)
    ActionCategory.VOLUME -> Color(0xFF1B73B3)
    ActionCategory.DISPLAY -> Color(0xFFA7540D)
    ActionCategory.MEDIA -> Color(0xFFB3477D)
    ActionCategory.CONNECTIVITY -> Color(0xFF1C71E1)
    ActionCategory.KEYBOARD -> Color(0xFF6E6E83)
    ActionCategory.DEVICE -> Color(0xFF7F6C52)
    ActionCategory.SOUND -> Color(0xFFB52AB0)
    ActionCategory.AUTOMATION -> Color(0xFF3F7F7D)
}

/**
 * Tile fill and glyph for [category], or the theme accent where a tile has no category of its own.
 *
 * A resting tile is a wash of the swatch over the surface behind it, carrying the swatch itself as
 * the glyph; a dark appearance needs more wash to separate from its own ground, and a glyph lifted
 * toward white to clear that wash. Every resulting pair measures 3.7-4.3:1 on light and sepia and
 * 5.6-6.2:1 on dark, clear of the 3:1 floor for a meaningful graphic. Only a [selected] tile takes
 * the solid swatch, which is the one [colorFor] was solved to carry a white glyph on.
 */
@Composable
fun categoryTileColors(category: ActionCategory?, selected: Boolean = false): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val base = category?.let(::colorFor) ?: colors.primary
    if (selected) return base to if (category == null) colors.onPrimary else Color.White
    val dark = colors.surface.luminance() < 0.5f
    return base.copy(alpha = if (dark) 0.18f else 0.14f).compositeOver(colors.surface) to
        if (dark) lerp(base, Color.White, 0.42f) else base
}
