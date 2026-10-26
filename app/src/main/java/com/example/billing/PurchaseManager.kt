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
import com.example.data.EntitlementManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.util.Currency

/** Owns the Pro one-time purchase: [PRO_PRODUCT_ID] must exist as an in-app product in
 *  Play Console before this can complete a real purchase (it will fail to find product details
 *  until then, which is expected during development). App-scoped: one BillingClient for the
 *  process, because MainViewModel dies with every Activity and a client per view model was never
 *  ended — its auto-reconnecting service connection and threads piled up on every relaunch. */
class PurchaseManager private constructor(context: Context, private val entitlements: EntitlementManager) {
    companion object {
        private const val PRO_PRODUCT_ID = "pro_unlock"

        @Volatile private var instance: PurchaseManager? = null

        fun getInstance(context: Context): PurchaseManager = instance ?: synchronized(this) {
            instance ?: PurchaseManager(context, EntitlementManager.getInstance(context)).also { instance = it }
        }
    }

    private var started = false
    private var proProductDetails: ProductDetails? = null
    private var proOfferToken: String? = null

    private val _proPrice = MutableStateFlow<ProPrice?>(null)

    /** Play's own localized, currency-formatted price, or null until the product query answers -
     *  which it never does when Billing is unavailable or the product is not published yet. While a
     *  discount offer set up in Play Console is live, it also carries the full price and end time. */
    val proPrice: StateFlow<ProPrice?> = _proPrice.asStateFlow()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::handlePurchase)
        }
    }

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Connects once per process; later Activities reuse the connection and rely on [refresh]. */
    fun start() {
        if (started) return
        started = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                queryProductDetails()
                restorePurchases()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Re-reads Play on every return to the app, so a promo code redeemed in the Play Store or a
     *  refund takes effect without a restart, a price that failed to load gets another try, and a
     *  discount that started or ended in Play Console shows up or disappears. */
    fun refresh() {
        queryProductDetails()
        restorePurchases()
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        billingClient.queryProductDetailsAsync(params) { _, result ->
            val details = result.productDetailsList.firstOrNull()
            proProductDetails = details
            val offers = details?.oneTimePurchaseOfferDetailsList.orEmpty()
            val chosen = pickProOffer(offers.map { it.toOfferInfo() }, System.currentTimeMillis())
                ?.let { index -> offers[index] } ?: details?.oneTimePurchaseOfferDetails
            proOfferToken = chosen?.offerToken
            _proPrice.value = chosen?.let { offer ->
                ProPrice(offer.formattedPrice, offer.sale(offers))
            }
        }
    }

    /** Play Billing requires every purchase eventually be observed here, not just at the moment of
     *  purchase, so a reinstall, a purchase on another device, a promo code or a refund is seen. */
    private fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            purchases.forEach(::handlePurchase)
            val ownsPro = purchases.any {
                PRO_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            proAfterQuery(result.responseCode, ownsPro)?.let(entitlements::setPro)
        }
    }

    fun purchasePro(activity: Activity) {
        val details = proProductDetails ?: return
        // The offer token is what applies a Play Console discount; without it Play charges full price.
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { proOfferToken?.let(::setOfferToken) }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRO_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        entitlements.setPro(true)
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            billingClient.acknowledgePurchase(ackParams) {}
        }
    }
}

/** Pro's price as Play reports it. [sale] is set only while a discount offer is live. */
data class ProPrice(val price: String, val sale: ProSale? = null)

/** A Play Console discount: the struck-out full price, its percentage and when Play ends it. */
data class ProSale(val fullPrice: String, val percentOff: Int?, val endsAtMillis: Long?)

internal data class OfferInfo(val priceMicros: Long, val discounted: Boolean, val endsAtMillis: Long?)

/** The offer to sell: the cheapest live discount, else the first (base) offer; null with no offers. */
internal fun pickProOffer(offers: List<OfferInfo>, nowMillis: Long): Int? {
    if (offers.isEmpty()) return null
    val live = offers.indices.filter { i -> offers[i].discounted && offers[i].endsAtMillis.let { it == null || it > nowMillis } }
    return live.minByOrNull { offers[it].priceMicros } ?: offers.indices.firstOrNull { !offers[it].discounted } ?: 0
}

private fun ProductDetails.OneTimePurchaseOfferDetails.toOfferInfo() =
    OfferInfo(priceAmountMicros, discountDisplayInfo != null, validTimeWindow?.endTimeMillis)

private fun ProductDetails.OneTimePurchaseOfferDetails.sale(all: List<ProductDetails.OneTimePurchaseOfferDetails>): ProSale? {
    val discount = discountDisplayInfo ?: return null
    val fullMicros = fullPriceMicros ?: return null
    // Play's own string for the undiscounted offer when it is listed; otherwise format the micros.
    val full = all.firstOrNull { it.discountDisplayInfo == null && it.priceAmountMicros == fullMicros }?.formattedPrice
        ?: NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(priceCurrencyCode) }
            .format(fullMicros / 1_000_000.0)
    return ProSale(full, discount.percentageDiscount, validTimeWindow?.endTimeMillis)
}

/** What Pro should become after a purchases query; null keeps the stored value. Only an OK answer
 *  proves ownership or its absence — offline, a signed-out Play Store or a service error must never
 *  cost a paying user Pro. */
internal fun proAfterQuery(responseCode: Int, ownsPro: Boolean): Boolean? =
    if (responseCode == BillingClient.BillingResponseCode.OK) ownsPro else null
