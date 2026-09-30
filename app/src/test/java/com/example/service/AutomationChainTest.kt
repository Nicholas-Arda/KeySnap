package com.example.service

import com.example.data.ScriptAction
import com.example.service.bridge.BridgeRunResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An automation run must split its chain between the bridge and the app exactly as a key run does. */
class AutomationChainTest {

    // toggle_bluetooth only the bridge can do; take_screenshot is in UNSUPPORTED_BY_BRIDGE.
    private val actions = listOf(ScriptAction("toggle_bluetooth"), ScriptAction("take_screenshot"))

    private val appRan = mutableListOf<String>()
    private val executor = ScriptActionExecutor { action -> appRan.add(action.type); true }

    @Test fun withTheBridgeRunningTheChainGoesToItAndTheAppRunsOnlyItsShare() = runBlocking {
        val sent = mutableListOf<List<ScriptAction>>()
        val report = listOf("toggle_bluetooth" to true, "take_screenshot" to false)

        val run = runChain(actions, executor) { sent.add(it); BridgeRunResult(1, 2, report) }

        assertEquals(listOf(actions), sent)
        assertEquals(listOf("take_screenshot"), appRan)
        assertEquals(listOf(null, true), run.appResults)
        assertEquals(report, run.bridgeReport)
    }

    @Test fun withNoBridgeTheAppRunsEveryActionExactlyOnce() = runBlocking {
        val run = runChain(actions, executor) { null }

        assertEquals(listOf("toggle_bluetooth", "take_screenshot"), appRan)
        assertEquals(listOf(true, true), run.appResults)
        assertNull(run.bridgeReport)
    }
}
