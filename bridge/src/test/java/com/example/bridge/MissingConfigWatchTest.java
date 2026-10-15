package com.example.bridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MissingConfigWatchTest {
    @Test
    public void presentFileNeverShutsDown() {
        MissingConfigWatch watch = new MissingConfigWatch(3);
        for (int i = 0; i < 20; i++) assertFalse(watch.shouldShutDown(true));
    }

    @Test
    public void missingWithinGraceKeepsRunning() {
        MissingConfigWatch watch = new MissingConfigWatch(3);
        for (int i = 0; i < 3; i++) assertFalse(watch.shouldShutDown(false));
    }

    @Test
    public void missingPastGraceShutsDown() {
        MissingConfigWatch watch = new MissingConfigWatch(3);
        for (int i = 0; i < 3; i++) watch.shouldShutDown(false);
        assertTrue(watch.shouldShutDown(false));
    }

    @Test
    public void reappearingFileResetsTheCount() {
        MissingConfigWatch watch = new MissingConfigWatch(3);
        for (int i = 0; i < 3; i++) watch.shouldShutDown(false);
        assertFalse(watch.shouldShutDown(true));
        for (int i = 0; i < 3; i++) assertFalse(watch.shouldShutDown(false));
        assertTrue(watch.shouldShutDown(false));
    }
}
