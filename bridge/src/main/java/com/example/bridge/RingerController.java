package com.example.bridge;

import java.lang.reflect.Method;

/**
 * Ringer mode from the shell UID, through {@code IAudioService.setRingerModeInternal}.
 *
 * <p>The public {@code AudioManager.setRingerMode} takes the *external* path, where
 * {@code ZenModeHelper} creates a manual Zen rule on every transition into SILENT — that is what
 * lights the Do Not Disturb icon up, no matter which package calls it and no matter how the
 * notification-policy grant was obtained. The internal entry point is the one SystemUI's own
 * volume dialog uses; it moves the ringer and leaves zen mode alone. Verified on the emulator:
 * {@code mode_ringer} 2 -> 0 with {@code zen_mode} staying 0.
 *
 * <p>The app process cannot reach this call (it is guarded for the shell/system UID), so
 * {@code cycle_ringer_mode}/{@code change_ringer_mode} run here whenever the bridge is up and only
 * fall back to the app's DND-permission path when it is not.
 */
final class RingerController {

    static final int MODE_SILENT = 0;
    static final int MODE_VIBRATE = 1;
    static final int MODE_NORMAL = 2;

    private Object audioService;
    private Method setRingerModeInternal;

    void initialize() {
        try {
            Object binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, "audio");
            Class<?> iface = Class.forName("android.media.IAudioService");
            audioService = Class.forName("android.media.IAudioService$Stub")
                    .getMethod("asInterface", Class.forName("android.os.IBinder"))
                    .invoke(null, binder);
            setRingerModeInternal = iface.getMethod("setRingerModeInternal", int.class, String.class);
        } catch (Throwable t) {
            BridgeLog.w("IAudioService.setRingerModeInternal unavailable: " + t);
            audioService = null;
        }
    }

    /** [mode] is one of the MODE_ constants; anything else fails safe. */
    boolean set(int mode) {
        if (audioService == null || mode < MODE_SILENT || mode > MODE_NORMAL) {
            return false;
        }
        try {
            setRingerModeInternal.invoke(audioService, mode, "com.android.shell");
            return true;
        } catch (Throwable t) {
            BridgeLog.w("setRingerModeInternal(" + mode + ") failed: " + t);
            return false;
        }
    }

    boolean cycle() {
        return set(next(current()));
    }

    /** Mirrors nextRingerMode in the app's ScriptActionExecutor.kt. */
    static int next(int current) {
        switch (current) {
            case MODE_NORMAL:
                return MODE_VIBRATE;
            case MODE_VIBRATE:
                return MODE_SILENT;
            default:
                return MODE_NORMAL;
        }
    }

    /**
     * Read the setting rather than AudioManager: a mode set through the internal call does not
     * refresh the AudioManager instance living in this process, so cycling would run off a stale
     * value. Through `settings get` rather than a ContentResolver, because the shell-UID context's
     * resolver throws "Given calling package android does not match caller's uid 2000" — the same
     * mismatch SystemContext works around for the camera, and there is no working around it here.
     */
    private int current() {
        String mode = ConstraintEvaluator.shell("settings", "get", "global", "mode_ringer").trim();
        if (!mode.matches("\\d+")) {
            return MODE_NORMAL;
        }
        int value = Integer.parseInt(mode);
        return value < MODE_SILENT || value > MODE_NORMAL ? MODE_NORMAL : value;
    }

    /** "normal"/"vibrate"/"silent", mirroring ringerModeFor in the app. -1 when unrecognised. */
    static int modeFor(String name) {
        if ("normal".equals(name)) return MODE_NORMAL;
        if ("vibrate".equals(name)) return MODE_VIBRATE;
        if ("silent".equals(name)) return MODE_SILENT;
        return -1;
    }
}
