package com.example.service.adb

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairWithCodeTapTest {
    @After fun tearDown() = WirelessAdbManager.disarmPairWithCodeTap()

    @Test fun oneTapPerUserStart() {
        WirelessAdbManager.startSearchingMdns()
        assertFalse("never taps before the user opens Settings", WirelessAdbManager.takePairWithCodeTap())
        WirelessAdbManager.armPairWithCodeTap()
        assertTrue(WirelessAdbManager.takePairWithCodeTap())
        assertFalse("a later screen change must not tap again", WirelessAdbManager.takePairWithCodeTap())
    }

    @Test fun closingTheWizardDisarms() {
        WirelessAdbManager.startSearchingMdns()
        WirelessAdbManager.armPairWithCodeTap()
        WirelessAdbManager.disarmPairWithCodeTap()
        assertFalse(WirelessAdbManager.takePairWithCodeTap())
    }

    @Test fun wirelessRowIsOpenedOncePerStartAndIndependentlyOfThePairTap() {
        WirelessAdbManager.startSearchingMdns()
        assertFalse(WirelessAdbManager.takeWirelessRowTap())
        WirelessAdbManager.armPairWithCodeTap()
        assertTrue(WirelessAdbManager.takeWirelessRowTap())
        assertFalse("Back to Developer options must not open it again", WirelessAdbManager.takeWirelessRowTap())
        assertTrue("the Pair with code tap is still available", WirelessAdbManager.takePairWithCodeTap())
    }
}
