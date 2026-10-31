package com.example.service.bridge

import com.example.bridge.ActionTestResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionTestReplyTest {
    @Test fun shellOutputRoundTripsAsPrivateResult() {
        val result = ActionTestResult(true, 0, null, 15, false, listOf("secret\tvalue", "a\nb"))
        val decoded = parseTestActionReply("OK\t${result.toWire()}")!!
        assertEquals(result.output, decoded.output)
        assertEquals(0, decoded.exitCode)
        assertEquals(15, decoded.durationMs)
        assertNull(decoded.httpStatus)
    }

    @Test fun httpStatusAndTimeoutRoundTrip() {
        val http = parseTestActionReply("OK\t${ActionTestResult(false, null, 403, 25, false, emptyList()).toWire()}")!!
        assertEquals(403, http.httpStatus)
        assertFalse(http.ok)
        val timeout = parseTestActionReply("OK\t${ActionTestResult(false, null, null, 5000, true, emptyList()).toWire()}")!!
        assertTrue(timeout.timedOut)
        assertNull(timeout.exitCode)
    }

    @Test fun malformedAndMissingRepliesNeverBecomeSuccess() {
        for (line in listOf(null, "", "OK\t{}", "OK\tsecret malformed", "ERROR\tunauthorized")) assertNull(parseTestActionReply(line))
    }
}
