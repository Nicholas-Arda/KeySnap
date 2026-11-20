package com.example.bridge;

import java.time.Duration;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the phone lies, from one accelerometer reading, for the "Physical orientation" condition.
 * The app (SensorManager) and the bridge (the sensor service's recent events) both call
 * {@link #classify}, so the two sides never disagree on where face up ends and portrait begins.
 */
public final class PhysicalOrientation {
    private PhysicalOrientation() {}

    public static final String FACE_UP = "face_up";
    public static final String FACE_DOWN = "face_down";
    public static final String PORTRAIT = "portrait";
    public static final String UPSIDE_DOWN = "upside_down";
    public static final String LANDSCAPE = "landscape";

    /** Below this no axis carries gravity (falling, shaking): not known. m/s². */
    private static final double MIN_GRAVITY = 6.0;
    /** The sensor service keeps a stopped sensor's last events; older than this is not "now". */
    private static final Duration MAX_AGE = Duration.ofSeconds(10);

    /** The axis carrying most of gravity decides; null when none clearly does. */
    public static String classify(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (Math.max(ax, Math.max(ay, az)) < MIN_GRAVITY) return null;
        if (az >= ax && az >= ay) return z > 0 ? FACE_UP : FACE_DOWN;
        if (ay >= ax) return y > 0 ? PORTRAIT : UPSIDE_DOWN;
        return LANDSCAPE;
    }

    /** True/false when [expected] is a known value and the reading is known, else null. */
    public static Boolean matches(String expected, String actual) {
        if (actual == null || expected == null) return null;
        String want = expected.trim();
        if (!want.matches(FACE_UP + "|" + FACE_DOWN + "|" + PORTRAIT + "|" + UPSIDE_DOWN + "|" + LANDSCAPE)) return null;
        return want.equals(actual);
    }

    private static final Pattern ACCEL_NAME = Pattern.compile(
            "(?m)^0x[0-9a-fA-F]+\\)\\s+(.+?)\\s*\\|[^\\n]*type: android\\.sensor\\.accelerometer\\(1\\)");
    private static final Pattern EVENT = Pattern.compile(
            "(?m)^\\s*\\d+ \\(ts=([\\d.]+), wall=(\\d\\d:\\d\\d:\\d\\d)[.\\d]*\\) (-?[\\d.]+), (-?[\\d.]+), (-?[\\d.]+)");

    /**
     * The newest event of the first accelerometer in {@code dumpsys sensorservice}, classified, or
     * null when there is none from the last few seconds (the sensor is idle on this phone).
     */
    static String fromSensorDump(String dump, LocalTime now) {
        if (dump == null) return null;
        Matcher name = ACCEL_NAME.matcher(dump);
        if (!name.find()) return null;
        int start = dump.indexOf("\n" + name.group(1) + ": last ");
        if (start < 0) return null;
        int end = dump.indexOf(": last ", start + name.group(1).length() + 8);
        Matcher event = EVENT.matcher(end < 0 ? dump.substring(start) : dump.substring(start, end));
        double newest = -1;
        String result = null;
        LocalTime wall = null;
        while (event.find()) {
            double ts = Double.parseDouble(event.group(1));
            if (ts <= newest) continue;
            newest = ts;
            wall = LocalTime.parse(event.group(2));
            result = classify(Double.parseDouble(event.group(3)), Double.parseDouble(event.group(4)), Double.parseDouble(event.group(5)));
        }
        if (wall == null) return null;
        long age = Math.floorMod(Duration.between(wall, now).getSeconds(), 86_400L);
        return Math.min(age, 86_400L - age) <= MAX_AGE.getSeconds() ? result : null;
    }
}
