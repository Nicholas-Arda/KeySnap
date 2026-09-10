package com.example.bridge;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Logging for a process with no app sandbox: mirrors to logcat and to a shell-owned file so
 * failures remain diagnosable after the app has been killed and `adb logcat` history rolled.
 */
public final class BridgeLog {

    public static final String TAG = "ArdaBridge";

    private static final long MAX_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT);

    private static File logFile;

    private BridgeLog() { }

    public static void init(File file) {
        synchronized (LOCK) {
            logFile = file;
            rotateIfNeededLocked();
        }
    }

    public static void i(String message) {
        Log.i(TAG, message);
        write("I", message);
    }

    public static void w(String message) {
        Log.w(TAG, message);
        write("W", message);
    }

    public static void e(String message, Throwable error) {
        Log.e(TAG, message, error);
        StringWriter trace = new StringWriter();
        if (error != null) {
            error.printStackTrace(new PrintWriter(trace));
        }
        write("E", message + (error != null ? "\n" + trace : ""));
    }

    private static void write(String level, String message) {
        synchronized (LOCK) {
            if (logFile == null) {
                return;
            }
            FileWriter writer = null;
            try {
                rotateIfNeededLocked();
                writer = new FileWriter(logFile, true);
                writer.write(FORMAT.format(new Date()) + " " + level + " " + message + "\n");
            } catch (Exception ignored) {
                // Logging must never take the bridge down.
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Exception ignored) {
                        // Nothing useful to do here.
                    }
                }
            }
        }
    }

    private static void rotateIfNeededLocked() {
        try {
            if (logFile != null && logFile.length() > MAX_BYTES) {
                File previous = new File(logFile.getParentFile(), logFile.getName() + ".1");
                if (previous.exists() && !previous.delete()) {
                    return;
                }
                if (!logFile.renameTo(previous)) {
                    // Truncate in place when the rename is refused, to keep the file bounded.
                    new FileWriter(logFile, false).close();
                }
            }
        } catch (Exception ignored) {
            // Ignore rotation failures; bounded logging is best effort.
        }
    }
}
