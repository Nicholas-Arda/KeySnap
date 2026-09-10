package com.example.bridge;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;

import java.io.File;
import java.io.FileWriter;

/**
 * Mirrors app/src/test/java/com/example/data/ScriptTimingTest.kt case for case. The Kotlin
 * {@code resolveTimingForKey} and this class's {@code doublePressWindowFor}/
 * {@code longPressThresholdFor} must resolve identical timings for the same config, since a
 * script authored in the app is later matched by this detached process.
 */
public class BridgeConfigTimingTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final long DEFAULT_DOUBLE = 300L;
    private static final long DEFAULT_LONG = 500L;

    private JSONObject trigger(int keyCode, String pressType, Long doubleOverride, Long longOverride) throws Exception {
        JSONObject t = new JSONObject()
                .put("keyCodes", new JSONArray().put(keyCode))
                .put("pressType", pressType);
        if (doubleOverride != null) t.put("doublePressWindowMs", doubleOverride.longValue());
        if (longOverride != null) t.put("longPressThresholdMs", longOverride.longValue());
        return t;
    }

    private JSONObject script(String id, boolean enabled, JSONObject trigger) throws Exception {
        JSONObject action = new JSONObject().put("type", "toggle_flashlight").put("params", new JSONObject());
        return new JSONObject()
                .put("id", id)
                .put("name", id)
                .put("enabled", enabled)
                .put("triggers", new JSONArray().put(trigger))
                .put("actions", new JSONArray().put(action));
    }

    private BridgeConfig read(JSONObject... scripts) throws Exception {
        JSONObject root = new JSONObject()
                .put("port", 38200)
                .put("token", "test-token")
                .put("packageName", "com.example")
                .put("globallyEnabled", true)
                .put("doublePressWindowMs", DEFAULT_DOUBLE)
                .put("longPressThresholdMs", DEFAULT_LONG);
        JSONArray scriptArray = new JSONArray();
        for (JSONObject s : scripts) {
            scriptArray.put(s);
        }
        root.put("scripts", scriptArray);

        File file = tempFolder.newFile("bridge_config.json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(root.toString());
        }
        return BridgeConfig.read(file);
    }

    @Test
    public void noTriggersForKeyFallsBackToDefaults() throws Exception {
        BridgeConfig config = read();
        assertEquals(DEFAULT_DOUBLE, config.doublePressWindowFor(24));
        assertEquals(DEFAULT_LONG, config.longPressThresholdFor(24));
    }

    @Test
    public void overrideOnMatchingPressTypeWins() throws Exception {
        BridgeConfig config = read(script("a", true, trigger(24, BridgeConfig.PRESS_DOUBLE, 450L, null)));
        assertEquals(450L, config.doublePressWindowFor(24));
        assertEquals(DEFAULT_LONG, config.longPressThresholdFor(24));
    }

    @Test
    public void overrideOnNonMatchingTypeStillAppliesAsFallback() throws Exception {
        BridgeConfig config = read(script("a", true, trigger(24, BridgeConfig.PRESS_SINGLE, 600L, null)));
        assertEquals(600L, config.doublePressWindowFor(24));
    }

    @Test
    public void firstScriptInOrderWins() throws Exception {
        BridgeConfig config = read(
                script("first", true, trigger(24, BridgeConfig.PRESS_DOUBLE, 400L, null)),
                script("second", true, trigger(24, BridgeConfig.PRESS_DOUBLE, 700L, null)));
        assertEquals(400L, config.doublePressWindowFor(24));
    }

    @Test
    public void disabledScriptOverrideIsIgnored() throws Exception {
        BridgeConfig config = read(
                script("disabled", false, trigger(24, BridgeConfig.PRESS_DOUBLE, 700L, null)),
                script("active", true, trigger(24, BridgeConfig.PRESS_DOUBLE, 450L, null)));
        assertEquals(450L, config.doublePressWindowFor(24));
    }

    @Test
    public void resolvedValueIsNormalizedToTheNearestStep() throws Exception {
        BridgeConfig config = read(script("a", true, trigger(24, BridgeConfig.PRESS_DOUBLE, 733L, null)));
        assertEquals(750L, config.doublePressWindowFor(24));
    }

    @Test
    public void differentKeyIsUnaffected() throws Exception {
        BridgeConfig config = read(script("a", true, trigger(24, BridgeConfig.PRESS_DOUBLE, 450L, null)));
        assertEquals(DEFAULT_DOUBLE, config.doublePressWindowFor(25));
    }
}
