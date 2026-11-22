package com.example.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Press-gesture resolver for the detached bridge.
 *
 * <p>A direct port of the app's KeyPressScheduler so single/double/long press behaviour is
 * identical whether an event arrives through the Activity, the Accessibility service, or this
 * bridge. Timings come from the config file rather than from constants here.
 */
public final class PressTimingMachine {

    /** Receives resolved gestures; press type is one of the BridgeConfig.PRESS_* values. */
    public interface Listener {
        void onGesture(int keyCode, String pressType);
    }

    /** When a single press resolves; see {@link #singlePressAt}. */
    public static final int SINGLE_AFTER_WINDOW = 0;
    public static final int SINGLE_ON_RELEASE = 1;
    public static final int SINGLE_ON_PRESS = 2;

    /**
     * The one rule for when a key's single press fires, shared by the app's KeyPressScheduler and
     * this machine. Only a double-press shortcut on the key is worth waiting for. A long press, a
     * chord id or chord membership still needs the release (a chord forms while its keys are held).
     * Anything else fires on the down. {@code pressTypes}: the press types of every enabled
     * trigger on {@code keyId}; {@code chords}: the live chords, as ChordTracker gets them.
     */
    public static int singlePressAt(int keyId, java.util.Collection<String> pressTypes, java.util.List<java.util.List<Integer>> chords) {
        if (pressTypes.contains(BridgeConfig.PRESS_DOUBLE)) return SINGLE_AFTER_WINDOW;
        if (pressTypes.contains(BridgeConfig.PRESS_LONG) || keyId < 0) return SINGLE_ON_RELEASE;
        for (java.util.List<Integer> chord : chords) {
            if (chord.contains(keyId)) return SINGLE_ON_RELEASE;
        }
        return SINGLE_ON_PRESS;
    }

    private static final class KeyState {
        Long downAt;
        long lastUpAt = Long.MIN_VALUE;
        int clickCount;
        long sequence;
        long doublePressWindowMs;
        long longPressThresholdMs;
        int singleAt;
        boolean longPressTriggered;
        boolean doublePressTriggered;
        ScheduledFuture<?> longPressJob;
        ScheduledFuture<?> singlePressJob;
    }

    private final Object lock = new Object();
    private final Map<Integer, KeyState> states = new HashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Listener listener;

    public PressTimingMachine(ScheduledExecutorService scheduler, Listener listener) {
        this.scheduler = scheduler;
        this.listener = listener;
    }

    public void onEvent(int keyCode, boolean isDown, long eventTime, long doubleWindowMs, long longThresholdMs) {
        onEvent(keyCode, isDown, eventTime, doubleWindowMs, longThresholdMs, SINGLE_AFTER_WINDOW);
    }

    /** {@code singleAt}: {@link #singlePressAt} for this key. */
    public void onEvent(int keyCode, boolean isDown, long eventTime, long doubleWindowMs, long longThresholdMs, int singleAt) {
        if (isDown) {
            onDown(keyCode, eventTime, doubleWindowMs, longThresholdMs, singleAt);
        } else {
            onUp(keyCode, eventTime);
        }
    }

    public void reset() {
        synchronized (lock) {
            for (KeyState state : states.values()) {
                cancel(state.longPressJob);
                cancel(state.singlePressJob);
            }
            states.clear();
        }
    }

    /** Drops one key's pending press, e.g. when it turns out to be part of a chord. Mirrors KeyPressScheduler.cancel. */
    public void cancel(int keyCode) {
        synchronized (lock) {
            KeyState state = states.get(keyCode);
            if (state != null) {
                clearStateLocked(keyCode, state);
            }
        }
    }

    private void onDown(int keyCode, long eventTime, long doubleWindowMs, long longThresholdMs, int singleAt) {
        int singles = 0;
        boolean triggerDouble = false;
        synchronized (lock) {
            KeyState state = states.get(keyCode);
            if (state == null) {
                state = new KeyState();
                states.put(keyCode, state);
            }
            if (state.downAt != null) {
                return;
            }
            if (state.clickCount == 1) {
                long elapsed = eventTime - state.lastUpAt;
                if (elapsed >= 0 && elapsed <= state.doublePressWindowMs) {
                    cancel(state.singlePressJob);
                    state.singlePressJob = null;
                    state.clickCount = 0;
                    state.sequence++;
                    state.downAt = eventTime;
                    state.doublePressTriggered = true;
                    state.longPressTriggered = false;
                    cancel(state.longPressJob);
                    state.longPressJob = null;
                    triggerDouble = true;
                } else {
                    cancel(state.singlePressJob);
                    state.singlePressJob = null;
                    state.clickCount = 0;
                    state.sequence++;
                    singles++;
                    beginPressLocked(keyCode, state, eventTime, doubleWindowMs, longThresholdMs, singleAt);
                }
            } else {
                beginPressLocked(keyCode, state, eventTime, doubleWindowMs, longThresholdMs, singleAt);
            }
            if (state.downAt != null && state.singleAt == SINGLE_ON_PRESS) {
                singles++;
            }
        }
        for (int i = 0; i < singles; i++) {
            listener.onGesture(keyCode, BridgeConfig.PRESS_SINGLE);
        }
        if (triggerDouble) {
            listener.onGesture(keyCode, BridgeConfig.PRESS_DOUBLE);
        }
    }

    private void onUp(int keyCode, long eventTime) {
        boolean triggerLong = false;
        boolean triggerSingle = false;
        synchronized (lock) {
            KeyState state = states.get(keyCode);
            if (state == null || state.downAt == null) {
                return;
            }
            long downAt = state.downAt;
            state.downAt = null;
            cancel(state.longPressJob);
            state.longPressJob = null;

            // SINGLE_ON_PRESS fired on the down.
            if (state.longPressTriggered || state.doublePressTriggered || state.singleAt == SINGLE_ON_PRESS) {
                clearStateLocked(keyCode, state);
                return;
            }
            if (eventTime - downAt >= state.longPressThresholdMs) {
                state.longPressTriggered = true;
                triggerLong = true;
                clearStateLocked(keyCode, state);
            } else if (state.singleAt == SINGLE_ON_RELEASE) {
                triggerSingle = true;
                clearStateLocked(keyCode, state);
            } else {
                state.clickCount = 1;
                state.lastUpAt = eventTime;
                scheduleSinglePressLocked(keyCode, state);
            }
        }
        if (triggerLong) {
            listener.onGesture(keyCode, BridgeConfig.PRESS_LONG);
        }
        if (triggerSingle) {
            listener.onGesture(keyCode, BridgeConfig.PRESS_SINGLE);
        }
    }

    private void beginPressLocked(int keyCode, KeyState state, long eventTime,
                                  long doubleWindowMs, long longThresholdMs, int singleAt) {
        state.doublePressWindowMs = doubleWindowMs;
        state.longPressThresholdMs = longThresholdMs;
        state.singleAt = singleAt;
        state.downAt = eventTime;
        state.clickCount = 0;
        state.longPressTriggered = false;
        state.doublePressTriggered = false;
        state.sequence++;
        final long sequence = state.sequence;
        final KeyState captured = state;
        cancel(state.longPressJob);
        state.longPressJob = null;
        if (singleAt == SINGLE_ON_PRESS) {
            return; // No long press on this key, and its single fires now.
        }
        state.longPressJob = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                boolean shouldTrigger = false;
                synchronized (lock) {
                    KeyState current = states.get(keyCode);
                    if (current == captured && current.sequence == sequence
                            && current.downAt != null && !current.doublePressTriggered) {
                        current.longPressTriggered = true;
                        shouldTrigger = true;
                    }
                }
                if (shouldTrigger) {
                    listener.onGesture(keyCode, BridgeConfig.PRESS_LONG);
                }
            }
        }, longThresholdMs, TimeUnit.MILLISECONDS);
    }

    private void scheduleSinglePressLocked(final int keyCode, KeyState state) {
        cancel(state.singlePressJob);
        final long sequence = state.sequence;
        final KeyState captured = state;
        state.singlePressJob = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                boolean shouldTrigger = false;
                synchronized (lock) {
                    KeyState current = states.get(keyCode);
                    if (current == captured && current.sequence == sequence
                            && current.clickCount == 1 && current.downAt == null) {
                        current.clickCount = 0;
                        current.singlePressJob = null;
                        shouldTrigger = true;
                        clearStateLocked(keyCode, current);
                    }
                }
                if (shouldTrigger) {
                    listener.onGesture(keyCode, BridgeConfig.PRESS_SINGLE);
                }
            }
        }, state.doublePressWindowMs, TimeUnit.MILLISECONDS);
    }

    private void clearStateLocked(int keyCode, KeyState state) {
        cancel(state.longPressJob);
        cancel(state.singlePressJob);
        if (states.get(keyCode) == state) {
            states.remove(keyCode);
        }
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }
}
