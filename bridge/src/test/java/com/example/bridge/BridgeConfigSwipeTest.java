package com.example.bridge;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * These bounds are duplicated from ScriptActionExecutor.swipeScreen on purpose: a script authored
 * in the app is executed by the detached bridge, so the two clamps must agree exactly.
 */
public class BridgeConfigSwipeTest {

    private static Map<String, String> params(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test public void anAbsentDurationUsesTheAppsThreeHundredMillisecondDefault() {
        assertEquals(300L, BridgeConfig.swipeDurationMs(params()));
    }

    @Test public void durationIsClampedToTheAppsOneToFiveThousandRange() {
        assertEquals(1L, BridgeConfig.swipeDurationMs(params("duration", "0")));
        assertEquals(1L, BridgeConfig.swipeDurationMs(params("duration", "-5")));
        assertEquals(5000L, BridgeConfig.swipeDurationMs(params("duration", "99999")));
        assertEquals(750L, BridgeConfig.swipeDurationMs(params("duration", "750")));
    }

    @Test public void anUnparseableDurationFallsBackToTheDefaultRatherThanThrowing() {
        assertEquals(300L, BridgeConfig.swipeDurationMs(params("duration", "not-a-number")));
        assertEquals(300L, BridgeConfig.swipeDurationMs(params("duration", "")));
    }
}
