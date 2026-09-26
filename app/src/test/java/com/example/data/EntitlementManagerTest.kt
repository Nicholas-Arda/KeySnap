package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntitlementManagerTest {
    @Test fun firstWatchGrantsFullWindowAndRewatchOnlyExtends() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("entitlement_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        val manager = EntitlementManager(prefs, ScriptRepository(prefs, Moshi.Builder().build()))

        val before = System.currentTimeMillis()
        manager.grantRewardWindow()
        val first = manager.rewardExpiryAt.value
        assertTrue(first >= before + EntitlementManager.REWARD_WINDOW_MS)

        manager.grantRewardWindow()
        assertEquals(first + EntitlementManager.REWARD_EXTEND_MS, manager.rewardExpiryAt.value)
    }
}
