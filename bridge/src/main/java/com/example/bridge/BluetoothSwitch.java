package com.example.bridge;

/**
 * Bluetooth on/off from the shell UID through {@code svc bluetooth}, nothing else. ColorOS with its
 * permission monitor on (Developer options "Disable system optimization" off, the default) refuses
 * it with "uid 2000 does not have UPDATE_APP_OPS_STATS" (PLG110, 2026-11-19); turning that option on
 * makes it work at once, no reboot (PLG110, 2026-11-20). The editor tells the user so.
 *
 * <p>Never fall back to Android's "An app wants to turn Bluetooth on" dialog: a prompt is slower
 * than the Quick Settings tile, which defeats the shortcut (owner's decision, 2026-11-20).
 */
final class BluetoothSwitch {

    private BluetoothSwitch() {}

    static boolean toggle() {
        return set(!ConstraintEvaluator.isBluetoothOn());
    }

    static boolean set(boolean on) {
        return ShellKeyInjector.runShell("svc", "bluetooth", on ? "enable" : "disable");
    }
}
