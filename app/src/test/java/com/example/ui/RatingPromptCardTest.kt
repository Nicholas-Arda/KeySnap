package com.example.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.RatingPromptChoice
import com.example.ui.home.RatingPromptCard
import com.example.ui.home.openKeySnapPlayListing
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nicholasarda.keysnap.R
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
@Config(sdk = [34], qualifiers = "en-w320dp-h640dp-mdpi")
class RatingPromptCardTest {
    @get:Rule val rule = createComposeRule()

    @Test fun light() = checkCard(ThemeMode.LIGHT, "light")
    @Test fun darkLargeText() = checkCard(ThemeMode.DARK, "dark-large", fontScale = 2f)

    @Test
    @Config(qualifiers = "ar-w320dp-h640dp-mdpi")
    fun arabicLargeText() = checkCard(ThemeMode.LIGHT, "ar-large", fontScale = 2f)

    private fun checkCard(theme: ThemeMode, name: String, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        val choices = mutableListOf<RatingPromptChoice>()
        rule.setContent {
            MyApplicationTheme(themeMode = theme) {
                Column(Modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                    RatingPromptCard { choices += it }
                }
            }
        }
        rule.onNodeWithTag("rating_prompt").captureRoboImage("build/outputs/rating-prompt/$name.png")
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (label in listOf(R.string.rating_prompt_rate, R.string.rating_prompt_later, R.string.rating_prompt_no_thanks)) {
            rule.onNodeWithText(context.getString(label)).performScrollTo().assertIsDisplayed().performClick()
        }
        assertEquals(RatingPromptChoice.entries, choices)
    }

    @Test fun marketUsesThePlayPackageEvenFromADevContext() {
        val intents = mutableListOf<Intent>()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getPackageName() = "io.github.nicholasarda.keysnap.dev"
            override fun startActivity(intent: Intent) { intents += intent }
        }
        openKeySnapPlayListing(context)
        assertEquals(listOf("market://details?id=io.github.nicholasarda.keysnap"), intents.map { it.dataString })
    }

    @Test fun missingMarketFallsBackToThePublicWebListing() {
        val intents = mutableListOf<Intent>()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) {
                intents += intent
                if (intent.data?.scheme == "market") throw ActivityNotFoundException()
            }
        }
        openKeySnapPlayListing(context)
        assertEquals(
            listOf("market://details?id=io.github.nicholasarda.keysnap", "https://play.google.com/store/apps/details?id=io.github.nicholasarda.keysnap"),
            intents.map { it.dataString },
        )
    }
}
