package com.example.bridge;

import android.content.Context;
import android.content.res.Resources;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Read-only platform discovery. Identical code runs under app UID and shell UID. */
public final class BrightnessSettings {
    private static volatile BrightnessScale cached;

    private BrightnessSettings() { }

    public static BrightnessScale scale(Context context) {
        BrightnessScale result = cached;
        if (result != null) return result;
        synchronized (BrightnessSettings.class) {
            if (cached == null) {
                int oplusMaximum = propertyInt("sys.oplus.multibrightness");
                if (oplusMaximum <= 255) oplusMaximum = propertyInt("sys.oppo.multibrightness");
                // Oplus hands app UIDs the same perceptual setting squeezed into 0..255 (measured:
                // shell 2102/4095, app 131/255); only the shell UID sees the full range.
                if (oplusMaximum > BrightnessScale.LEGACY_MAXIMUM
                        && android.os.Process.myUid() >= android.os.Process.FIRST_APPLICATION_UID) {
                    cached = new BrightnessScale(BrightnessScale.LEGACY_MAXIMUM, true);
                } else {
                    cached = BrightnessScale.resolve(oplusMaximum, resourceMaximum(), powerMaximum(context));
                }
            }
            return cached;
        }
    }

    private static int resourceMaximum() {
        try {
            Resources resources = Resources.getSystem();
            int id = resources.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android");
            return id == 0 ? 0 : resources.getInteger(id);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static int powerMaximum(Context context) {
        if (context == null) return 0;
        try {
            PowerManager power = context.getSystemService(PowerManager.class);
            return (Integer) PowerManager.class.getMethod("getMaximumScreenBrightnessSetting").invoke(power);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Hidden API access can be denied; no exemption or new dependency is needed.
            return 0;
        }
    }

    private static int propertyInt(String name) {
        Process process = null;
        try {
            // Fixed read-only OEM keys; no user input, shell interpolation, or logging.
            // getprop also works when SystemProperties reflection is hidden from the app UID.
            process = new ProcessBuilder("/system/bin/getprop", name).start();
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) return 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return Integer.parseInt(reader.readLine().trim());
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception ignored) {
            return 0;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
