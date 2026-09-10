package com.example.bridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Output-side key injection through Android's authorized-shell {@code input} command.
 *
 * <p>This is the sanctioned direction: the bridge <em>reads</em> keys only from {@code getevent}
 * and never touches evdev to write. Media and volume transport keys are delivered here instead of
 * through AudioManager because this process has no package identity, which is what makes the
 * framework-level audio APIs unreliable for uid 2000 (the same limitation documented in
 * {@link TorchController}).
 */
final class ShellKeyInjector {

    static final int KEYCODE_VOLUME_MUTE = 164;
    static final int KEYCODE_VOLUME_UP = 24;
    static final int KEYCODE_VOLUME_DOWN = 25;
    static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    static final int KEYCODE_MEDIA_NEXT = 87;
    static final int KEYCODE_MEDIA_PREVIOUS = 88;
    static final int KEYCODE_MEDIA_PLAY = 126;
    static final int KEYCODE_MEDIA_PAUSE = 127;
    static final int KEYCODE_MEDIA_STOP = 86;
    static final int KEYCODE_MEDIA_FAST_FORWARD = 90;
    static final int KEYCODE_MEDIA_REWIND = 89;
    static final int KEYCODE_BACK = 4;
    static final int KEYCODE_HOME = 3;
    static final int KEYCODE_MENU = 82;
    static final int KEYCODE_APP_SWITCH = 187;
    static final int KEYCODE_SYSRQ = 120;
    static final int KEYCODE_SLEEP = 223;
    static final int KEYCODE_WAKEUP = 224;
    static final int KEYCODE_CUT = 277;
    static final int KEYCODE_COPY = 278;
    static final int KEYCODE_PASTE = 279;

    private ShellKeyInjector() {
    }

    static boolean sendKey(int keyCode) {
        try {
            Process process = new ProcessBuilder("input", "keyevent", String.valueOf(keyCode))
                    .redirectErrorStream(true)
                    .start();
            int exit = process.waitFor();
            if (exit != 0) {
                BridgeLog.w("input keyevent " + keyCode + " exited " + exit);
                return false;
            }
            return true;
        } catch (Exception e) {
            BridgeLog.w("input keyevent " + keyCode + " failed: " + e);
            return false;
        }
    }

    /**
     * Runs an arbitrary shell command (e.g. {@code cmd statusbar expand-notifications}) with the
     * same authorized-shell privilege as {@link #sendKey(int)}. Returns true only on exit code 0.
     */
    static boolean runShell(String... command) {
        String description = describe(command);
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            int exit = process.waitFor();
            if (exit != 0) {
                BridgeLog.w(description + " exited " + exit);
                return false;
            }
            return true;
        } catch (Exception e) {
            BridgeLog.w(description + " failed: " + e);
            return false;
        }
    }

    /**
     * Same as {@link #runShell(String...)} but logs only the leading program name.
     *
     * <p>User-authored commands, URLs and intent extras routinely carry tokens and passwords.
     * The bridge's log file outlives the app, so the arguments of those actions must never
     * reach it — only enough to tell which action failed.
     */
    static boolean runShellUnlogged(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            int exit = process.waitFor();
            if (exit != 0) {
                BridgeLog.w(command[0] + " exited " + exit);
                return false;
            }
            return true;
        } catch (Exception e) {
            BridgeLog.w(command[0] + " failed: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Runs a shell command and returns its combined stdout+stderr, or an empty string on failure.
     * Used by actions that need to read state (e.g. the enabled input methods) before acting.
     */
    static String runShellForOutput(String... command) {
        String description = describe(command);
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            process.waitFor();
            return out.toString();
        } catch (Exception e) {
            BridgeLog.w(description + " failed: " + e);
            return "";
        }
    }

    private static String describe(String... command) {
        StringBuilder sb = new StringBuilder();
        for (String part : command) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part);
        }
        return sb.toString();
    }
}
