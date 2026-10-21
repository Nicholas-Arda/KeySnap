package com.example.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import com.example.capsule.CapsuleHooks
import com.example.data.KeyMappingConfig
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-mdpi")
class WhatsNewSettingsNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun advancedModeTargetRevealsAutostartWithoutChangingIt() = checkTarget(SettingsSection.ADVANCED_MODE, "advanced_autostart_switch")
    @Test fun appearanceTargetRevealsTheme() = checkTarget(SettingsSection.APPEARANCE, "appearance_toggle_button")
    @Test fun capsuleTargetRevealsEntry() {
        assumeTrue("Key capsule is a Pro-source feature", CapsuleHooks.AVAILABLE)
        checkTarget(SettingsSection.KEY_CAPSULE, "open_key_capsule")
    }
    @Test fun aboutTargetRevealsProblemRecording() = checkTarget(SettingsSection.ABOUT, null)

    @Test fun darkSettingsLabelsRemainReadable() {
        var background = Color.Unspecified
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                background = MaterialTheme.colorScheme.surface
                SettingsScreen(KeyMappingConfig(), {}, {}, themeMode = ThemeMode.DARK)
            }
        }
        for (tag in listOfNotNull("open_appearance_label", "language_picker_button_label", "open_key_capsule_label".takeIf { CapsuleHooks.AVAILABLE })) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag(tag, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val foreground = layouts.single().layoutInput.style.color
            val light = maxOf(foreground.luminance(), background.luminance())
            val dark = minOf(foreground.luminance(), background.luminance())
            assertTrue("$tag must have readable dark-theme contrast", (light + .05f) / (dark + .05f) >= 4.5f)
        }
    }

    private fun checkTarget(section: SettingsSection, tag: String?) {
        var changes = 0
        var shown = 0
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    config = KeyMappingConfig(),
                    onToggleConsume = {},
                    onToggleVibrate = {},
                    themeMode = ThemeMode.LIGHT,
                    onSetThemeMode = { changes++ },
                    onToggleAutostart = { changes++ },
                    targetSection = section,
                    onSectionShown = { shown++ },
                )
            }
        }
        if (tag != null) rule.onNodeWithTag(tag).assertIsDisplayed()
        else rule.onNodeWithText("Problem recording").assertIsDisplayed()
        assertEquals(0, changes)
        assertEquals(1, shown)
    }
}
