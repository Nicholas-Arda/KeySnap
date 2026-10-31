package com.example.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.billing.ProPrice
import com.example.billing.ProSale
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class HomeSaleStripTest {
    @get:Rule val rule = createComposeRule()

    private fun render(isPro: Boolean = false, sale: ProSale? = liveSale()) {
        rule.setContent {
            MyApplicationTheme(ThemeMode.LIGHT) {
                HomeScreen(
                    isMappingEnabled = false,
                    isServiceActive = true,
                    isAdvancedModeRunning = false,
                    triggerCount = 0,
                    lastSource = "",
                    isPro = isPro,
                    proPrice = ProPrice("$2.99", sale),
                    activeScriptCount = 0,
                    activeScriptLimit = 3,
                    themeMode = ThemeMode.LIGHT,
                    scripts = emptyList(),
                    onOpenScript = {},
                    onToggleMapping = {},
                    onOpenWizard = {},
                    onOpenAccessibility = {},
                    onPickSuggestion = {},
                    onOpenProScreen = {},
                )
            }
        }
    }

    @Test fun freeUserSeesLiveSale() {
        render()
        rule.onNodeWithTag("home_sale_strip").assertIsDisplayed()
    }

    @Test fun proUserDoesNotSeeSaleStrip() {
        render(isPro = true)
        rule.onNodeWithTag("home_sale_strip").assertDoesNotExist()
    }

    @Test fun noSaleHidesStrip() {
        render(sale = null)
        rule.onNodeWithTag("home_sale_strip").assertDoesNotExist()
    }

    @Test fun expiredSaleHidesStrip() {
        render(sale = liveSale().copy(endsAtMillis = System.currentTimeMillis() - 60_000))
        rule.onNodeWithTag("home_sale_strip").assertDoesNotExist()
    }

    @Test fun saleWithoutEndTimeIsVisible() {
        render(sale = liveSale().copy(endsAtMillis = null))
        rule.onNodeWithTag("home_sale_strip").assertIsDisplayed()
    }

    private fun liveSale() = ProSale("$5.99", 50, System.currentTimeMillis() + 7 * 24 * 60 * 60_000L)
}
