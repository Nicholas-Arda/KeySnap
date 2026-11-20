package com.example.bridge;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class PresetSettingsTest {
    private static final class FakeStore implements PresetSettings.Store {
        final Map<String, String> values = new HashMap<>();
        FakeStore with(String ns, String key, String value) { values.put(ns + "/" + key, value); return this; }
        public String get(String ns, String key) { return values.getOrDefault(ns + "/" + key, "null"); }
        public boolean put(String ns, String key, String value) { values.put(ns + "/" + key, value); return true; }
    }

    @Test public void aodUsesTheColorOsKeyWherePresentElseAosp() {
        FakeStore oppo = new FakeStore().with("secure", "Setting_AodSwitchEnable", "1");
        assertTrue(PresetSettings.apply(PresetSettings.ALWAYS_ON_DISPLAY, "toggle", oppo));
        assertEquals("0", oppo.get("secure", "Setting_AodSwitchEnable"));
        assertEquals("null", oppo.get("secure", "doze_always_on"));

        FakeStore pixel = new FakeStore();
        assertTrue(PresetSettings.apply(PresetSettings.ALWAYS_ON_DISPLAY, "on", pixel));
        assertEquals("1", pixel.get("secure", "doze_always_on"));
    }

    @Test public void screenTimeoutAcceptsOnlyKnownValues() {
        assertEquals("30000", PresetSettings.screenTimeout(" 30000 "));
        assertNull(PresetSettings.screenTimeout("1"));
        assertNull(PresetSettings.screenTimeout(null));
    }

    @Test public void darkModeToggleFlipsTheCurrentTheme() {
        assertEquals("yes", PresetSettings.darkModeArgument("on", ""));
        assertEquals("no", PresetSettings.darkModeArgument("off", ""));
        assertEquals("no", PresetSettings.darkModeArgument("toggle", "Night mode: yes"));
        assertEquals("yes", PresetSettings.darkModeArgument(null, "Night mode: auto"));
    }

    @Test public void grayscaleOffLeavesTheCorrectionModeAlone() {
        FakeStore store = new FakeStore();
        PresetSettings.apply(PresetSettings.GRAYSCALE, "on", store);
        assertEquals("1", store.get("secure", "accessibility_display_daltonizer_enabled"));
        assertEquals("0", store.get("secure", "accessibility_display_daltonizer"));
        store.with("secure", "accessibility_display_daltonizer", "11");
        PresetSettings.apply(PresetSettings.GRAYSCALE, "off", store);
        assertEquals("0", store.get("secure", "accessibility_display_daltonizer_enabled"));
        assertEquals("11", store.get("secure", "accessibility_display_daltonizer"));
    }

    @Test public void chargeLimitFailsWithoutAKnownKeyAndClampsTheLevel() {
        assertFalse(PresetSettings.applyChargeLimit("on", "80", new FakeStore()));
        FakeStore oppo = new FakeStore().with("system", "regular_charge_protection_switch_state", "0");
        assertTrue(PresetSettings.applyChargeLimit("on", "42", oppo));
        assertEquals("80", oppo.get("system", PresetSettings.CHARGE_LEVEL_KEY));
        assertEquals("1", oppo.get("system", "regular_charge_protection_switch_state"));
        assertTrue(PresetSettings.applyChargeLimit("off", "90", oppo));
        assertEquals("0", oppo.get("system", "regular_charge_protection_switch_state"));
        assertEquals("80", oppo.get("system", PresetSettings.CHARGE_LEVEL_KEY));
        assertEquals("85", PresetSettings.chargeStep("87"));
        assertEquals("100", PresetSettings.chargeStep("100"));
    }
}
