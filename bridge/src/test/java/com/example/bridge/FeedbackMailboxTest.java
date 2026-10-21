package com.example.bridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class FeedbackMailboxTest {
    @Test public void coldStartBurstKeepsOnlyNewestAndExpiresIt() {
        FeedbackMailbox box = new FeedbackMailbox();
        box.offer("flashlight", 100);
        box.offer("ringer", 200);
        assertEquals("ringer", box.peek(300));
        assertEquals("ringer", box.peek(3200));
        assertNull(box.peek(3201));
        assertNull(box.peek(3202));
    }

    @Test public void deliveredReportCannotReplayAndClockResetDropsIt() {
        FeedbackMailbox box = new FeedbackMailbox();
        box.offer("first", 100);
        box.clear();
        assertNull(box.peek(101));
        box.offer("second", 200);
        assertNull(box.peek(199));
    }
}
