package com.example.service.adb

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WirelessAdbHelperServiceTest {
    @Test fun stopBeforeCreateEntersForegroundThenStopsItself() {
        val context = RuntimeEnvironment.getApplication()
        WirelessAdbHelperService.start(context)
        WirelessAdbHelperService.stop(context)
        val service = Robolectric.buildService(WirelessAdbHelperService::class.java).create().get()
        assertTrue("startForeground must still run", shadowOf(service).lastForegroundNotification != null)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
