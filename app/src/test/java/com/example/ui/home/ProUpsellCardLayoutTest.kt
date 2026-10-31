package com.example.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.billing.ProSale
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-mdpi")
class ProUpsellCardLayoutTest(private val language: String, private val fontScale: Float, private val price: String) {
    companion object {
        @JvmStatic
        @Parameters(name = "{0}, font={1}, price={2}")
        fun cases(): List<Array<Any>> = buildList {
            listOf("en", "ar", "pt-BR", "de", "es", "fr", "hi", "it", "nl", "ru", "tr").forEach { language ->
                listOf(1f, 2f).forEach { scale -> add(arrayOf(language, scale, "$2.99")) }
            }
            listOf("Rp 39.900,00", "COP 10.500,00", "UZS 31 900,00", "222.000,00 LBP").forEach { price ->
                add(arrayOf("en", 2f, price))
            }
        }
    }

    @get:Rule val rule = createComposeRule()
    private val originalLocale = Locale.getDefault()

    @Before fun useLocale() {
        val qualifier = if (language == "pt-BR") "pt-rBR" else language
        RuntimeEnvironment.setQualifiers("$qualifier-w360dp-h900dp-mdpi")
        Locale.setDefault(Locale.forLanguageTag(language))
        RuntimeEnvironment.setFontScale(fontScale)
    }

    @After fun restoreLocale() { Locale.setDefault(originalLocale) }

    @Test fun saleCardFitsAllLanguagesAndLargeFontWithoutClipping() {
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.forLanguageTag(language))
                fontScale = this@ProUpsellCardLayoutTest.fontScale
            }
            val context = LocalContext.current.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MyApplicationTheme(if (fontScale == 1f) ThemeMode.LIGHT else ThemeMode.DARK) {
                    Box(
                        Modifier.width(if (fontScale == 1f) 360.dp else 290.dp).height(900.dp)
                            .background(MaterialTheme.colorScheme.background).testTag("pro_card_review"),
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                            ProUpsellCard(1, 3, price, {}, ProSale("$5.99", 50, System.currentTimeMillis() + 6 * 86_400_000L))
                        }
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        }
        assertTrue("Text was measured", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertTrue("Text height fits: ${layout.layoutInput.text}", layout.multiParagraph.height <= layout.size.height + 1f)
            for (line in 0 until layout.lineCount) {
                assertFalse("Text must not ellipsize: ${layout.layoutInput.text}", layout.isLineEllipsized(line))
                assertTrue(
                    "Glyphs fit: ${layout.layoutInput.text}",
                    layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f,
                )
            }
        }
        if (price == "$2.99") {
            rule.onNodeWithTag("pro_card_review").captureRoboImage("build/outputs/pro-card-review/$language-$fontScale-top.png")
        }
        rule.onNodeWithTag("pro_upsell_purchase_button").performScrollTo().assertHeightIsAtLeast(56.dp)
        if (price == "$2.99" && fontScale == 2f) {
            rule.onNodeWithTag("pro_card_review").captureRoboImage("build/outputs/pro-card-review/$language-$fontScale-bottom.png")
        }
    }
}
