package com.example.bridge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class AlarmIntentsTest {

    private static Map<String, String> params(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    @Test public void timerAddsMinutesAndSeconds() {
        assertEquals(Integer.valueOf(330), AlarmIntents.timerSeconds(params("minutes", "5", "seconds", "30")));
        assertEquals(Integer.valueOf(45), AlarmIntents.timerSeconds(params("seconds", "45")));
        assertEquals(Integer.valueOf(86_400), AlarmIntents.timerSeconds(params("minutes", "1440")));
    }

    @Test public void timerRefusesZeroAndJunk() {
        assertNull(AlarmIntents.timerSeconds(params()));
        assertNull(AlarmIntents.timerSeconds(params("minutes", "0", "seconds", "0")));
        assertNull(AlarmIntents.timerSeconds(params("minutes", "-1")));
        assertNull(AlarmIntents.timerSeconds(params("seconds", "60")));
        assertNull(AlarmIntents.timerSeconds(params("minutes", "1441")));
        assertNull(AlarmIntents.timerSeconds(params("minutes", "5; reboot")));
    }

    @Test public void alarmReadsAClockTime() {
        assertArrayEquals(new int[] {7, 5}, AlarmIntents.alarmTime(params("time", "07:05")));
        assertArrayEquals(new int[] {23, 59}, AlarmIntents.alarmTime(params("time", "23:59")));
        assertNull(AlarmIntents.alarmTime(params("time", "24:00")));
        assertNull(AlarmIntents.alarmTime(params("time", "7")));
        assertNull(AlarmIntents.alarmTime(params()));
    }
}
