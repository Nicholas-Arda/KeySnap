package com.example.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** Owns the one rewarded-ad slot behind the home screen's reward button: loads it ahead of time so
 *  the tap that shows it doesn't wait on a network round trip. */
class RewardedAdController(context: Context) {
    private val appContext = context.applicationContext
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun preload() {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        RewardedAd.load(
            appContext,
            AdConfig.REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                }
            },
        )
    }

    val isReady: Boolean get() = rewardedAd != null

    fun show(activity: Activity, onRewardEarned: () -> Unit) {
        val ad = rewardedAd ?: run { preload(); return }
        rewardedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = preload()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = preload()
        }
        ad.show(activity) { onRewardEarned() }
    }
}
