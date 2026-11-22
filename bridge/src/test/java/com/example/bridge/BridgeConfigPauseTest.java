package com.example.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * While mapping is paused, only a shortcut that can resume it stays live (mirrored by the app's
 * ShortcutScript.isLive, which calls the same {@link BridgeConfig#resumesMapping}).
 */
public class BridgeConfigPauseTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private static JSONObject script(String id, boolean enabled, String action, Integer... keys) throws Exception {
        return new JSONObject()
                .put("id", id)
                .put("name", id)
                .put("enabled", enabled)
                .put("triggers", new JSONArray().put(new JSONObject()
                        .put("keyCodes", new JSONArray(Arrays.asList(keys)))
                        .put("pressType", BridgeConfig.PRESS_SINGLE)))
                .put("actions", new JSONArray()
                        .put(new JSONObject().put("type", action).put("params", new JSONObject()))
                        .put(new JSONObject().put("type", "toggle_flashlight").put("params", new JSONObject())));
    }

    private BridgeConfig read(boolean globallyEnabled) throws Exception {
        JSONObject root = new JSONObject()
                .put("port", 38200)
                .put("token", "test-token")
                .put("globallyEnabled", globallyEnabled)
                .put("scripts", new JSONArray()
                        .put(script("torch", true, "toggle_flashlight", 24))
                        .put(script("resume", true, BridgeConfig.ACTION_RESUME_MAPPING, 25))
                        .put(script("toggle", true, BridgeConfig.ACTION_TOGGLE_MAPPING, 26, 27))
                        .put(script("off", false, BridgeConfig.ACTION_RESUME_MAPPING, 28)));
        File file = tempFolder.newFile();
        Files.write(file.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
        return BridgeConfig.read(file);
    }

    private static List<String> ids(List<BridgeConfig.Script> scripts) {
        String[] ids = new String[scripts.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = scripts.get(i).id;
        return Arrays.asList(ids);
    }

    @Test
    public void pausedKeepsOnlyScriptsThatCanResume() throws Exception {
        BridgeConfig paused = read(false);
        assertEquals(Collections.emptyList(), ids(paused.scriptsFor(24, BridgeConfig.PRESS_SINGLE)));
        assertFalse(paused.isMappedKey(24));
        assertEquals(Collections.singletonList("resume"), ids(paused.scriptsFor(25, BridgeConfig.PRESS_SINGLE)));
        assertTrue(paused.isMappedKey(25));
        int chord = ChordTracker.chordId(Arrays.asList(26, 27));
        assertEquals(Collections.singletonList("toggle"), ids(paused.scriptsFor(chord, BridgeConfig.PRESS_SINGLE)));
        assertEquals(Collections.singletonList(Arrays.asList(26, 27)), paused.chords());
        // A paused script stays off even when it could resume.
        assertFalse(paused.isMappedKey(28));
    }

    @Test
    public void runningKeepsEveryEnabledScript() throws Exception {
        BridgeConfig running = read(true);
        assertEquals(Collections.singletonList("torch"), ids(running.scriptsFor(24, BridgeConfig.PRESS_SINGLE)));
        assertEquals(Collections.singletonList("resume"), ids(running.scriptsFor(25, BridgeConfig.PRESS_SINGLE)));
        assertTrue(running.isMappedKey(26));
        assertFalse(running.isMappedKey(28));
    }

    @Test
    public void onlyResumeAndToggleCanResume() {
        assertTrue(BridgeConfig.resumesMapping(BridgeConfig.ACTION_RESUME_MAPPING));
        assertTrue(BridgeConfig.resumesMapping(BridgeConfig.ACTION_TOGGLE_MAPPING));
        assertFalse(BridgeConfig.resumesMapping(BridgeConfig.ACTION_PAUSE_MAPPING));
        assertFalse(BridgeConfig.resumesMapping("toggle_flashlight"));
        assertFalse(BridgeConfig.resumesMapping(null));
    }
}
