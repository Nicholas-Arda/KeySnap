package com.example.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.KeyMappingConfig
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
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
    @Test fun appearanceTargetRevealsTheme() = checkTarget(SettingsSection.APPEARANCE, "appearance")
    @Test fun aboutTargetRevealsProblemRecording() = checkTarget(SettingsSection.ABOUT, null)

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
