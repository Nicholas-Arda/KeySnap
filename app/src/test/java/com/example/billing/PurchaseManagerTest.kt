package com.example.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseManagerTest {
    @Test fun okAnswerDecidesProEitherWay() {
        assertEquals(true, proAfterQuery(BillingResponseCode.OK, ownsPro = true))
        assertEquals(false, proAfterQuery(BillingResponseCode.OK, ownsPro = false))
    }

    @Test fun anyFailedQueryLeavesProAlone() {
        listOf(
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.NETWORK_ERROR,
            BillingResponseCode.ERROR,
        ).forEach { code -> assertNull("code $code", proAfterQuery(code, ownsPro = false)) }
    }

    @Test fun liveDiscountIsSoldOverTheBaseOffer() {
        val base = OfferInfo(3_990_000, discounted = false, endsAtMillis = null)
        val sale = OfferInfo(2_490_000, discounted = true, endsAtMillis = 2_000)
        assertEquals(1, pickProOffer(listOf(base, sale), nowMillis = 1_000))
    }

    @Test fun endedDiscountFallsBackToFullPrice() {
        val base = OfferInfo(3_990_000, discounted = false, endsAtMillis = null)
        val sale = OfferInfo(2_490_000, discounted = true, endsAtMillis = 2_000)
        assertEquals(1, pickProOffer(listOf(sale, base), nowMillis = 2_000))
        assertNull(pickProOffer(emptyList(), nowMillis = 0))
    }
}
