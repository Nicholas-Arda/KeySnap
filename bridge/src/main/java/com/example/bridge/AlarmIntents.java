package com.example.bridge;

import java.util.Map;

/**
 * set_timer / set_alarm parameters, shared by the bridge's `am start` and the app's AlarmClock
 * intent so both read them the same way. Out-of-range input is refused, never clamped into a
 * timer or alarm the user did not ask for.
 */
public final class AlarmIntents {

    private AlarmIntents() { }

    /** Timer length in seconds from "minutes" and "seconds" (blank = 0); null unless 1 s..24 h. */
    public static Integer timerSeconds(Map<String, String> params) {
        Integer minutes = number(params.get("minutes"));
        Integer seconds = number(params.get("seconds"));
        if (minutes == null || seconds == null || minutes > 1440 || seconds > 59) return null;
        int total = minutes * 60 + seconds;
        return total >= 1 && total <= 86_400 ? total : null;
    }

    /** {hour, minute} from "time" as HH:MM, or null when it is not a clock time. */
    public static int[] alarmTime(Map<String, String> params) {
        String time = params.get("time");
        if (time == null || !time.trim().matches("\\d{1,2}:\\d{2}")) return null;
        String[] parts = time.trim().split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return hour <= 23 && minute <= 59 ? new int[] {hour, minute} : null;
    }

    /** Blank counts as 0; anything but digits is refused. */
    private static Integer number(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        return value.trim().matches("\\d{1,5}") ? Integer.valueOf(value.trim()) : null;
    }
}
