package com.example.ui.home

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class UsageAccessAlertTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sheetExplainsTheGrantAndOpensSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var opened = false
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) { UsageAccessSheet(onOpenSettings = { opened = true }, onDismiss = {}) }
        }
        rule.onNodeWithText("Allow usage access").assertIsDisplayed()
        rule.onNodeWithText("Shows which app is in front").assertIsDisplayed()
        rule.onNodeWithText("Open settings").performClick()
        assertEquals(true, opened)
    }
}
