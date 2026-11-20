package com.example.bridge;

import java.util.ArrayList;
import java.util.List;

/**
 * A buzz pattern as "pulse,gap,pulse,…" in ms: each buzz and each pause with its own length.
 * Used by a shortcut's own vibration and by cycle_ringer_mode's per-mode buzz, on both the app
 * and the bridge side (the app compiles against :bridge), so neither side mirrors the other.
 */
public final class BuzzPattern {

    public static final int MAX_BUZZES = 5;
    public static final long PULSE_MIN_MS = 20L;
    public static final long PULSE_MAX_MS = 300L;
    public static final long PULSE_DEFAULT_MS = 60L;
    public static final long GAP_MIN_MS = 60L;
    public static final long GAP_MAX_MS = 600L;
    public static final long GAP_DEFAULT_MS = 150L;

    private BuzzPattern() { }

    /** [count] buzzes of the default length with default pauses. */
    public static String evenly(int count) {
        List<Long> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i > 0) parts.add(GAP_DEFAULT_MS);
            parts.add(PULSE_DEFAULT_MS);
        }
        return join(parts);
    }

    /** "pulse,gap,pulse,…" → clamped values, at most [MAX_BUZZES] pulses. Blank or junk → none. */
    public static List<Long> parse(String pattern) {
        List<Long> out = new ArrayList<>();
        if (pattern == null) return out;
        for (String part : pattern.split(",")) {
            if (out.size() >= MAX_BUZZES * 2 - 1) break;
            long value;
            try { value = Long.parseLong(part.trim()); } catch (NumberFormatException e) { continue; }
            boolean pulse = out.size() % 2 == 0;
            out.add(clamp(value, pulse ? PULSE_MIN_MS : GAP_MIN_MS, pulse ? PULSE_MAX_MS : GAP_MAX_MS));
        }
        if (out.size() % 2 == 0 && !out.isEmpty()) out.remove(out.size() - 1); // A trailing gap means nothing.
        return out;
    }

    public static String join(List<Long> parts) {
        StringBuilder sb = new StringBuilder();
        for (Long part : parts) {
            if (sb.length() > 0) sb.append(',');
            sb.append(part);
        }
        return sb.toString();
    }

    /** Off/on waveform starting now: [0, pulse, gap, pulse, …]; empty for no pulses. */
    public static long[] waveform(List<Long> parts) {
        if (parts.isEmpty()) return new long[0];
        long[] timings = new long[parts.size() + 1];
        for (int i = 0; i < parts.size(); i++) timings[i + 1] = parts.get(i);
        return timings;
    }

    /** How long the pattern takes, buzzes and pauses together. */
    public static long total(List<Long> parts) {
        long sum = 0L;
        for (Long part : parts) sum += part;
        return sum;
    }

    static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
