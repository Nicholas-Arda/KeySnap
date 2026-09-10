package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.ActionCategory
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The category swatches and the accent are solved values, not taste: they only work as a set. These
 * tests state the contract they were solved against, so a later edit that breaks the band fails
 * here rather than shipping an illegible glyph.
 */
class ColorTest {

    private companion object {
        /** WCAG 1.4.3 for text; the glyph and accent are both held to it. */
        const val TEXT_CONTRAST = 4.5

        /** WCAG 1.4.11 for a meaningful graphic against its ground. */
        const val GRAPHIC_CONTRAST = 3.0

        /** Below roughly this CIELAB distance two swatches stop reading as different colours. */
        const val MIN_DISTANCE = 20.0
    }

    @Test fun everyCategorySwatchCarriesAWhiteGlyph() {
        ActionCategory.entries.forEach { category ->
            val ratio = contrast(colorFor(category), Color.White)
            assertTrue(
                "$category swatch contrasts ${"%.2f".format(ratio)}:1 with a white glyph",
                ratio >= TEXT_CONTRAST,
            )
        }
    }

    @Test fun everyCategorySwatchSeparatesFromEveryThemeSurface() {
        ActionCategory.entries.forEach { category ->
            ThemeMode.entries.forEach { mode ->
                val ratio = contrast(colorFor(category), surfaceFor(mode))
                assertTrue(
                    "$category swatch contrasts ${"%.2f".format(ratio)}:1 with the $mode surface",
                    ratio >= GRAPHIC_CONTRAST,
                )
            }
        }
    }

    @Test fun noTwoCategoriesReadAsTheSameColour() {
        val categories = ActionCategory.entries
        categories.forEachIndexed { i, a ->
            categories.drop(i + 1).forEach { b ->
                val distance = labDistance(colorFor(a), colorFor(b))
                assertTrue(
                    "$a and $b are only ${"%.1f".format(distance)} apart in CIELAB",
                    distance >= MIN_DISTANCE,
                )
            }
        }
    }

    @Test fun theAccentIsLegibleAsTextInEveryAppearance() {
        ThemeMode.entries.forEach { mode ->
            listOf("surface" to surfaceFor(mode), "background" to backgroundFor(mode))
                .forEach { (name, ground) ->
                    val ratio = contrast(accentFor(mode), ground)
                    assertTrue(
                        "$mode accent contrasts ${"%.2f".format(ratio)}:1 with its $name",
                        ratio >= TEXT_CONTRAST,
                    )
                }
        }
    }

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (x, y) = luminance(a) to luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** CIELAB (D65) so "different colour" means perceptually different, not just numerically. */
    private fun lab(color: Color): Triple<Double, Double, Double> {
        val r = channel(color.red)
        val g = channel(color.green)
        val b = channel(color.blue)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private fun labDistance(a: Color, b: Color): Double {
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }
}
