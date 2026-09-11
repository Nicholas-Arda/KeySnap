package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    private var retryDelayMs = INITIAL_RETRY_MS

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
                    retryDelayMs = INITIAL_RETRY_MS
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                    // A no-fill at start-up used to leave the reward button dead until the next
                    // tap. Retry with the backoff AdMob asks for so the slot fills on its own.
                    handler.postDelayed(::preload, retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
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

private const val INITIAL_RETRY_MS = 30_000L
private const val MAX_RETRY_MS = 5 * 60_000L
