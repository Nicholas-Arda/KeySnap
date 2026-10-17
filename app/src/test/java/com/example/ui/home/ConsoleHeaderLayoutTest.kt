package com.example.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.settings.SettingsGear
import com.example.ui.settings.rememberSettingsMotion
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h900dp-mdpi")
class ConsoleHeaderLayoutTest(
    private val width: Int,
    private val fontScale: Float,
    private val language: String,
    private val active: Boolean,
    private val density: Float,
) {
    companion object {
        @JvmStatic
        @Parameters(name = "{2}, active={3}, width={0}, font={1}, density={4}")
        fun cases(): List<Array<Any>> = buildList {
            // 411 dp at the default density becomes about 328 dp at 1.25x display size.
            listOf("en", "ar", "pt-BR", "de", "es", "fr", "hi", "it", "ru", "tr").forEach { language ->
                listOf(1f, 2f).forEach { fontScale ->
                    listOf(false, true).forEach { active ->
                        add(arrayOf(328, fontScale, language, active, 1.25f))
                    }
                }
            }
            listOf(320, 411, 480).forEach { width ->
                add(arrayOf(width, 1f, "en", true, 1f))
            }
        }
    }

    @get:Rule val rule = createComposeRule()

    @Test fun statusAndSettingsStayOnTheLogoRow() {
        var themeClicks = 0
        var mappingClicks = 0
        var settingsClicks = 0
        val rtl = language == "ar"
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val context = LocalContext.current.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MyApplicationTheme(themeMode = ThemeMode.DARK) {
                    // Match GroundedPage's horizontal/top inset and MainScreen's floating gear.
                    Box(Modifier.width(width.dp).background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ConsoleHeader(active, ThemeMode.DARK, { mappingClicks++ }, { themeClicks++ })
                        }
                        SettingsGear(rememberSettingsMotion(false), false, { settingsClicks++ },
                            Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 14.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            filePath = "build/outputs/header-review/header-${width}-${fontScale}-${language}-active${active}.png",
        )
        fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        fun separated(a: Rect, b: Rect, gap: Float) =
            a.right + gap <= b.left + 1f || b.right + gap <= a.left + 1f ||
                a.bottom + gap <= b.top + 1f || b.bottom + gap <= a.top + 1f
        val logo = bounds("header_logo")
        val controls = bounds("header_controls")
        val mapping = bounds("mapping_badge")
        val label = bounds("mapping_status_label")
        val gear = bounds("settings_gear")
        val labelLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("mapping_status_label", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labelLayouts) }
        assertEquals("status stays readable on one line", 1, labelLayouts.single().lineCount)
        // Android can report the unellipsized glyph span for RTL text. An ellipsized line is
        // constrained by Text; otherwise its whole glyph span must fit.
        val labelLayout = labelLayouts.single()
        assertEquals("requested font scale is applied", fontScale, labelLayout.layoutInput.density.fontScale, 0f)
        assertEquals("requested display density is applied", density, labelLayout.layoutInput.density.density, 0f)
        val glyphWidth = labelLayout.getLineRight(0) - labelLayout.getLineLeft(0)
        assertTrue("status fits or ellipsizes", labelLayout.isLineEllipsized(0) || glyphWidth <= labelLayout.size.width + 1f)
        assertTrue("status text stays inside pill", label.left >= mapping.left && label.right <= mapping.right + 1f)
        assertTrue("logo/control gap", separated(logo, controls, 12f * density))
        assertTrue("logo/gear gap", separated(logo, gear, 12f * density))
        assertTrue("controls/gear gap", separated(controls, gear, 12f * density))
        assertTrue("brand stays legible", logo.width >= 96f * density)
        assertEquals("status shares logo row", logo.center.y, mapping.center.y, 1f)
        assertTrue("status shares Settings row", kotlin.math.abs(mapping.center.y - gear.center.y) <= 12f * density)
        listOf(logo, mapping, gear).forEach {
            assertTrue("48 dp touch height", it.height >= 48f * density - 1f)
            assertTrue("48 dp touch width", it.width >= 48f * density - 1f)
            assertTrue("within screen", it.left >= 0f && it.right <= width * density + 1f)
        }
        rule.onNodeWithTag("header_logo").performClick()
        rule.onNodeWithTag("mapping_badge").performClick()
        rule.onNodeWithTag("settings_gear").performClick()
        assertEquals(1, themeClicks)
        assertEquals(1, mappingClicks)
        assertEquals(1, settingsClicks)
    }
}
