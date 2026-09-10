package com.example.ads

import io.github.nicholasarda.keysnap.BuildConfig

/**
 * AdMob and Play Billing identifiers. The ad ids come from the build config, which reads them from
 * local.properties or the environment and falls back to Google's published test ids
 * (developers.google.com/admob/android/test-ads) when neither supplies one — see app/build.gradle.kts.
 * Never hard-code a real ad unit id here; this repository is public and a leaked unit id gets the
 * AdMob account suspended, not just the app. Swap [PRO_PRODUCT_ID] for the Play Console in-app
 * product id once it is created there.
 */
object AdConfig {
    val REWARDED_UNIT_ID: String = BuildConfig.ADMOB_REWARDED_UNIT_ID
    val NATIVE_UNIT_ID: String = BuildConfig.ADMOB_NATIVE_UNIT_ID
    const val PRO_PRODUCT_ID = "pro_unlock"
}
