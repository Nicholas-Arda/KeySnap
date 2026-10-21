package com.example.bridge;

/** One recent display-only report. Bursts during cold startup replace older reports. */
final class FeedbackMailbox {
    static final long MAX_AGE_MS = 3000;
    private String report;
    private long receivedAt;

    synchronized void offer(String value, long now) {
        report = value;
        receivedAt = now;
    }

    synchronized String peek(long now) {
        if (now - receivedAt > MAX_AGE_MS || now < receivedAt) report = null;
        return report;
    }

    synchronized void clear() {
        report = null;
    }
}
