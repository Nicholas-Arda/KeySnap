package com.example.bridge;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class SettingsTeachTest {
    private static SettingsTeach.Snapshot snap(String secure) {
        SettingsTeach.Snapshot s = new SettingsTeach.Snapshot();
        SettingsTeach.put(s, "secure", secure);
        SettingsTeach.put(s, "system", "screen_brightness=100\n");
        return s;
    }

    @Test public void keepsOnlyKeysThatWentBack() {
        SettingsTeach.Snapshot a = snap("aod=0\ncounter=5\nname=Phone\n");
        SettingsTeach.Snapshot b = snap("aod=1\ncounter=6\nname=Phone\n");
        SettingsTeach.Snapshot c = snap("aod=0\ncounter=7\nname=Phone\n");
        assertEquals(2, SettingsTeach.moved(a, b));
        SettingsTeach.Result r = SettingsTeach.result(a, b, c);
        assertEquals(SettingsTeach.OK, r.status);
        assertEquals(1, r.changes.size());
        assertEquals("aod", r.changes.get(0).key);
        assertEquals("1", r.changes.get(0).changed);
        assertEquals("0", r.changes.get(0).back);
    }

    @Test public void keyCreatedOnFirstChangeIsKept() {
        SettingsTeach.Result r = SettingsTeach.result(snap(""), snap("new_key=1\n"), snap("new_key=0\n"));
        assertEquals(SettingsTeach.OK, r.status);
        assertEquals("new_key", r.changes.get(0).key);
    }

    @Test public void notChangedBackYetIsNone() {
        assertEquals(SettingsTeach.NONE, SettingsTeach.result(snap("aod=0\n"), snap("aod=1\n"), snap("aod=1\n")).status);
    }

    @Test public void driftingAndLongValuesAreSkipped() {
        SettingsTeach.Snapshot a = new SettingsTeach.Snapshot(), b = new SettingsTeach.Snapshot();
        SettingsTeach.put(a, "system", "screen_brightness=10\nvolume_music=3\nlist=a,b\n");
        SettingsTeach.put(b, "system", "screen_brightness=90\nvolume_music=7\nlist=a,b,c\n");
        assertEquals(0, SettingsTeach.moved(a, b));
        assertEquals(SettingsTeach.NONE, SettingsTeach.result(a, b, a).status);
    }

    @Test public void moreThanThreeIsTooMany() {
        String off = "k1=0\nk2=0\nk3=0\nk4=0\n", on = "k1=1\nk2=1\nk3=1\nk4=1\n";
        assertEquals(SettingsTeach.TOO_MANY, SettingsTeach.result(snap(off), snap(on), snap(off)).status);
    }

    @Test public void debuggingKeysAreBlocked() {
        SettingsTeach.Snapshot a = new SettingsTeach.Snapshot(), b = new SettingsTeach.Snapshot();
        SettingsTeach.put(a, "global", "adb_wifi_enabled=1\n");
        SettingsTeach.put(b, "global", "adb_wifi_enabled=0\n");
        SettingsTeach.Result r = SettingsTeach.result(a, b, a);
        assertEquals(SettingsTeach.BLOCKED, r.status);
        assertTrue(r.changes.isEmpty());
    }

    @Test public void teachJsonCarriesTheChanges() throws Exception {
        String json = BridgeMain.teachJson(SettingsTeach.result(snap("aod=0\n"), snap("aod=1\n"), snap("aod=0\n")));
        org.json.JSONObject parsed = new org.json.JSONObject(json);
        assertEquals("ok", parsed.getString("status"));
        org.json.JSONObject change = parsed.getJSONArray("changes").getJSONObject(0);
        assertEquals("secure/aod 1 0", change.getString("ns") + "/" + change.getString("key") + " "
                + change.getString("changed") + " " + change.getString("back"));
    }

    @Test public void learnedWritesRoundTrip() {
        String writes = "secure/Setting_AodSwitchEnable=1|0;secure/full_screen_aod=on|off";
        List<LearnedSetting.Write> parsed = LearnedSetting.parse(writes);
        assertEquals(2, parsed.size());
        assertEquals(writes, LearnedSetting.encode(parsed));
        assertEquals(1, LearnedSetting.variants(writes).size());
    }

    @Test public void learnedWritesRejectBadInput() {
        assertNull(LearnedSetting.parse(""));
        assertNull(LearnedSetting.parse("secure/aod=1"));
        assertNull(LearnedSetting.parse("other/aod=1|0"));
        assertNull(LearnedSetting.parse("global/adb_enabled=1|0"));
        assertNull(LearnedSetting.parse("secure/aod=1|1"));
        assertNull(LearnedSetting.parse("secure/aod=a b|0"));
        assertNull(LearnedSetting.parse("secure/a=1|0;secure/b=1|0;secure/c=1|0;secure/d=1|0"));
    }

    @Test public void learnedSettingAppliesStateAndToggle() {
        Map<String, String> values = new HashMap<>();
        values.put("secure/aod", "0");
        PresetSettings.Store store = new PresetSettings.Store() {
            public String get(String ns, String key) { return values.getOrDefault(ns + "/" + key, "null"); }
            public boolean put(String ns, String key, String value) { values.put(ns + "/" + key, value); return true; }
        };
        List<PresetSettings.Variant> v = LearnedSetting.variants("secure/aod=1|0;system/extra=on|off");
        assertTrue(PresetSettings.apply(v, "toggle", store));
        assertEquals("1", values.get("secure/aod"));
        assertEquals("on", values.get("system/extra"));
        assertTrue(PresetSettings.apply(v, "off", store));
        assertEquals("0", values.get("secure/aod"));
        assertEquals("off", values.get("system/extra"));
    }
}
