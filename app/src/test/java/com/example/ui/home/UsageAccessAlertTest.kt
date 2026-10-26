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

    @Test fun permissionRowExplainsTheGrantAndOpensUsageAccessSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) { UsageAccessAlert() }
        }
        rule.onNodeWithText("Permissions needed").assertIsDisplayed()
        rule.onNodeWithText(context.getString(io.github.nicholasarda.keysnap.R.string.constraint_needs_usage_access))
            .assertIsDisplayed()
        rule.onNodeWithText("Open settings").performClick()
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, shadowOf(context as android.app.Application).nextStartedActivity.action)
    }
}
