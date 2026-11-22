package com.example.bridge;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

/**
 * Mind Space through the phone's own side key (OPPO Snap Key / OnePlus Plus Key). The system turns
 * that key into an internal code which {@code com.oplus.gesture} handles according to the
 * {@code oplus_action_button_switch_state} setting. Sending the code ourselves while the setting is
 * {@code flash_memory} does what the key would: one tap saves the screen to Mind Space, two taps about
 * 150 ms apart open Mind Space. {@link #KEYCODE_SIDE_KEY_HOLD} is the key held down: with the screen on
 * the gesture app runs the current job's press-and-hold action (screen translate, start recording).
 * The setting is put back right after, so the user's own key keeps its
 * job. {@code input keyevent} cannot send these codes (above LAST_KEYCODE), hence InputManager.
 * Verified on PLG110 (ColorOS 17 CN) 2026-11-21/22; a phone without the setting reports "null" and
 * nothing is sent.
 */
final class SideKey {

    static final int KEYCODE_SIDE_KEY_TAP = 781;
    static final int KEYCODE_SIDE_KEY_HOLD = 782;
    static final String SETTING = "oplus_action_button_switch_state";
    static final String MIND_SPACE = "flash_memory";
    static final String TRANSLATE = "translate";
    static final String RECORDING = "recording";
    private static final long TAP_GAP_MS = 150;
    private static final long RESTORE_DELAY_MS = 400;
    private static final int INJECT_ASYNC = 0;

    private SideKey() {
    }

    /** One tap = save the screen to Mind Space; two = open Mind Space. */
    static boolean mindSpace(int taps) {
        return press(MIND_SPACE, KEYCODE_SIDE_KEY_TAP, taps);
    }

    /** The press-and-hold action of [job]. */
    static boolean hold(String job) {
        return press(job, KEYCODE_SIDE_KEY_HOLD, 1);
    }

    private static synchronized boolean press(String job, int keyCode, int taps) {
        String previous = ShellKeyInjector.runShellForOutput("settings", "get", "system", SETTING).trim();
        if (!isKnownValue(previous)) return false;
        boolean switched = !job.equals(previous);
        if (switched && !ShellKeyInjector.runShell("settings", "put", "system", SETTING, job)) return false;
        try {
            for (int i = 0; i < taps; i++) {
                if (i > 0) SystemClock.sleep(TAP_GAP_MS);
                if (!tap(keyCode)) return false;
            }
            return true;
        } finally {
            if (switched) {
                SystemClock.sleep(RESTORE_DELAY_MS);
                ShellKeyInjector.runShell("settings", "put", "system", SETTING, previous);
            }
        }
    }

    /** "null" (or nothing) means this phone has no side key setting at all. */
    static boolean isKnownValue(String value) {
        return value != null && !value.isEmpty() && !"null".equals(value) && value.matches("[a-z_]+");
    }

    private static boolean tap(int keyCode) {
        long now = SystemClock.uptimeMillis();
        return inject(event(now, now, KeyEvent.ACTION_DOWN, keyCode)) && inject(event(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode));
    }

    private static KeyEvent event(long downTime, long eventTime, int action, int keyCode) {
        return new KeyEvent(downTime, eventTime, action, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }

    private static boolean inject(KeyEvent event) {
        try {
            Object manager;
            try {
                manager = Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null);
            } catch (ClassNotFoundException olderThan14) {
                manager = Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null);
            }
            Object ok = manager.getClass().getMethod("injectInputEvent", InputEvent.class, int.class)
                    .invoke(manager, event, INJECT_ASYNC);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            BridgeLog.w("side key: inject failed (" + cause.getClass().getSimpleName() + ")");
            return false;
        }
    }
}
