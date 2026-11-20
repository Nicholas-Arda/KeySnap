package com.example.bridge;

import java.util.Map;

/**
 * cycle_ringer_mode's cycle and its per-mode vibration, one implementation for both the app and
 * the bridge (the app compiles against :bridge), so neither side mirrors the other.
 *
 * <p>Params: "modes" = the modes in the cycle ("normal,vibrate,silent"; also the older single
 * words below); "buzz_normal" / "buzz_vibrate" / "buzz_silent" = that mode's pattern as
 * a {@link BuzzPattern}, blank = no buzz; "buzz_lead" = pause after the shortcut's own buzz.
 * A script saved before these existed has none of them and does not buzz.
 */
public final class RingerBuzz {

    public static final int SILENT = 0;
    public static final int VIBRATE = 1;
    public static final int NORMAL = 2;

    public static final long LEAD_MIN_MS = 0L;
    public static final long LEAD_MAX_MS = 600L;
    public static final long LEAD_DEFAULT_MS = 350L;
    public static final String LEAD_KEY = "buzz_lead";

    private static final int[] ORDER = {NORMAL, VIBRATE, SILENT};

    private RingerBuzz() { }

    public static String name(int mode) {
        return mode == NORMAL ? "normal" : mode == VIBRATE ? "vibrate" : "silent";
    }

    public static String key(int mode) {
        return "buzz_" + name(mode);
    }

    /** What a freshly added action starts with: 1, 2 and 3 buzzes. */
    public static String defaultPattern(int mode) {
        return BuzzPattern.evenly(mode == NORMAL ? 1 : mode == VIBRATE ? 2 : 3);
    }

    public static long lead(Map<String, String> params) {
        String raw = params == null ? null : params.get(LEAD_KEY);
        try {
            return raw == null ? LEAD_DEFAULT_MS : BuzzPattern.clamp(Long.parseLong(raw.trim()), LEAD_MIN_MS, LEAD_MAX_MS);
        } catch (NumberFormatException e) {
            return LEAD_DEFAULT_MS;
        }
    }

    /** Off/on waveform for [mode], starting now; empty when that mode does not buzz. */
    public static long[] waveform(Map<String, String> params, int mode) {
        return BuzzPattern.waveform(BuzzPattern.parse(params == null ? null : params.get(key(mode))));
    }

    /** True when any mode of this action buzzes. */
    public static boolean buzzes(Map<String, String> params) {
        for (int mode : ORDER) if (!BuzzPattern.parse(params == null ? null : params.get(key(mode))).isEmpty()) return true;
        return false;
    }

    /**
     * After the shortcut's own haptic, which takes [hapticDurationMs] in all: how long to wait before the chain so
     * that buzz ends and the lead pause passes before the mode buzz. Zero when nothing buzzes.
     */
    public static long triggerPause(String type, Map<String, String> params, long hapticDurationMs) {
        return "cycle_ringer_mode".equals(type) && buzzes(params) ? hapticDurationMs + lead(params) : 0L;
    }

    /** Whether [mode] is in the cycle; null/blank/"all" = every mode. */
    public static boolean inCycle(int mode, String modes) {
        if (modes == null || modes.trim().isEmpty() || "all".equals(modes)) return true;
        switch (modes) {
            case "normal_vibrate": return mode != SILENT;
            case "normal_silent": return mode != VIBRATE;
            case "vibrate_silent": return mode != NORMAL;
            default:
                for (String part : modes.split(",")) if (part.trim().equals(name(mode))) return true;
                return false;
        }
    }

    /** Normal → Vibrate → Silent → Normal over the modes in the cycle. */
    public static int next(int current, String modes) {
        int start = current == NORMAL ? 0 : current == VIBRATE ? 1 : 2;
        for (int step = 1; step <= 3; step++) {
            int mode = ORDER[(start + step) % 3];
            if (inCycle(mode, modes)) return mode;
        }
        return NORMAL;
    }
}
