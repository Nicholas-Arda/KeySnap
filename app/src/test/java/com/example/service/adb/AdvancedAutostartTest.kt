package com.example.service.adb

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AdvancedAutostartTest {
    @Test fun gatesHaveExplicitPriority() {
        assertEquals(AutostartGate.OFF, autostartGate(false, false, false, false))
        assertEquals(AutostartGate.NOT_WANTED, autostartGate(true, false, false, false))
        assertEquals(AutostartGate.NO_PERMISSION, autostartGate(true, true, false, false))
        assertEquals(AutostartGate.DEV_OPTIONS_OFF, autostartGate(true, true, true, false))
        assertEquals(AutostartGate.GO, autostartGate(true, true, true, true))
    }

    @Test fun permissionCommandsAreFixedAndPackageValidated() {
        assertEquals("pm grant io.github.keysnap.dev android.permission.WRITE_SECURE_SETTINGS", secureSettingsCommand("io.github.keysnap.dev", true))
        assertEquals("pm revoke io.github.keysnap.dev android.permission.WRITE_SECURE_SETTINGS", secureSettingsCommand("io.github.keysnap.dev", false))
        assertThrows(IllegalArgumentException::class.java) { secureSettingsCommand("pkg; id", true) }
        assertEquals(true, grantChange(true, false))
        assertEquals(false, grantChange(false, true))
        assertNull(grantChange(true, true))
        assertNull(grantChange(false, false))
    }

    @Test fun trustedNetworkNeedsOneWriteAndSettlingTime() = runTest {
        var value = 0
        var writes = 0
        assertEquals(WifiDebugging.ON, enableWifiDebugging({ value }, { value = it; writes++ }, { false }))
        assertEquals(1, writes)
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test fun transientEnableDoesNotCountAsNetworkApproval() = runTest {
        var value = 0
        var writes = 0
        launch { delay(500); value = 0; delay(9_500); value = 1 }
        assertEquals(WifiDebugging.ON, enableWifiDebugging({ value }, { if (++writes == 1) value = it }, { false }))
        assertEquals(2, writes)
    }

    @Test fun rejectedNetworkStopsAfterBoundedWait() = runTest {
        assertEquals(WifiDebugging.NOT_ALLOWED, enableWifiDebugging({ 0 }, {}, { false }))
        assertEquals(63_000L, testScheduler.currentTime)
    }

    @Test fun secondWriteWaitsForUnlock() = runTest {
        val writes = mutableListOf<Long>()
        var value = 0
        assertEquals(WifiDebugging.ON, enableWifiDebugging(
            { value },
            { writes += testScheduler.currentTime; if (writes.size == 2) value = it },
            { testScheduler.currentTime < 30_000 },
        ))
        assertEquals(listOf(0L, 30_000L), writes)
    }

    @Test fun onlyLocalAddressesAreAccepted() {
        assertFalse(isOwnAddress(null, setOf("127.0.0.1")))
        assertFalse(isOwnAddress("foreign", setOf("local")))
        assertTrue(isOwnAddress("local", setOf("local")))
    }

    @Test fun recentConnectionAndClockRollbackSkipDailyTouch() {
        assertTrue(keepAliveDue(0, 1))
        assertFalse(keepAliveDue(100, 99))
        assertFalse(keepAliveDue(100, 100 + 20 * 60 * 60_000L - 1))
        assertTrue(keepAliveDue(100, 100 + 20 * 60 * 60_000L))
    }
}
