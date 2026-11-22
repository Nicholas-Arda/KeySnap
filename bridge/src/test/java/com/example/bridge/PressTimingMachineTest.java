package com.example.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Guards the press semantics the bridge executes on the device.
 *
 * The bridge resolves gestures itself, so these are the same single/double/long rules the
 * in-app scheduler applies; if they drift, a mapping behaves differently depending on whether
 * Advanced Mode happens to be running.
 */
public class PressTimingMachineTest {

    /** Shortened from the 300/500 ms production defaults to keep the suite fast. */
    private static final long DOUBLE_WINDOW_MS = 120L;
    private static final long LONG_THRESHOLD_MS = 240L;

    private ScheduledExecutorService scheduler;
    private List<String> gestures;
    private PressTimingMachine machine;

    @Before
    public void setUp() {
        scheduler = Executors.newScheduledThreadPool(2);
        gestures = Collections.synchronizedList(new ArrayList<String>());
        machine = new PressTimingMachine(scheduler, (keyCode, pressType) -> gestures.add(pressType));
    }

    @After
    public void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    public void singlePressResolvesAfterTheDoublePressWindow() throws Exception {
        long now = 1_000L;
        machine.onEvent(7, true, now, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.onEvent(7, false, now + 30, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);

        // Nothing may fire until the window that could still turn this into a double press closes.
        Thread.sleep(DOUBLE_WINDOW_MS / 2);
        assertEquals(Collections.emptyList(), new ArrayList<>(gestures));

        Thread.sleep(DOUBLE_WINDOW_MS);
        assertEquals(Collections.singletonList(BridgeConfig.PRESS_SINGLE), new ArrayList<>(gestures));
    }

    @Test
    public void onlyADoublePressIsWorthWaitingFor() {
        List<List<Integer>> chords = Collections.singletonList(java.util.Arrays.asList(24, 25));
        assertEquals(PressTimingMachine.SINGLE_AFTER_WINDOW, PressTimingMachine.singlePressAt(7,
                java.util.Arrays.asList(BridgeConfig.PRESS_SINGLE, BridgeConfig.PRESS_DOUBLE), chords));
        assertEquals(PressTimingMachine.SINGLE_ON_RELEASE, PressTimingMachine.singlePressAt(7,
                java.util.Arrays.asList(BridgeConfig.PRESS_SINGLE, BridgeConfig.PRESS_LONG), chords));
        assertEquals(PressTimingMachine.SINGLE_ON_RELEASE, PressTimingMachine.singlePressAt(24,
                Collections.singletonList(BridgeConfig.PRESS_SINGLE), chords));
        assertEquals(PressTimingMachine.SINGLE_ON_RELEASE, PressTimingMachine.singlePressAt(-5,
                Collections.singletonList(BridgeConfig.PRESS_SINGLE), chords));
        assertEquals(PressTimingMachine.SINGLE_ON_PRESS, PressTimingMachine.singlePressAt(7,
                Collections.singletonList(BridgeConfig.PRESS_SINGLE), chords));
    }

    @Test
    public void singleOnPressFiresOnTheDownOnly() throws Exception {
        machine.onEvent(7, true, 1_000L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS, PressTimingMachine.SINGLE_ON_PRESS);
        assertEquals(Collections.singletonList(BridgeConfig.PRESS_SINGLE), new ArrayList<>(gestures));

        // Held past the long threshold and released: still the one single, nothing else.
        machine.onEvent(7, false, 1_000L + LONG_THRESHOLD_MS + 50, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS, PressTimingMachine.SINGLE_ON_PRESS);
        Thread.sleep(LONG_THRESHOLD_MS + DOUBLE_WINDOW_MS + 50);
        assertEquals(Collections.singletonList(BridgeConfig.PRESS_SINGLE), new ArrayList<>(gestures));

        // A quick second press is a second single.
        machine.onEvent(7, true, 2_000L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS, PressTimingMachine.SINGLE_ON_PRESS);
        machine.onEvent(7, false, 2_030L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS, PressTimingMachine.SINGLE_ON_PRESS);
        assertEquals(java.util.Arrays.asList(BridgeConfig.PRESS_SINGLE, BridgeConfig.PRESS_SINGLE), new ArrayList<>(gestures));
    }

    @Test
    public void singleOnReleaseSkipsTheWindowButKeepsTheLongPress() throws Exception {
        machine.onEvent(7, true, 1_000L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS, PressTimingMachine.SINGLE_ON_RELEASE);
        assertEquals(Collections.emptyList(), new ArrayList<>(gestures));
        machine.onEvent(7, false, 1_030L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS, PressTimingMachine.SINGLE_ON_RELEASE);
        assertEquals(Collections.singletonList(BridgeConfig.PRESS_SINGLE), new ArrayList<>(gestures));

        gestures.clear();
        machine.onEvent(7, true, 2_000L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS, PressTimingMachine.SINGLE_ON_RELEASE);
        Thread.sleep(LONG_THRESHOLD_MS + 60);
        machine.onEvent(7, false, 2_000L + LONG_THRESHOLD_MS + 60, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS, PressTimingMachine.SINGLE_ON_RELEASE);
        assertEquals(Collections.singletonList(BridgeConfig.PRESS_LONG), new ArrayList<>(gestures));
    }

    @Test
    public void secondPressInsideTheWindowIsADoublePressAndSuppressesTheSingle() throws Exception {
        long now = 2_000L;
        machine.onEvent(7, true, now, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.onEvent(7, false, now + 20, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.onEvent(7, true, now + 60, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.onEvent(7, false, now + 80, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);

        Thread.sleep(DOUBLE_WINDOW_MS + LONG_THRESHOLD_MS + 100);
        assertEquals(Collections.singletonList(BridgeConfig.PRESS_DOUBLE), new ArrayList<>(gestures));
    }

    @Test
    public void holdingPastTheThresholdFiresLongPressWhileStillDown() throws Exception {
        machine.onEvent(7, true, 3_000L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);

        Thread.sleep(LONG_THRESHOLD_MS + 120);
        assertEquals(Collections.singletonList(BridgeConfig.PRESS_LONG), new ArrayList<>(gestures));

        // The release of an already-resolved long press must not add a single press.
        machine.onEvent(7, false, 3_000L + LONG_THRESHOLD_MS + 100, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        Thread.sleep(DOUBLE_WINDOW_MS + 80);
        assertEquals(Collections.singletonList(BridgeConfig.PRESS_LONG), new ArrayList<>(gestures));
    }

    @Test
    public void unmappedSecondKeyKeepsIndependentState() throws Exception {
        long now = 4_000L;
        machine.onEvent(7, true, now, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.onEvent(7, false, now + 20, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.onEvent(9, true, now + 30, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.onEvent(9, false, now + 50, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);

        Thread.sleep(DOUBLE_WINDOW_MS + 150);
        // A press on a different key must not be read as the second half of a double press.
        assertEquals(2, gestures.size());
        assertTrue(gestures.contains(BridgeConfig.PRESS_SINGLE));
    }

    @Test
    public void resetDropsPendingGestures() throws Exception {
        machine.onEvent(7, true, 5_000L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.onEvent(7, false, 5_020L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.reset();

        Thread.sleep(DOUBLE_WINDOW_MS + LONG_THRESHOLD_MS + 100);
        assertEquals(Collections.emptyList(), new ArrayList<>(gestures));
    }

    @Test
    public void schedulerIsNotLeakedBetweenGestures() throws Exception {
        for (int i = 0; i < 5; i++) {
            long base = 6_000L + (i * 1_000L);
            machine.onEvent(7, true, base, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
            machine.onEvent(7, false, base + 20, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
            Thread.sleep(DOUBLE_WINDOW_MS + 80);
        }
        assertEquals(5, gestures.size());
        scheduler.shutdown();
        assertTrue(scheduler.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    public void cancelDropsAPendingPressAndItsRelease() throws Exception {
        machine.onEvent(7, true, 4_000L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);
        machine.cancel(7);
        machine.onEvent(7, false, 4_050L, DOUBLE_WINDOW_MS, LONG_THRESHOLD_MS);

        Thread.sleep(DOUBLE_WINDOW_MS + LONG_THRESHOLD_MS + 100);
        assertEquals(Collections.emptyList(), new ArrayList<>(gestures));
    }
}
