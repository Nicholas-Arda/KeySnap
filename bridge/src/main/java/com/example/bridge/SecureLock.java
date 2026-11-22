package com.example.bridge;

import android.os.Bundle;
import android.os.IBinder;

/**
 * secure_lock_device: the power menu's "Lockdown". Strong auth first (fingerprint and face refused
 * until the PIN, pattern or password is entered; PLG110 2026-11-21: primaryAuthFlags=20), then the
 * same lock Accessibility's GLOBAL_ACTION_LOCK_SCREEN does: {@code IWindowManager.lockNow} (needs
 * DEVICE_POWER, which shell holds) and the screen off. `require-strong-auth` exists from Android 13;
 * on older builds the command fails and nothing is locked, so a plain lock never passes for a secure one.
 */
final class SecureLock {

    private SecureLock() {
    }

    static boolean lock() {
        if (!ShellKeyInjector.runShell("cmd", "lock_settings", "require-strong-auth", "STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN")) {
            return false;
        }
        try {
            Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "window");
            Object wm = Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
            wm.getClass().getMethod("lockNow", Bundle.class).invoke(wm, (Object) null);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            BridgeLog.w("secure_lock_device: lockNow failed (" + cause.getClass().getSimpleName() + ")");
            return false;
        }
        return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_SLEEP);
    }
}
