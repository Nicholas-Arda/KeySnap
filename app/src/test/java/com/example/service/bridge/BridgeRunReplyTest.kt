package com.example.service.bridge

import com.example.bridge.BridgeProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** Key events and triggers are pushed to every connection; one arriving first must not be taken for the reply. */
    @Test fun pushedLinesBeforeTheReplyAreSkipped() {
        val lines = ArrayDeque(
            listOf(
                "EVENT\t1\t24\tdown\t1\tAdvanced Mode",
                "TRIGGER\t1\t24\tSINGLE_PRESS\ts1\t1\ttoggle_wifi=1",
                "DEVICE\t/dev/input/event3\tgpio-keys",
                "OK\t1\t1\ttoggle_wifi=1",
            ),
        )
        assertEquals(BridgeRunResult(1, 1, listOf("toggle_wifi" to true)), parseRunReply(readReply { lines.removeFirstOrNull() }))

        val pressSend = ArrayDeque(listOf("EVENT\t1\t24\tup\t1\tAdvanced Mode", "OK\tok"))
        assertEquals(BridgeProtocol.PRESS_SEND_OK, parsePressSendReply(readReply { pressSend.removeFirstOrNull() }))
        val unconfirmed = ArrayDeque(listOf("TRIGGER\t1\t24\tSINGLE_PRESS\ts1\t1\t", "OK\tunconfirmed"))
        assertEquals(BridgeProtocol.PRESS_SEND_UNCONFIRMED, parsePressSendReply(readReply { unconfirmed.removeFirstOrNull() }))
    }

    /** The bridge marks a connection logged in just before answering HELLO, so a push can come first. */
    @Test fun theHandshakeSkipsAPushedLine() {
        val lines = ArrayDeque(listOf("EVENT\t1\t24\tdown\t1\tx", "OK\t17\t4242\t1000\trunning"))
        assertEquals(BridgeStatus(17, 4242, 1000L, "running"), parseHandshake(readReply { lines.removeFirstOrNull() }))
        assertNull(parseHandshake("ERROR\tunauthorized"))
        assertNull(parseHandshake(null))
    }

    /** A key event landing before BYE must not make a stop look refused, nor an error look like one. */
    @Test fun theShutdownAckSkipsPushedLines() {
        val lines = ArrayDeque(listOf("TRIGGER\t1\t24\tSINGLE_PRESS\ts1\t1\t", "BYE"))
        assertTrue(isShutdownAck(readReply { lines.removeFirstOrNull() }))
        // The bridge closing the connection as it exits also counts.
        assertTrue(isShutdownAck(readReply { null }))
        assertFalse(isShutdownAck("ERROR\tunknown-command"))
    }

    @Test fun anErrorIsAReplyAndAnEndedStreamIsNone() {
        val error = ArrayDeque(listOf("EVENT\t1\t24\tdown\t1\tx", "ERROR\tunknown-command"))
        assertEquals("ERROR\tunknown-command", readReply { error.removeFirstOrNull() })
        assertNull(parsePressSendReply("ERROR\tunknown-command"))
        val onlyEvents = ArrayDeque(listOf("EVENT\t1\t24\tdown\t1\tx"))
        assertNull(readReply { onlyEvents.removeFirstOrNull() })
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
