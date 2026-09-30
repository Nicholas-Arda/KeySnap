package com.example.service.bridge

import com.example.bridge.BridgeProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeRunReplyTest {

    @Test fun theRunReplyCarriesEachActionsResult() {
        val field = BridgeProtocol.encodeActionResults(listOf("toggle_wifi", "take_screenshot"), booleanArrayOf(true, false))

        val reply = parseRunReply("OK\t1\t2\t$field")

        assertEquals(BridgeRunResult(1, 2, listOf("toggle_wifi" to true, "take_screenshot" to false)), reply)
    }

    @Test fun anOlderBridgesReplyHasNoPerActionResults() {
        assertEquals(BridgeRunResult(1, 2, null), parseRunReply("OK\t1\t2"))
    }

    @Test fun aRejectedReplyIsNoResult() {
        assertNull(parseRunReply("ERROR\tauth"))
        assertNull(parseRunReply(null))
    }

    @Test fun aPayloadTheBridgeCouldNotParseIsNoResult() {
        // BridgeMain.onRunRequested answers "0 0" when parsing fails; the app must run the chain itself.
        assertNull(parseRunReply("OK\t0\t0"))
    }
}
