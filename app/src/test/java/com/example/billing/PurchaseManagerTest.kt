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
}
