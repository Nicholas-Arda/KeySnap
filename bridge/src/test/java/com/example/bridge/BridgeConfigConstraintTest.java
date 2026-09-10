package com.example.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * Mirrors app/src/test/java/com/example/data/ScriptRepositoryTest.kt's constraint round-trip
 * cases: BridgeConfig parses the same "constraints" JSON shape BridgePayload.writeConfig writes.
 */
public class BridgeConfigConstraintTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private JSONObject constraint(String type) throws Exception {
        return new JSONObject().put("type", type).put("params", new JSONObject());
    }

    private JSONObject script(String id, JSONArray constraints) throws Exception {
        JSONObject trigger = new JSONObject()
                .put("keyCodes", new JSONArray().put(24))
                .put("pressType", BridgeConfig.PRESS_SINGLE);
        JSONObject action = new JSONObject().put("type", "toggle_flashlight").put("params", new JSONObject());
        JSONObject obj = new JSONObject()
                .put("id", id)
                .put("name", id)
                .put("enabled", true)
                .put("triggers", new JSONArray().put(trigger))
                .put("actions", new JSONArray().put(action));
        if (constraints != null) {
            obj.put("constraints", constraints);
        }
        return obj;
    }

    private BridgeConfig read(JSONObject... scripts) throws Exception {
        JSONObject root = new JSONObject()
                .put("port", 38200)
                .put("token", "test-token")
                .put("packageName", "com.example")
                .put("globallyEnabled", true)
                .put("doublePressWindowMs", 300L)
                .put("longPressThresholdMs", 500L);
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
    public void scriptWithNoConstraintsKeyParsesWithAnEmptyList() throws Exception {
        BridgeConfig config = read(script("a", null));
        assertTrue(config.scripts.get(0).constraints.isEmpty());
    }

    @Test
    public void constraintsArrayParsesTypesInOrder() throws Exception {
        JSONArray constraints = new JSONArray().put(constraint(BridgeConfig.CONSTRAINT_WIFI_ON)).put(constraint(BridgeConfig.CONSTRAINT_CHARGING));
        BridgeConfig config = read(script("a", constraints));
        List<BridgeConfig.Constraint> parsed = config.scripts.get(0).constraints;
        assertEquals(2, parsed.size());
        assertEquals(BridgeConfig.CONSTRAINT_WIFI_ON, parsed.get(0).type);
        assertEquals(BridgeConfig.CONSTRAINT_CHARGING, parsed.get(1).type);
    }

    @Test
    public void aConstraintWithNoTypeIsSkipped() throws Exception {
        JSONArray constraints = new JSONArray().put(new JSONObject()).put(constraint(BridgeConfig.CONSTRAINT_SCREEN_ON));
        BridgeConfig config = read(script("a", constraints));
        List<BridgeConfig.Constraint> parsed = config.scripts.get(0).constraints;
        assertEquals(1, parsed.size());
        assertEquals(BridgeConfig.CONSTRAINT_SCREEN_ON, parsed.get(0).type);
    }

    @Test
    public void constraintParamsRoundTripAsStrings() throws Exception {
        JSONObject c = new JSONObject()
                .put("type", BridgeConfig.CONSTRAINT_BATTERY_ABOVE)
                .put("params", new JSONObject().put("percent", "20"));
        BridgeConfig config = read(script("a", new JSONArray().put(c)));
        BridgeConfig.Constraint parsed = config.scripts.get(0).constraints.get(0);
        assertEquals(BridgeConfig.CONSTRAINT_BATTERY_ABOVE, parsed.type);
        assertEquals("20", parsed.params.get("percent"));
    }
}
