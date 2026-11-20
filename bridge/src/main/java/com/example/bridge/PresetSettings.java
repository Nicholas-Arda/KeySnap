package com.example.bridge;

import java.util.Arrays;
import java.util.List;

/**
 * Ready-made setting actions (Always-on display, Grayscale, Charge limit, Bedtime) over a list of
 * known setting keys. A brand's variant is used only where this phone already has its probe key,
 * so the same action works on every brand we know and fails cleanly on the rest. Add a key here
 * only after it was written from the bridge on a real phone and the screen followed; note where.
 */
public final class PresetSettings {

    public interface Store {
        /** {@code settings get}; "null" or "" when the phone has no such key. */
        String get(String namespace, String key);

        boolean put(String namespace, String key, String value);
    }

    public static final class Write {
        public final String namespace, key, on, off;

        Write(String namespace, String key, String on, String off) {
            this.namespace = namespace;
            this.key = key;
            this.on = on;
            this.off = off;
        }
    }

    /** Used when [probe] is null or present on the phone; the first write's key is the on/off state. */
    public static final class Variant {
        public final String probeNamespace, probeKey;
        public final List<Write> writes;

        Variant(String probeNamespace, String probeKey, Write... writes) {
            this.probeNamespace = probeNamespace;
            this.probeKey = probeKey;
            this.writes = Arrays.asList(writes);
        }
    }

    // ColorOS 17 CN01 (PLG110), 2026-11-16: the system followed with full_screen_aod. Else AOSP.
    public static final List<Variant> ALWAYS_ON_DISPLAY = Arrays.asList(
            new Variant("secure", "Setting_AodSwitchEnable", new Write("secure", "Setting_AodSwitchEnable", "1", "0")),
            new Variant(null, null, new Write("secure", "doze_always_on", "1", "0")));

    // AOSP colour correction in monochromacy mode (0); also works on ColorOS 17, 2026-11-16.
    public static final List<Variant> GRAYSCALE = Arrays.asList(
            new Variant(null, null,
                    new Write("secure", "accessibility_display_daltonizer_enabled", "1", "0"),
                    new Write("secure", "accessibility_display_daltonizer", "0", null)));

    // ColorOS 17 CN01 (PLG110), 2026-11-15: charging really stopped at the limit. Level is a param.
    public static final List<Variant> CHARGE_LIMIT = Arrays.asList(
            new Variant("system", "regular_charge_protection_switch_state",
                    new Write("system", "regular_charge_protection_switch_state", "1", "0")));
    public static final String CHARGE_LEVEL_KEY = "regular_charge_protection_open_power_level";

    // ColorOS Eye Care "Bedtime" warm colour (PLG110, 2026-11-16). Bedtime adds it where present.
    static final List<Variant> WARM_COLOR = Arrays.asList(
            new Variant("system", "oplus_customize_comfortable_rhythm_enable",
                    new Write("system", "oplus_customize_comfortable_rhythm_enable", "1", "0")));

    // AOSP Night Light and Extra dim: ColorDisplayService reported both "Activated: true" after the
    // write on ColorOS 17 CN01 (PLG110, 2026-11-18).
    public static final List<Variant> NIGHT_LIGHT = Arrays.asList(
            new Variant(null, null, new Write("secure", "night_display_activated", "1", "0")));
    public static final List<Variant> EXTRA_DIM = Arrays.asList(
            new Variant(null, null, new Write("secure", "reduce_bright_colors_activated", "1", "0")));

    /** Mirrors ScriptActionCatalog.SCREEN_TIMEOUTS (:app); anything else is refused. */
    static final List<String> SCREEN_TIMEOUTS = Arrays.asList("15000", "30000", "60000", "120000", "300000", "600000", "1800000");

    static final List<String> CHARGE_LEVELS = Arrays.asList("80", "85", "90", "95", "100");

    private PresetSettings() { }

    static Variant pick(List<Variant> variants, Store store) {
        for (Variant variant : variants) {
            if (variant.probeKey == null || present(store.get(variant.probeNamespace, variant.probeKey))) return variant;
        }
        return null;
    }

    /** Current state of [variants] on this phone, or null when none applies. The app reads it for "Undo when it ends". */
    public static Boolean isOn(List<Variant> variants, Store store) {
        Variant variant = pick(variants, store);
        if (variant == null) return null;
        Write first = variant.writes.get(0);
        return first.on.equals(String.valueOf(store.get(first.namespace, first.key)).trim());
    }

    /** [state] is "on", "off" or "toggle" (anything else = toggle). False when no variant applies. */
    static boolean apply(List<Variant> variants, String state, Store store) {
        Variant variant = pick(variants, store);
        if (variant == null) return false;
        boolean on = "on".equals(state) || (!"off".equals(state) && !Boolean.TRUE.equals(isOn(variants, store)));
        boolean ok = true;
        for (Write write : variant.writes) {
            String value = on ? write.on : write.off;
            if (value != null) ok &= store.put(write.namespace, write.key, value);
        }
        return ok;
    }

    /** Sets the level first so the switch turns on at the new limit. Off leaves the level alone. */
    static boolean applyChargeLimit(String state, String level, Store store) {
        Variant variant = pick(CHARGE_LIMIT, store);
        if (variant == null) return false;
        if (!"off".equals(state)) {
            if (!store.put("system", CHARGE_LEVEL_KEY, chargeStep(level))) return false;
        }
        return apply(CHARGE_LIMIT, "off".equals(state) ? "off" : "on", store);
    }

    /** The highest known step at or below [level]; 80 for anything unreadable or lower. */
    static String chargeStep(String level) {
        int value;
        try { value = Integer.parseInt(String.valueOf(level).trim()); } catch (NumberFormatException e) { return "80"; }
        String step = "80";
        for (String known : CHARGE_LEVELS) if (Integer.parseInt(known) <= value) step = known;
        return step;
    }

    /** A known screen timeout in ms, or null so the action fails instead of writing a guess. */
    static String screenTimeout(String value) {
        String trimmed = value == null ? "" : value.trim();
        return SCREEN_TIMEOUTS.contains(trimmed) ? trimmed : null;
    }

    /** `cmd uimode night` argument: "yes"/"no" for on/off; toggle flips [current], that command's own output. */
    static String darkModeArgument(String state, String current) {
        if ("on".equals(state)) return "yes";
        if ("off".equals(state)) return "no";
        return String.valueOf(current).contains("Night mode: yes") ? "no" : "yes";
    }

    private static boolean present(String value) {
        return value != null && !value.trim().isEmpty() && !"null".equals(value.trim());
    }
}
