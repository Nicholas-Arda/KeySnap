package com.example.bridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the authorized-shell `getevent` stream.
 *
 * <p>Android's own `getevent` performs the evdev open/read and decoding. The bridge never opens
 * an input node itself, never grabs one (EVIOCGRAB) and never injects (sendevent): it is a
 * read-only observer of the kernel stream.
 */
public final class GeteventReader implements Runnable {

    public interface Listener {
        void onKeyEvent(KeyEventMapping.ParsedKey key, String deviceName);

        void onDevicesChanged(Map<String, String> devicesByPath);

        void onStreamError(String message);
    }

    private static final Pattern ADD_DEVICE =
            Pattern.compile("^add device \\d+:\\s*(/dev/input/event\\d+)\\s*$");
    private static final Pattern DEVICE_NAME =
            Pattern.compile("^\\s*name:\\s*\"(.*)\"\\s*$");

    private final Listener listener;
    private final Map<String, String> devicesByPath = new LinkedHashMap<>();
    private volatile boolean running = true;
    private volatile Process current;

    public GeteventReader(Listener listener) {
        this.listener = listener;
    }

    public void stop() {
        running = false;
        Process process = current;
        if (process != null) {
            process.destroy();
        }
    }

    @Override
    public void run() {
        int consecutiveFailures = 0;
        while (running) {
            long startedAt = System.currentTimeMillis();
            try {
                readOnce();
            } catch (Exception e) {
                BridgeLog.e("getevent stream failed", e);
            }
            if (!running) {
                return;
            }
            // A stream that dies immediately means a persistent problem; back off before retrying
            // so a broken device state cannot spin the CPU.
            if (System.currentTimeMillis() - startedAt < 2000) {
                consecutiveFailures++;
            } else {
                consecutiveFailures = 0;
            }
            long backoff = Math.min(30_000L, 500L * (1L << Math.min(consecutiveFailures, 6)));
            listener.onStreamError("getevent exited; restarting in " + backoff + "ms");
            BridgeLog.w("getevent exited; restarting in " + backoff + "ms");
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void readOnce() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("getevent", "-lt");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        current = process;
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8")));
        try {
            String pendingDevice = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!running) {
                    break;
                }
                Matcher addDevice = ADD_DEVICE.matcher(line);
                if (addDevice.matches()) {
                    pendingDevice = addDevice.group(1);
                    devicesByPath.put(pendingDevice, "");
                    continue;
                }
                Matcher name = DEVICE_NAME.matcher(line);
                if (name.matches() && pendingDevice != null) {
                    devicesByPath.put(pendingDevice, name.group(1));
                    listener.onDevicesChanged(new LinkedHashMap<>(devicesByPath));
                    pendingDevice = null;
                    continue;
                }
                KeyEventMapping.ParsedKey parsed = KeyEventMapping.parse(line);
                if (parsed == null || parsed.touchContact
                        || parsed.action == KeyEventMapping.ACTION_REPEAT) {
                    continue;
                }
                String deviceName = parsed.device != null ? devicesByPath.get(parsed.device) : null;
                listener.onKeyEvent(parsed, deviceName);
            }
        } finally {
            try {
                reader.close();
            } catch (Exception ignored) {
                // Stream is being torn down anyway.
            }
            try {
                process.destroy();
                process.waitFor();
            } catch (Exception ignored) {
                // Best effort reap.
            }
            current = null;
        }
    }
}
