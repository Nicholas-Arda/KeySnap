package com.example.bridge;

import android.content.Context;

import java.lang.reflect.Field;

/**
 * Builds a usable {@link Context} inside app_process, which has no Application and no
 * LoadedApk of its own.
 *
 * <p>The raw system context reports package "android", which the camera service rejects for
 * uid 2000 ("Given calling package android does not match caller's uid 2000"). Re-deriving the
 * context for com.android.shell — the package that actually owns uid 2000 — is what makes
 * {@code setTorchMode} succeed. Verified on ColorOS 16 / Android 16.
 */
final class SystemContext {

    private SystemContext() { }

    static Context create() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("systemMain").invoke(null);
            Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);

            Context shellContext;
            try {
                shellContext = system.createPackageContext("com.android.shell", 0);
            } catch (Throwable t) {
                BridgeLog.w("createPackageContext(com.android.shell) failed, using system context: " + t);
                return system;
            }
            forceField(shellContext, "mOpPackageName", "com.android.shell");
            forceField(shellContext, "mBasePackageName", "com.android.shell");
            return shellContext;
        } catch (Throwable t) {
            BridgeLog.e("Could not build a system context", t);
            return null;
        }
    }

    /**
     * Best effort: these ContextImpl fields are not present on every OEM build, and the torch
     * path works even when the attribution source keeps reporting "android".
     */
    private static void forceField(Object target, String name, String value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return;
            }
        }
    }
}
