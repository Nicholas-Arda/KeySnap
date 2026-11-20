package com.example.bridge;

import java.util.Arrays;
import java.util.List;

/**
 * Bluetooth on/off from the shell UID. {@code svc bluetooth} first; ColorOS with its permission
 * monitor on (Developer options "Disable system optimization" off, the default) refuses it with
 * "uid 2000 does not have UPDATE_APP_OPS_STATS" (PLG110, 2026-11-19), while Wi-Fi, data, location
 * and airplane mode still work. Then Android's own "An app wants to turn Bluetooth on/off" dialog is
 * opened and its Allow pressed, which the system grants as itself.
 *
 * <p>Safety lock: it taps only a dialog of an allow-listed system settings package that shows a
 * Decline button (android:id/button2), and only its Allow: button1 on AOSP, button3 on ColorOS; and
 * only if that package still holds focus right before the tap, since a dump takes seconds. Nothing
 * else on screen is read; the dump is dropped and never logged.
 */
final class BluetoothSwitch {

    private static final String REQUEST_ENABLE = "android.bluetooth.adapter.action.REQUEST_ENABLE";
    private static final String REQUEST_DISABLE = "android.bluetooth.adapter.action.REQUEST_DISABLE";
    /** A dump takes 1-3 s; at 3 s the dialog was missed once and left on screen (PLG110, 2026-11-19). */
    private static final long DIALOG_DEADLINE_MS = 6_000L;
    private static final long STATE_DEADLINE_MS = 5_000L;
    private static final long POLL_MS = 300L;
    /** Window owners of the request dialog: AOSP Settings, ColorOS wireless settings (PLG110). */
    static final List<String> DIALOG_PACKAGES = Arrays.asList("com.android.settings", "com.oplus.wirelesssettings");

    private BluetoothSwitch() {}

    static boolean toggle() {
        return set(!ConstraintEvaluator.isBluetoothOn());
    }

    static boolean set(boolean on) {
        if (ShellKeyInjector.runShell("svc", "bluetooth", on ? "enable" : "disable")) return true;
        BridgeLog.i("Bluetooth: svc refused, using the system dialog");
        if (!ShellKeyInjector.runShell("am", "start", "-a", on ? REQUEST_ENABLE : REQUEST_DISABLE)) return false;
        int[] tap = null;
        for (long end = System.currentTimeMillis() + DIALOG_DEADLINE_MS; tap == null && System.currentTimeMillis() < end; ) {
            String xml = SendButton.dump();
            tap = xml == null ? null : allowButton(xml);
            if (tap == null && !pause()) return false;
        }
        if (tap == null || !dialogFocused(ShellKeyInjector.runShellForOutput("dumpsys", "window"))
                || !ShellKeyInjector.runShell("input", "tap", String.valueOf(tap[0]), String.valueOf(tap[1]))) {
            BridgeLog.w("Bluetooth: no system dialog to confirm");
            return false;
        }
        for (long end = System.currentTimeMillis() + STATE_DEADLINE_MS; System.currentTimeMillis() < end; ) {
            if (ConstraintEvaluator.isBluetoothOn() == on) return true;
            if (!pause()) return false;
        }
        return false;
    }

    /** Centre of the dialog's Allow, or null unless the safety lock above holds in this dump. */
    static int[] allowButton(String xml) {
        List<SendButton.UiNode> nodes = SendButton.parse(xml);
        if (nodes == null) return null;
        SendButton.UiNode decline = null;
        for (SendButton.UiNode n : nodes) {
            if ("android:id/button2".equals(n.resourceId)) decline = n;
        }
        if (decline == null || !DIALOG_PACKAGES.contains(decline.pkg)) return null;
        for (String id : new String[] {"android:id/button1", "android:id/button3"}) {
            for (SendButton.UiNode n : nodes) {
                if (id.equals(n.resourceId) && n.pkg.equals(decline.pkg) && n.clickable && n.bounds != null) {
                    return new int[] {(n.bounds[0] + n.bounds[2]) / 2, (n.bounds[1] + n.bounds[3]) / 2};
                }
            }
        }
        return null;
    }

    /**
     * Whether a display's focus (`dumpsys window` prints one mCurrentFocus per display) is the
     * Bluetooth RequestPermissionActivity of an allow-listed package, e.g.
     * "Window{57b773f u0 com.oplus.wirelesssettings/com.android.settings.bluetooth.RequestPermissionActivity}".
     */
    static boolean dialogFocused(String dumpsysWindow) {
        if (dumpsysWindow == null) return false;
        for (String line : dumpsysWindow.split("\n")) {
            if (!line.contains("mCurrentFocus=") || !line.contains(".bluetooth.RequestPermissionActivity}")) continue;
            for (String pkg : DIALOG_PACKAGES) {
                if (line.contains(" " + pkg + "/")) return true;
            }
        }
        return false;
    }

    private static boolean pause() {
        try {
            Thread.sleep(POLL_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
