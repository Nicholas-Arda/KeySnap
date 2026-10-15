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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h900dp-mdpi")
class ConsoleHeaderLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun narrowTurkish() = checkHeader(320, 1f, "tr")
    @Test fun normalTurkish() = checkHeader(360, 1f, "tr")
    @Test fun normalEnglishIdle() = checkHeader(411, 1f, "en", active = true, rewardActive = false)
    @Test fun wideTurkish() = checkHeader(480, 1f, "tr")
    @Test fun narrowDoubleFontLongCountdown() = checkHeader(320, 2f, "tr", remaining = "59m 59s")
    @Test fun largerGerman() = checkHeader(360, 1.3f, "de")
    @Test fun doubleFontArabic() = checkHeader(360, 2f, "ar", remaining = "٢١ د")
    @Test fun narrowDoubleFontPro() = checkHeader(320, 2f, "tr", pro = true)
    @Test fun arabicPro() = checkHeader(411, 1f, "ar", pro = true, active = true)

    private fun checkHeader(
        width: Int, fontScale: Float, language: String, pro: Boolean = false,
        active: Boolean = false, rewardActive: Boolean = true, remaining: String = "21m",
    ) {
        var themeClicks = 0
        var mappingClicks = 0
        var rewardClicks = 0
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
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MyApplicationTheme(themeMode = ThemeMode.DARK) {
                    // Match GroundedPage's horizontal/top inset and MainScreen's floating gear.
                    Box(Modifier.width(width.dp).background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ConsoleHeader(active, pro, rewardActive, remaining, ThemeMode.DARK,
                                { mappingClicks++ }, { rewardClicks++ }, { themeClicks++ })
                        }
                        SettingsGear(rememberSettingsMotion(false), false, { settingsClicks++ },
                            Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 14.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            filePath = "build/outputs/header-review/header-${width}-${fontScale}-${language}-pro${pro}.png",
        )
        fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        fun separated(a: Rect, b: Rect, gap: Float) =
            a.right + gap <= b.left + 1f || b.right + gap <= a.left + 1f ||
                a.bottom + gap <= b.top + 1f || b.bottom + gap <= a.top + 1f
        val logo = bounds("header_logo")
        val controls = bounds("header_controls")
        val mapping = bounds("mapping_badge")
        val gear = bounds("settings_gear")
        val labelLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("mapping_status_label", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(labelLayouts) }
        assertEquals("status stays readable on one line", 1, labelLayouts.single().lineCount)
        // FlowRow's intrinsic pass can retain a wider paragraph container than the final Text.
        // Check the actual line's glyph span, rather than counting that empty space as overflow.
        val labelLayout = labelLayouts.single()
        val glyphWidth = labelLayout.getLineRight(0) - labelLayout.getLineLeft(0)
        assertTrue("status glyphs fit horizontally", glyphWidth <= labelLayout.size.width + 1f)
        assertTrue("status is not ellipsized", !labelLayouts.single().isLineEllipsized(0))
        assertTrue("logo/control gap", separated(logo, controls, 12f))
        assertTrue("logo/gear gap", separated(logo, gear, 12f))
        assertTrue("controls/gear gap", separated(controls, gear, 12f))
        assertTrue("full brand width", logo.width >= 141f)
        listOf(logo, mapping, gear).forEach {
            assertTrue("48 dp touch height", it.height >= 48f)
            assertTrue("within screen", it.left >= 0f && it.right <= width.toFloat())
        }
        if (!pro) {
            val envelope = bounds("reward_ad_envelope")
            val badge = bounds("reward_ad_badge")
            val button = bounds("reward_ad_button")
            assertTrue("reward/mapping gap", separated(envelope, mapping, 12f))
            // The badge hangs over the circle's top edge without taking layout height, so it sits
            // outside the envelope; it must stay on screen and clear of every other control.
            assertTrue("badge on screen", badge.top >= 0f && badge.left >= 0f && badge.right <= width.toFloat())
            assertTrue("badge over the button", badge.bottom > button.top && badge.top < button.top &&
                badge.left < button.right && badge.right > button.left)
            listOf(logo, mapping, gear).forEach { assertTrue("badge clear of controls", separated(badge, it, 0f)) }
            assertTrue("48 dp reward target", button.width >= 48f && button.height >= 48f)
            rule.onNodeWithTag("reward_ad_button").performClick()
            assertEquals(1, rewardClicks)
        }
        rule.onNodeWithTag("header_logo").performClick()
        rule.onNodeWithTag("mapping_badge").performClick()
        rule.onNodeWithTag("settings_gear").performClick()
        assertEquals(1, themeClicks)
        assertEquals(1, mappingClicks)
        assertEquals(1, settingsClicks)
    }
}
