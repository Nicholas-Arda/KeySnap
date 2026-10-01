package com.example.service.adb

import com.example.service.bridge.BridgeController
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The launch-time decision: an app update that replaced a stale bridge restarts it, a reboot does not. */
class StartupBridgeRestartTest {

    private var restarts = 0

    @Before fun reset() {
        BridgeController.staleReplaced.set(false)
        restarts = 0
    }

    private suspend fun resolve(wanted: Boolean, restartSucceeds: Boolean = true) = resolveStartupBridge(
        adoptExisting = { false },
        takeStaleReplacement = BridgeController::takeStaleReplacement,
        advancedWanted = wanted,
        restart = { restarts++; restartSucceeds },
    )

    @Test fun staleReplacedAndWantedRestartsOncePerProcess() = runTest {
        BridgeController.staleReplaced.set(true)
        assertEquals(StartupBridge.RESTARTED, resolve(wanted = true))
        // Activity and Accessibility service both run the launch check; only the first restarts.
        assertEquals(StartupBridge.NOT_RUNNING, resolve(wanted = true))
        assertEquals(1, restarts)
    }

    @Test fun staleReplacedButNotWantedDoesNotRestart() = runTest {
        BridgeController.staleReplaced.set(true)
        assertEquals(StartupBridge.OFF, resolve(wanted = false))
        assertEquals(0, restarts)
        assertFalse("the flag is consumed even when unused", BridgeController.takeStaleReplacement())
    }

    @Test fun rebootWithNothingReplacedKeepsTheOldMessage() = runTest {
        val outcome = resolve(wanted = true)
        assertEquals(StartupBridge.NOT_RUNNING, outcome)
        assertEquals(0, restarts)
        assertEquals(BRIDGE_NOT_RUNNING_MESSAGE, startupStatusMessage(outcome, "bridge"))
    }

    @Test fun failedRestartSaysTheAppWasUpdated() = runTest {
        BridgeController.staleReplaced.set(true)
        val outcome = resolve(wanted = true, restartSucceeds = false)
        assertEquals(StartupBridge.RESTART_FAILED, outcome)
        assertEquals(1, restarts)
        assertEquals(
            "KeySnap was updated, so Advanced Mode needs a restart. Open setup to restart it.",
            startupStatusMessage(outcome, "bridge"),
        )
    }

    /** The restart cap cut the spawn off, but the bridge binds its port 3 s later: adopt it, don't call it off. */
    @Test fun aBridgeThatComesUpAfterTheCapIsAdopted() = runTest {
        var probes = 0
        val start = testScheduler.currentTime
        assertTrue(adoptLateSpawn({ ++probes >= 7 }))
        assertEquals(7, probes)
        assertEquals(3_000L, testScheduler.currentTime - start)
    }

    @Test fun aSpawnThatNeverComesUpGivesUpAfterAboutFiveSeconds() = runTest {
        var probes = 0
        val start = testScheduler.currentTime
        assertFalse(adoptLateSpawn({ probes++; false }))
        assertEquals(10, probes)
        assertEquals(4_500L, testScheduler.currentTime - start)
    }

    @Test fun adoptedBridgeIsNeverRestarted() = runTest {
        val outcome = resolveStartupBridge(
            adoptExisting = { true },
            takeStaleReplacement = { true },
            advancedWanted = true,
            restart = { restarts++; true },
        )
        assertEquals(StartupBridge.ADOPTED, outcome)
        assertEquals(0, restarts)
        assertEquals("bridge", startupStatusMessage(outcome, "bridge"))
    }
}
