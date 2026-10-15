package com.example.bridge;

import android.os.Bundle;
import android.os.IBinder;
import java.lang.reflect.Method;

/**
 * Opens the default assistant the way a long-press of Home does, by asking the voice
 * interaction service for a session. The VOICE_COMMAND / ASSIST intents now land in the plain
 * Google app instead of the Gemini overlay. Hidden API, plain reflection; shell UID only.
 */
final class AssistantLauncher {
    // SHOW_WITH_ASSIST | SHOW_WITH_SCREENSHOT | SHOW_SOURCE_ASSIST_GESTURE
    private static final int FLAGS = 7;

    private AssistantLauncher() {
    }

    static boolean show() {
        try {
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "voiceinteraction");
            Object service = Class.forName("com.android.internal.app.IVoiceInteractionManagerService$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, binder);
            for (Method m : service.getClass().getMethods()) {
                if (!m.getName().equals("showSessionFromSession")) {
                    continue;
                }
                // Android 14+ takes an attribution tag as a fourth parameter.
                Object result = m.getParameterTypes().length == 4
                        ? m.invoke(service, null, new Bundle(), FLAGS, "keysnap")
                        : m.invoke(service, null, new Bundle(), FLAGS);
                return Boolean.TRUE.equals(result);
            }
        } catch (Throwable t) {
            BridgeLog.w("Assistant session unavailable: " + t.getClass().getName());
        }
        return false;
    }
}
