package com.example.service.adb

import java.net.ConnectException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdbFailurePrivacyTest {
    @Test fun connectionDiagnosticsKeepFailureKindWithoutEndpointOrPayload() {
        val failure = IllegalStateException("PRIVATE_PAYLOAD", ConnectException("PRIVATE_ENDPOINT"))
        val summary = adbFailureSummary(failure)
        assertEquals("IllegalStateException > ConnectException", summary)
        assertFalse(summary.contains("PRIVATE"))
    }

    @Test fun tlsDiagnosticsKeepCauseKindWithoutHandshakeDetails() {
        assertEquals("SSLException", adbFailureSummary(SSLException("PRIVATE_HANDSHAKE")))
    }

    @Test fun cyclicCausesAreBounded() {
        val first = IllegalStateException("PRIVATE_FIRST")
        val second = IllegalArgumentException("PRIVATE_SECOND", first)
        first.initCause(second)
        assertEquals("IllegalStateException > IllegalArgumentException", adbFailureSummary(first))
    }
}
