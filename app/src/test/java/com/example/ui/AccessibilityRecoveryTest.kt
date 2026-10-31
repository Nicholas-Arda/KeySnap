package com.example.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.AccessibilityStatus
import com.example.data.KeyMappingConfig
import com.example.ui.home.PermissionGaps
import com.example.ui.home.SetupCard
import com.example.ui.components.GroundedPage
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsSection
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
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
class AccessibilityRecoveryTest {
    @get:Rule val rule = createComposeRule()

    @Test fun reconnectCardExplainsTheEnabledButDisconnectedCase() = checkCard(ThemeMode.LIGHT, "light")
    @Test fun darkReconnectCard() = checkCard(ThemeMode.DARK, "dark")

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun largeTextCanStillReachTheRecoveryButton() = checkCard(ThemeMode.LIGHT, "large-text", 2f)

    private fun checkCard(theme: ThemeMode, name: String, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        var opens = 0
        rule.setContent {
            MyApplicationTheme(themeMode = theme) {
                GroundedPage(theme, rememberScrollState()) {
                    SetupCard(
                        gaps = PermissionGaps(notifications = true, battery = true),
                        isServiceActive = false,
                        onOpenAccessibility = { opens++ },
                    )
                }
            }
        }
        rule.onNodeWithText("Shortcuts aren’t running yet").assertIsDisplayed()
        rule.onNodeWithTag("permission_alerts").captureRoboImage("build/outputs/accessibility-recovery/$name.png")
        rule.onNodeWithText("Fix").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, opens)
    }

    @Test fun settingsReportsReconnectNeededAndOpensRecoveryWithoutChangingPermissions() {
        var opens = 0
        var permissionChanges = 0
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                SettingsScreen(
                    config = KeyMappingConfig(),
                    onToggleConsume = {},
                    onToggleVibrate = {},
                    themeMode = ThemeMode.DARK,
                    accessibilityStatus = AccessibilityStatus.NEEDS_RECONNECT,
                    onOpenAccessibility = { opens++ },
                    targetSection = SettingsSection.ADVANCED_MODE,
                    onToggleAdvancedMode = { permissionChanges++ },
                    onToggleAutostart = { permissionChanges++ },
                )
            }
        }
        rule.onNodeWithText("Reconnect needed").assertIsDisplayed()
        rule.onNodeWithTag("accessibility_reconnect").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, opens)
        assertEquals(0, permissionChanges)
    }
}
