package com.example.bridge;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * These bounds are duplicated from ScriptActionExecutor on purpose: a script authored in the app
 * is executed by the detached bridge, so the two clamps must agree exactly.
 */
public class BridgeConfigTapTest {

    private static Map<String, String> params(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test public void anAbsentCountTapsOnce() {
        assertEquals(1, BridgeConfig.tapCount(params()));
    }

    @Test public void anAbsentIntervalUsesTheAppsHundredMillisecondDefault() {
        assertEquals(100L, BridgeConfig.tapIntervalMs(params()));
    }

    @Test public void countIsClampedToTheAppsOneToTwentyRange() {
        assertEquals(1, BridgeConfig.tapCount(params("count", "0")));
        assertEquals(1, BridgeConfig.tapCount(params("count", "-5")));
        assertEquals(20, BridgeConfig.tapCount(params("count", "999")));
        assertEquals(3, BridgeConfig.tapCount(params("count", "3")));
    }

    @Test public void intervalIsClampedToTheAppsZeroToFiveSecondRange() {
        assertEquals(0L, BridgeConfig.tapIntervalMs(params("interval", "-1")));
        assertEquals(5000L, BridgeConfig.tapIntervalMs(params("interval", "60000")));
        assertEquals(250L, BridgeConfig.tapIntervalMs(params("interval", "250")));
    }

    @Test public void unparseableValuesFallBackToTheDefaultsRatherThanThrowing() {
        assertEquals(1, BridgeConfig.tapCount(params("count", "many")));
        assertEquals(100L, BridgeConfig.tapIntervalMs(params("interval", "")));
    }
}
