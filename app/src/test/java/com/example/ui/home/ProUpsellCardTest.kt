package com.example.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.billing.ProSale
import com.example.capsule.CapsuleHooks
import com.example.data.EntitlementManager
import com.example.mode.ModeHooks
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import io.github.nicholasarda.keysnap.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h800dp")
class ProUpsellCardTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun render(sale: ProSale? = null, price: String? = "$2.99", activeCount: Int = 1, onOpen: () -> Unit = {}) {
        rule.setContent {
            MyApplicationTheme(ThemeMode.LIGHT) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProUpsellCard(activeCount, 3, price, onOpen, sale)
                }
            }
        }
    }

    private fun row(label: Int, free: String, pro: String) = rule.onNodeWithContentDescription(
        context.getString(R.string.pro_table_row_description, context.getString(label), free, pro),
    )

    @Test fun comparisonIncludesFutureAndSharedFeaturesWithoutPromotingWidgets() {
        render()
        rule.onNodeWithTag("pro_upsell_card").assertExists()
        row(R.string.pro_active_shortcuts, "${EntitlementManager.FREE_SCRIPT_LIMIT}", context.getString(R.string.pro_table_unlimited)).assertExists()
        row(R.string.pro_table_future, context.getString(R.string.pro_table_not_included), context.getString(R.string.pro_table_included)).assertExists()
        row(R.string.pro_table_everything_else, context.getString(R.string.pro_table_included), context.getString(R.string.pro_table_included)).assertExists()
        rule.onNode(hasText("widget", substring = true, ignoreCase = true)).assertDoesNotExist()
        rule.onNode(hasContentDescription("widget", substring = true, ignoreCase = true)).assertDoesNotExist()
    }

    @Test fun capsuleAndModesMatchEditionAvailability() {
        render()
        val notIncluded = context.getString(R.string.pro_table_not_included)
        val included = context.getString(R.string.pro_table_included)
        val capsule = row(R.string.capsule_intro_title, notIncluded, included)
        val modes = row(R.string.pro_table_modes, notIncluded, included)
        if (CapsuleHooks.AVAILABLE) capsule.assertExists() else capsule.assertDoesNotExist()
        if (ModeHooks.AVAILABLE) modes.assertExists() else modes.assertDoesNotExist()
    }

    @Test fun liveSaleShowsBadgePriceAndCountdown() {
        render(sale = liveSale())
        rule.onNodeWithTag("pro_sale_badge").assertTextContains("−50%".unbroken())
        rule.onNodeWithTag("pro_sale_line").assertExists()
        rule.onNodeWithText("Sale ends in", substring = true).assertExists()
        rule.onNodeWithText("$5.99".unbroken()).assertExists()
        rule.onNodeWithText("$2.99".unbroken()).assertExists()
    }

    @Test fun noSaleHidesBadgeAndSaleLineButKeepsPrice() {
        render()
        rule.onNodeWithTag("pro_sale_badge").assertDoesNotExist()
        rule.onNodeWithTag("pro_sale_line").assertDoesNotExist()
        rule.onNodeWithText("$2.99".unbroken()).assertExists()
    }

    @Test fun expiredSaleHidesBadgeAndSaleLine() {
        render(sale = liveSale().copy(endsAtMillis = System.currentTimeMillis() - 60_000))
        rule.onNodeWithTag("pro_sale_badge").assertDoesNotExist()
        rule.onNodeWithTag("pro_sale_line").assertDoesNotExist()
        rule.onNodeWithText("$5.99".unbroken()).assertDoesNotExist()
    }

    @Test fun saleWithoutEndTimeKeepsPricesAndBadgeWithoutCountdown() {
        render(sale = liveSale().copy(endsAtMillis = null))
        rule.onNodeWithTag("pro_sale_badge").assertExists()
        rule.onNodeWithTag("pro_sale_line").assertExists()
        rule.onNodeWithText("Sale ends in", substring = true).assertDoesNotExist()
    }

    @Test fun saleWithoutPercentageHasNoBadge() {
        render(sale = liveSale().copy(percentOff = null))
        rule.onNodeWithTag("pro_sale_badge").assertDoesNotExist()
        rule.onNodeWithTag("pro_sale_line").assertExists()
    }

    @Test fun missingPriceDoesNotInventAPriceRow() {
        render(sale = liveSale(), price = null)
        rule.onNodeWithTag("pro_sale_badge").assertExists()
        rule.onNodeWithTag("pro_sale_line").assertDoesNotExist()
        rule.onNodeWithTag("pro_upsell_purchase_button").assertExists()
    }

    @Test fun purchaseButtonKeepsTagAndOpensProWithoutPriceInLabel() {
        var clicks = 0
        render(onOpen = { clicks++ })
        rule.onNodeWithTag("pro_upsell_purchase_button")
            .performScrollTo()
            .assertHasClickAction()
            .assertTextContains(context.getString(R.string.pro_unlock))
            .assertTextContains(context.getString(R.string.pro_card_one_time))
            .performClick()
        assertEquals(1, clicks)
    }

    @Test fun atCapKeepsExplanationAndMergedUsageDescription() {
        render(activeCount = 3)
        rule.onNodeWithText(context.getString(R.string.pro_card_at_cap)).assertExists()
        rule.onNodeWithText(context.getString(R.string.pro_card_pay_once)).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.pro_progress_description, 3, 3)).assertExists()
    }

    @Test fun belowCapExplainsPermanentPurchase() {
        render()
        rule.onNodeWithText(context.getString(R.string.pro_card_pay_once)).assertExists()
        rule.onNodeWithText(context.getString(R.string.pro_card_at_cap)).assertDoesNotExist()
    }

    private fun liveSale() = ProSale("$5.99", 50, System.currentTimeMillis() + 7 * 86_400_000L)
}
