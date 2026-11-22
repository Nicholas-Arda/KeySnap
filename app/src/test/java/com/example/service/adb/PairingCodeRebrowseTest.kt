package com.example.service.adb

import android.content.Context
import android.net.nsd.NsdManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairingCodeRebrowseTest {
    /**
     * NSD reports a service once per browse. The browse started with "Pair with code" has usually
     * already resolved the dialog's port, before any code, and dropped it; without a new browse the
     * port never came again and a code typed into the notification did nothing.
     */
    @Test fun aCodeStartsANewPairingBrowse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WirelessAdbManager.initialize(context)
        val nsd = shadowOf(context.getSystemService(NsdManager::class.java))
        WirelessAdbManager.startSearchingMdns()
        // Not single(): the manager is a singleton, and a browse left running by an earlier test in
        // this sandbox makes startSearchingMdns a no-op against a freshly reset shadow.
        val beforeCode = nsd.getDiscoveryListeners(PAIRING).orEmpty()

        WirelessAdbManager.onCredentialsFromNotification("123456", null)

        val afterCode = nsd.getDiscoveryListeners(PAIRING).orEmpty()
        assertEquals("the old browse is stopped, one new browse runs", 1, afterCode.size)
        assertFalse("the browse must be new, not the one that ran before the code", afterCode.single() in beforeCode)
        assertEquals(1, nsd.getDiscoveryListeners("_adb-tls-connect._tcp").orEmpty().size)
    }

    private companion object {
        const val PAIRING = "_adb-tls-pairing._tcp"
    }
}
