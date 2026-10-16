package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.example.data.WhatsNewDestination
import com.example.data.whatsNewReleases
import com.example.ui.components.WhatsNewDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-mdpi")
class WhatsNewDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lightScreenshot() = checkDialog(ThemeMode.LIGHT, "light")
    @Test fun darkScreenshot() = checkDialog(ThemeMode.DARK, "dark")
    @Test fun sepiaScreenshot() = checkDialog(ThemeMode.SEPIA, "sepia")
    @Test fun oledScreenshot() = checkDialog(ThemeMode.DARK, "oled", oled = true)

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun smallDisplayScreenshot() = checkDialog(ThemeMode.LIGHT, "small-display")

    @Test
    @Config(qualifiers = "en-w480dp-h900dp-mdpi")
    fun largeDisplayScreenshot() = checkDialog(ThemeMode.DARK, "large-display", fontScale = 1.3f)

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun largeTextOnSmallDisplayKeepsDismissVisible() = checkDialog(ThemeMode.LIGHT, "large-text-small-display", fontScale = 2f)

    private fun checkDialog(theme: ThemeMode, name: String, oled: Boolean = false, fontScale: Float = 1f) {
        var dismissals = 0
        val destinations = mutableListOf<WhatsNewDestination>()
        RuntimeEnvironment.setFontScale(fontScale)
        rule.setContent {
            MyApplicationTheme(themeMode = theme, oled = oled) {
                WhatsNewDialog(
                    release = whatsNewReleases.getValue(12L),
                    themeMode = theme,
                    onDismiss = { dismissals++ },
                    onOpenSettings = { destinations += it },
                )
            }
        }
        rule.onNodeWithTag("whats_new_got_it").assertIsDisplayed()
        val titleLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("What's new", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        assertTrue("The dialog uses the system font scale", titleLayouts.single().getLineBottom(0) >= 24f * fontScale)
        rule.onNodeWithTag("whats_new_dialog").captureRoboImage("build/outputs/whats-new-review/$name.png")
        WhatsNewDestination.entries.forEach { destination ->
            rule.onNodeWithTag("whats_new_${destination.name.lowercase()}").performScrollTo().assertIsDisplayed().performClick()
        }
        assertEquals(WhatsNewDestination.entries, destinations)
        rule.onNodeWithText("Tapping anywhere in a search box opens the keyboard").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("whats_new_dialog").captureRoboImage("build/outputs/whats-new-review/$name-scrolled.png")
        rule.onNodeWithTag("whats_new_got_it").assertIsDisplayed().performClick()
        assertEquals(1, dismissals)
    }
}
