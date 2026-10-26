package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityStatusTrackerTest {
    @Test fun enabledButDisconnectedIsNeverActive() {
        val tracker = AccessibilityStatusTracker()
        assertEquals(AccessibilityStatus.CONNECTING, tracker.update(true, false, 0))
        assertEquals(AccessibilityStatus.NEEDS_RECONNECT, tracker.update(true, false, 3_000))
    }

    @Test fun permissionAloneCannotKeepAnOldConnectionActive() {
        val tracker = AccessibilityStatusTracker()
        assertEquals(AccessibilityStatus.ACTIVE, tracker.update(true, true, 0))
        assertEquals(AccessibilityStatus.CONNECTING, tracker.update(true, false, 100))
        assertEquals(AccessibilityStatus.NEEDS_RECONNECT, tracker.update(true, false, 3_100))
    }

    @Test fun repeatedPollingDoesNotRestartTheGracePeriod() {
        val tracker = AccessibilityStatusTracker()
        assertEquals(AccessibilityStatus.CONNECTING, tracker.update(true, false, 100))
        assertEquals(AccessibilityStatus.CONNECTING, tracker.update(true, false, 1_100))
        assertEquals(AccessibilityStatus.CONNECTING, tracker.update(true, false, 3_099))
        assertEquals(AccessibilityStatus.NEEDS_RECONNECT, tracker.update(true, false, 3_100))
    }

    @Test fun connectionDuringGraceBecomesActiveImmediately() {
        val tracker = AccessibilityStatusTracker()
        tracker.update(true, false, 0)
        assertEquals(AccessibilityStatus.ACTIVE, tracker.update(true, true, 100))
    }

    @Test fun reconnectionClearsTheWarningAndStartsAFreshGraceOnNextLoss() {
        val tracker = AccessibilityStatusTracker()
        tracker.update(true, false, 0)
        tracker.update(true, false, 3_000)
        assertEquals(AccessibilityStatus.ACTIVE, tracker.update(true, true, 4_000))
        assertEquals(AccessibilityStatus.CONNECTING, tracker.update(true, false, 4_001))
        assertEquals(AccessibilityStatus.NEEDS_RECONNECT, tracker.update(true, false, 7_001))
    }

    @Test fun disabledPermissionWinsOverAStaleServiceSignal() {
        assertEquals(AccessibilityStatus.DISABLED, AccessibilityStatusTracker().update(false, true, 0))
    }

    @Test fun disablingPermissionResetsTheBindingGrace() {
        val tracker = AccessibilityStatusTracker()
        tracker.update(true, false, 0)
        tracker.update(true, false, 3_000)
        assertEquals(AccessibilityStatus.DISABLED, tracker.update(false, false, 4_000))
        assertEquals(AccessibilityStatus.CONNECTING, tracker.update(true, false, 8_000))
    }

    @Test fun anIdleConnectedServiceDoesNotNeedAKeyPressHeartbeat() {
        val tracker = AccessibilityStatusTracker()
        assertEquals(AccessibilityStatus.ACTIVE, tracker.update(true, true, 0))
        assertEquals(AccessibilityStatus.ACTIVE, tracker.update(true, true, 86_400_000))
    }
}
