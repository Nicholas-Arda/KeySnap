package com.example.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantInvocationGateTest {
    @Test fun firstInvocationAtBootIsAcceptedAndDuplicatesAreDropped() {
        val gate = AssistantInvocationGate()
        assertTrue(gate.accept(0))
        assertFalse(gate.accept(0))
        assertFalse(gate.accept(299))
        assertTrue(gate.accept(300))
    }

    @Test fun outgoingAssistantSuppressesEveryCallbackForTwoSecondsWithoutExtendingTheWindow() {
        val gate = AssistantInvocationGate()
        gate.markOutgoing(1_000)
        assertFalse(gate.accept(1_000))
        assertFalse(gate.accept(2_999))
        assertTrue(gate.accept(3_000))
    }

    @Test fun anotherOutgoingLaunchRestartsTheGuard() {
        val gate = AssistantInvocationGate()
        gate.markOutgoing(1_000)
        gate.markOutgoing(2_500)
        assertFalse(gate.accept(3_000))
        assertTrue(gate.accept(4_500))
    }
}
