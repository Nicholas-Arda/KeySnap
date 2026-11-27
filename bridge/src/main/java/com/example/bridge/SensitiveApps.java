package com.example.bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings → Sensitive apps, decided without touching the phone: which of the two switches to turn
 * off, or back on, as an app comes to the front. {@link SensitiveAppGuard} runs the commands built
 * here and reports back what really happened. The two switches turned off and back on are
 * {@link #DEV_KEY} and {@link #A11Y_KEY}. The third, {@link #WIFI}, is one-way: a listed app with it
 * coming to the front ends this bridge ({@link #leavingFor}), and the guard's last act is
 * {@link #wirelessDebuggingOff}. adb_wifi_enabled is never written otherwise, never to 1, and
 * adb_enabled never at all. While adbd also listens on TCP ({@link #adbOverTcp}) turning wireless
 * debugging off does not end the bridge, so it writes the 0 and stays ({@link #stayed}).
 */
final class SensitiveApps {

    static final int DEV = 1;
    static final int A11Y = 2;
    /** Wireless debugging: turned off, never back on, and only as this bridge ends. Not in {@link #BITS}. */
    static final int WIFI = 4;
    static final int[] BITS = {DEV, A11Y};

    static final String DEV_KEY = "development_settings_enabled";
    static final String A11Y_KEY = "enabled_accessibility_services";
    static final String WIFI_KEY = "adb_wifi_enabled";

    /**
     * Screens that open over the app in front and hand back to it with no new launch: a permission
     * prompt, the share sheet, a system dialog. Stepping back for them would leave a bank that just
     * asked for the camera looking at the switches again, with nothing to turn them off once more.
     */
    static final Set<String> PASS_THROUGH = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller"));

    /** The app the switches are off for; null when none. */
    private String owner;
    /** Switches checked off for [owner]: turned off by us, or found off already. */
    private int covered;
    /** Switches we turned off: the only ones ever turned back on. */
    private int changed;
    /** "Restore now" was tapped while this app was in front: leave it alone until another one is. */
    private String suppressed;
    /** The listed app with {@link #WIFI} that came to the front; cleared only by {@link #stayed}, otherwise the bridge ends after that step. */
    private String leaving;

    /** {hide, restore} for [pkg] coming to the front; [apps] maps a package to its DEV|A11Y|WIFI bits. */
    int[] onForeground(String pkg, Map<String, Integer> apps) {
        if (pkg == null || pkg.isEmpty() || PASS_THROUGH.contains(pkg)) return new int[] {0, 0};
        if (!pkg.equals(suppressed)) suppressed = null;
        Integer wanted = suppressed == null ? apps.get(pkg) : null;
        int bits = wanted == null ? 0 : wanted;
        if ((bits & WIFI) != 0) leaving = pkg;
        return toward(bits & (DEV | A11Y), pkg);
    }

    /**
     * Non-null once a step brought a listed app with {@link #WIFI} to the front: that step's other
     * switches are settled as usual, then the bridge tells the app, turns wireless debugging off and
     * ends, putting nothing back. Only the rules above set it (pass-through screens, "Restore now",
     * the opener of the task in front); KeySnap's own entry never carries the bit ({@link BridgeConfig}).
     */
    String leavingFor() {
        return leaving;
    }

    /** Wireless debugging went off for [leavingFor] and the bridge carries on (adbd over TCP): back to normal steps. */
    void stayed() {
        leaving = null;
    }

    /**
     * True when `getprop service.adb.tcp.port` names a port: adbd then also listens on TCP, and
     * turning wireless debugging off leaves it, and this bridge, running (seen on ColorOS 17).
     */
    static boolean adbOverTcp(String getprop) {
        try {
            return getprop != null && Integer.parseInt(getprop.trim()) > 0;
        } catch (NumberFormatException notAPort) {
            return false;
        }
    }

    /**
     * The activity controller, before [pkg] launches. While the app in front is also read
     * ([frontTracked], {@link #onFront}), only a listed app acts here, so its switches are off before
     * it starts; leaving one waits until the reading shows another app on top, so a launch the system
     * then blocks, or a screen opened inside the listed app's task, puts nothing back.
     */
    int[] onLaunch(String pkg, Map<String, Integer> apps, boolean frontTracked) {
        return frontTracked && !apps.containsKey(pkg) ? new int[] {0, 0} : onForeground(pkg, apps);
    }

    /**
     * A reading of the task in front: its [top] activity's package and the package that opened the
     * task ([base]); null [top] = unreadable or nothing on screen, nothing changes. Another app's
     * screen inside a listed app's own task (a sign-in, a browser tab, a dialog it opened) keeps that
     * listed app in front.
     */
    int[] onFront(String top, String base, Map<String, Integer> apps) {
        boolean insideListed = top != null && !apps.containsKey(top) && base != null && apps.containsKey(base);
        return onForeground(insideListed ? base : top, apps);
    }

    /** A new config can only narrow what is off: the owner left the list, lost a switch, or the master went off. */
    int[] onConfig(Map<String, Integer> apps) {
        Integer wanted = owner == null ? null : apps.get(owner);
        return new int[] {0, toward(wanted == null ? 0 : wanted & covered, owner)[1]};
    }

    /** The notification's "Restore now": everything back, and the app in front stays as it is. */
    int[] onRestoreNow() {
        suppressed = owner;
        return toward(0, null);
    }

    private int[] toward(int target, String pkg) {
        int hide = target & ~covered;
        int restore = changed & ~target;
        covered &= target;
        owner = target == 0 ? null : pkg;
        return new int[] {hide, restore};
    }

    /**
     * The writes that carry out [step], one slot per bit of {@link #BITS} (null: nothing to write),
     * from each touched switch's value as just read ([current], same order; null = unreadable, left
     * for the next step). They are independent, so the guard runs them side by side. A switch already
     * as wanted is settled here; a write settles through {@link #hidden} / {@link #restored} once it
     * succeeds. [removed] = KeySnap's own accessibility entries, as noted at the last hide.
     */
    String[][] writes(int[] step, String[] current, String packageName, String removed) {
        String[][] writes = new String[BITS.length][];
        for (int i = 0; i < BITS.length; i++) {
            int bit = BITS[i];
            if (current[i] == null) continue;
            if ((step[1] & bit) != 0) {
                writes[i] = restoreCommand(bit, current[i], removed);
                if (writes[i] == null) restored(bit);
            } else if ((step[0] & bit) != 0 && (bit != A11Y || isPackageName(packageName))) {
                writes[i] = hideCommand(bit, current[i], packageName);
                if (writes[i] == null) hidden(bit, false);
            }
        }
        return writes;
    }

    static boolean isPackageName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+");
    }

    /** [bit] is off now; [byUs] when this bridge turned it off rather than finding it off. */
    void hidden(int bit, boolean byUs) {
        covered |= bit;
        if (byUs) changed |= bit;
    }

    void restored(int bit) {
        changed &= ~bit;
    }

    int changed() {
        return changed;
    }

    String owner() {
        return owner;
    }

    /** Nothing off and nothing remembered: a launch of an unlisted app needs no work at all. */
    boolean idle() {
        return covered == 0 && changed == 0 && suppressed == null;
    }

    // ---- commands ----

    static String namespace(int bit) {
        return bit == DEV ? "global" : "secure";
    }

    static String key(int bit) {
        return bit == DEV ? DEV_KEY : A11Y_KEY;
    }

    static String[] read(int bit) {
        return new String[] {"cmd", "settings", "get", namespace(bit), key(bit)};
    }

    /** The bridge's last write before it ends ({@link #leavingFor}); the only adb setting ever named, and only to 0. */
    static String[] wirelessDebuggingOff() {
        return new String[] {"cmd", "settings", "put", "global", WIFI_KEY, "0"};
    }

    /**
     * A `settings get` answer made safe to act on: "" for unset, null for anything that is not a
     * plain value of that setting (an error message must never be written back as a list).
     */
    static String parse(int bit, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || "null".equals(value)) return "";
        String allowed = bit == DEV ? "-?[0-9]+" : "[A-Za-z0-9_.$/:]+";
        return value.matches(allowed) ? value : null;
    }

    /** The write that turns [bit] off, or null when it is off already. [current] comes from {@link #parse}. */
    static String[] hideCommand(int bit, String current, String packageName) {
        if (bit == DEV) return "1".equals(current) ? put(bit, "0") : null;
        if (ownEntries(current, packageName).isEmpty()) return null;
        String kept = join(entriesOf(current, packageName, false));
        return kept.isEmpty() ? new String[] {"cmd", "settings", "delete", namespace(bit), key(bit)} : put(bit, kept);
    }

    /** The write that turns [bit] back on, or null when nothing needs writing. [removed] = KeySnap's own entries. */
    static String[] restoreCommand(int bit, String current, String removed) {
        if (bit == DEV) return "1".equals(current) ? null : put(bit, "1");
        List<String> merged = entriesOf(current, null, true);
        for (String entry : entriesOf(removed, null, true)) {
            if (!merged.contains(entry)) merged.add(entry);
        }
        String list = join(merged);
        return list.equals(join(entriesOf(current, null, true))) ? null : put(bit, list);
    }

    /** KeySnap's own components in an accessibility list, short or full form alike. */
    static String ownEntries(String list, String packageName) {
        return join(entriesOf(list, packageName, true));
    }

    private static String[] put(int bit, String value) {
        return new String[] {"cmd", "settings", "put", namespace(bit), key(bit), value};
    }

    /** Entries of a ':' list; with [packageName], only those of that package ([own]) or only the others. */
    private static List<String> entriesOf(String list, String packageName, boolean own) {
        List<String> entries = new ArrayList<>();
        if (list == null) return entries;
        for (String entry : list.split(":")) {
            if (entry.isEmpty()) continue;
            if (packageName == null || entry.startsWith(packageName + "/") == own) entries.add(entry);
        }
        return entries;
    }

    private static String join(List<String> entries) {
        return String.join(":", entries);
    }
}
