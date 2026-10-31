package com.example.bridge;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedbackDispatchTest {
    @Test public void stalledFeedbackWakeCannotHoldUpTheActionQueue() throws Exception {
        CountDownLatch waking = new CountDownLatch(1);
        CountDownLatch releaseWake = new CountDownLatch(1);
        EventServer server = new EventServer(0, "test-token", new EventServer.Listener() {
            @Override public void onReloadRequested() { }
            @Override public void onShutdownRequested() { }
            @Override public String onRunRequested(String payload) { return "0\t0"; }
            @Override public String onPressSendRequested(String payload) { return "not-found"; }
            @Override public void onFeedbackWakeRequested() {
                waking.countDown();
                try { releaseWake.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        });
        ExecutorService actions = Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<?> run = actions.submit(() -> {
                server.broadcastPending(100, 24, "SINGLE_PRESS", "test", "delay=0", 42);
                server.broadcastTrigger(200, 24, "SINGLE_PRESS", "test", true, "delay=1", 42);
            });
            assertTrue(waking.await(2, TimeUnit.SECONDS));
            // Both publication calls must return even while the display wake remains blocked.
            run.get(2, TimeUnit.SECONDS);
            assertEquals(1L, releaseWake.getCount());
        } finally {
            releaseWake.countDown();
            actions.shutdownNow();
            server.stop();
        }
    }
}
