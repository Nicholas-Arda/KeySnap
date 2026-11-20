package com.example.bridge;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class RingerBuzzTest {
    @Test public void cycleTakesOnlyTheChosenModesAndTheOldWords() {
        assertEquals(RingerBuzz.NORMAL, RingerBuzz.next(RingerBuzz.VIBRATE, "normal,vibrate"));
        assertEquals(RingerBuzz.SILENT, RingerBuzz.next(RingerBuzz.NORMAL, "normal,silent"));
        assertEquals(RingerBuzz.VIBRATE, RingerBuzz.next(RingerBuzz.NORMAL, "vibrate,silent"));
        assertEquals(RingerBuzz.VIBRATE, RingerBuzz.next(RingerBuzz.NORMAL, null));
        assertEquals(RingerBuzz.NORMAL, RingerBuzz.next(RingerBuzz.SILENT, "normal_vibrate"));
    }

    @Test public void eachBuzzKeepsItsOwnLengthAndClamps() {
        assertEquals(Arrays.asList(60L, 150L, 120L, 150L, 60L), BuzzPattern.parse("60,150,120,150,60"));
        assertEquals(Arrays.asList(20L, 600L, 300L), BuzzPattern.parse("1,9999,9999,"));
        assertTrue(BuzzPattern.parse("").isEmpty());
        assertEquals(9, BuzzPattern.parse("60,1,60,1,60,1,60,1,60,1,60,1,60").size());
    }

    @Test public void waveformStartsNowAndZeroBuzzesMeansSilence() {
        Map<String, String> params = new HashMap<>();
        params.put("buzz_silent", "60,150,120");
        params.put("buzz_normal", "");
        assertArrayEquals(new long[]{0, 60, 150, 120}, RingerBuzz.waveform(params, RingerBuzz.SILENT));
        assertEquals(0, RingerBuzz.waveform(params, RingerBuzz.NORMAL).length);
        assertEquals(50 + RingerBuzz.LEAD_DEFAULT_MS, RingerBuzz.triggerPause("cycle_ringer_mode", params, 50));
        assertEquals(0, RingerBuzz.triggerPause("cycle_ringer_mode", new HashMap<>(), 50));
    }

    @Test public void freshActionStartsWithOneTwoThree() {
        assertEquals(1, BuzzPattern.parse(RingerBuzz.defaultPattern(RingerBuzz.NORMAL)).size());
        assertEquals(5, BuzzPattern.parse(RingerBuzz.defaultPattern(RingerBuzz.SILENT)).size());
    }
}
