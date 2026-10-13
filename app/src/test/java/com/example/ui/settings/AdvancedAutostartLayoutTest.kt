package com.example.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.KeyMappingConfig
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
class AdvancedAutostartLayoutTest {
    @get:Rule val rule = createComposeRule()
    @Test fun narrowEnglish() = checkPanel(320, 1f, "en", ThemeMode.LIGHT)
    @Test fun standardEnglish() = checkPanel(411, 1f, "en", ThemeMode.DARK)
    @Test fun largeEnglish() = checkPanel(480, 1.3f, "en", ThemeMode.SEPIA)
    @Test fun largeFontGerman() = checkPanel(360, 2f, "de", ThemeMode.LIGHT)
    @Test fun rightToLeftArabic() = checkPanel(360, 1.3f, "ar", ThemeMode.DARK)

    private fun checkPanel(width: Int, font: Float, language: String, theme: ThemeMode) {
        var clicks = 0
        var title = ""
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language)) }
            val context = LocalContext.current.createConfigurationContext(config)
            title = context.getString(R.string.settings_autostart_title)
            CompositionLocalProvider(
                LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(1f, font),
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MyApplicationTheme(themeMode = theme) {
                    Box(Modifier.width(width.dp).background(MaterialTheme.colorScheme.background)) {
                        SettingsScreen(KeyMappingConfig(), {}, {}, advancedAutostart = true,
                            onToggleAutostart = { clicks++ })
                    }
                }
            }
        }
        rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("advanced_autostart_switch"))
        rule.onNodeWithTag("advanced_autostart_switch").performScrollTo().performClick()
        assertEquals(1, clicks)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(title, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        // Native Robolectric sometimes counts a paragraph's trailing advance as overflow.
        repeat(layout.lineCount) { line ->
            assertTrue("The title glyphs fit", layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
            assertTrue("The title is not ellipsized", !layout.isLineEllipsized(line))
        }
        rule.onRoot().captureRoboImage("build/outputs/autostart-review/settings-$width-$font-$language.png")
    }
}
