package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.ads.AdConfig
import com.example.data.EntitlementManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the Pro one-time purchase: [AdConfig.PRO_PRODUCT_ID] must exist as an in-app product in
 *  Play Console before this can complete a real purchase (it will fail to find product details
 *  until then, which is expected during development). */
class PurchaseManager(context: Context, private val entitlements: EntitlementManager) {
    private var proProductDetails: ProductDetails? = null

    private val _proPrice = MutableStateFlow<String?>(null)

    /** Play's own localized, currency-formatted price, or null until the product query answers -
     *  which it never does when Billing is unavailable or the product is not published yet. */
    val proPrice: StateFlow<String?> = _proPrice.asStateFlow()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::handlePurchase)
        }
    }

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                queryProductDetails()
                restorePurchases()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(AdConfig.PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        billingClient.queryProductDetailsAsync(params) { _, result ->
            proProductDetails = result.productDetailsList.firstOrNull()
            _proPrice.value = proProductDetails?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /** Play Billing requires every purchase eventually be observed here, not just at the moment of
     *  purchase, so a reinstall or a purchase made on another device is still recognized. */
    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(params) { _, purchases -> purchases.forEach(::handlePurchase) }
    }

    fun purchasePro(activity: Activity) {
        val details = proProductDetails ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(AdConfig.PRO_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        entitlements.setPro(true)
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            billingClient.acknowledgePurchase(ackParams) {}
        }
    }
}
