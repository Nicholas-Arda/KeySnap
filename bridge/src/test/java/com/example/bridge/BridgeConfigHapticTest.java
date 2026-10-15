package com.example.bridge;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;

/**
 * Mirrors the haptic case of app/src/test/java/com/example/data/ScriptTimingTest.kt case for case:
 * resolveVibrateForScript / resolveHapticDurationForScript must resolve the same as
 * BridgeConfig.shouldVibrate / hapticDurationFor.
 */
public class BridgeConfigHapticTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private BridgeConfig.Script script(JSONObject globals, Boolean vibrate, Long duration) throws Exception {
        JSONObject script = new JSONObject()
                .put("id", "h")
                .put("name", "h")
                .put("triggers", new JSONArray().put(new JSONObject()
                        .put("keyCodes", new JSONArray().put(24))
                        .put("pressType", BridgeConfig.PRESS_SINGLE)))
                .put("actions", new JSONArray().put(new JSONObject()
                        .put("type", "toggle_flashlight").put("params", new JSONObject())));
        if (vibrate != null) script.put("vibrateOnTrigger", vibrate.booleanValue());
        if (duration != null) script.put("hapticDurationMs", duration.longValue());
        globals.put("scripts", new JSONArray().put(script));
        File file = tempFolder.newFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(globals.toString());
        }
        return BridgeConfig.read(file).scripts.get(0);
    }

    private File lastFile(JSONObject root) throws Exception {
        File file = tempFolder.newFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(root.toString());
        }
        return file;
    }

    private boolean vibrate(Boolean override, boolean global) throws Exception {
        JSONObject root = new JSONObject().put("vibrateOnTrigger", global);
        BridgeConfig.Script s = script(root, override, null);
        return BridgeConfig.read(lastFile(root)).shouldVibrate(s);
    }

    private long duration(Long override, long global) throws Exception {
        JSONObject root = new JSONObject().put("hapticDurationMs", global);
        BridgeConfig.Script s = script(root, null, override);
        return BridgeConfig.read(lastFile(root)).hapticDurationFor(s);
    }

    @Test
    public void hapticResolutionCases() throws Exception {
        assertEquals(true, vibrate(null, true));
        assertEquals(false, vibrate(null, false));
        assertEquals(false, vibrate(false, true));
        assertEquals(true, vibrate(true, false));

        assertEquals(60L, duration(null, 60L));
        assertEquals(100L, duration(100L, 60L));
        assertEquals(20L, duration(null, 5L));
        assertEquals(200L, duration(999L, 60L));
        assertEquals(60L, duration(null, 64L));
        assertEquals(70L, duration(null, 65L));
        assertEquals(30L, duration(25L, 60L));
    }

    @Test
    public void oldConfigWithoutHapticFieldsUsesAppDefaults() throws Exception {
        JSONObject root = new JSONObject();
        BridgeConfig.Script s = script(root, null, null);
        BridgeConfig config = BridgeConfig.read(lastFile(root));
        assertEquals(true, config.shouldVibrate(s));
        assertEquals(60L, config.hapticDurationFor(s));
    }
}
