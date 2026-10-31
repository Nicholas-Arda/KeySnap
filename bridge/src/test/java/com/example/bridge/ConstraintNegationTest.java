package com.example.bridge;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Known true/false and unreadable fixtures for every bridge device constraint, including inverse ids. */
@RunWith(Parameterized.class)
public class ConstraintNegationTest {
    @Parameterized.Parameters(name = "{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][] {
            {"flashlight_on", "false", "true", Map.of()},
            {"flashlight_off", "true", "false", Map.of()},
            {"screen_on", "mWakefulness=Asleep", "mWakefulness=Awake", Map.of()},
            {"screen_off", "mWakefulness=Awake", "mWakefulness=Asleep", Map.of()},
            {"wifi_on", "0", "1", Map.of()},
            {"wifi_off", "1", "0", Map.of()},
            {"bluetooth_on", "0", "1", Map.of()},
            {"bluetooth_off", "1", "0", Map.of()},
            {"charging", "status: 3", "status: 2", Map.of()},
            {"discharging", "status: 2", "status: 3", Map.of()},
            {"in_phone_call", "mMode=0", "mMode=2", Map.of()},
            {"not_in_phone_call", "mMode=2", "mMode=0", Map.of()},
            {"app_in_foreground", "topResumedActivity=ActivityRecord{x u0 other.app/.Main}", "topResumedActivity=ActivityRecord{x u0 test.app/.Main}", Map.of("packageName", "test.app")},
            {"app_not_in_foreground", "topResumedActivity=ActivityRecord{x u0 test.app/.Main}", "topResumedActivity=ActivityRecord{x u0 other.app/.Main}", Map.of("packageName", "test.app")},
            {"media_is_playing", "MEDIA SESSION SERVICE\nSessions Stack - have 0 sessions", "PlaybackState {state=3,", Map.of()},
            {"no_media_playing", "PlaybackState {state=3,", "MEDIA SESSION SERVICE\nSessions Stack - have 0 sessions", Map.of()},
            {"bluetooth_device_connected", "AA:BB:CC:DD:EE:FF => [ACL BR/EDR:N LE:N] Headset", "AA:BB:CC:DD:EE:FF => [ACL BR/EDR:Y LE:N] Headset", Map.of("address", "AA:BB:CC:DD:EE:FF", "name", "Headset")},
            {"bluetooth_device_disconnected", "AA:BB:CC:DD:EE:FF => [ACL BR/EDR:Y LE:N] Headset", "AA:BB:CC:DD:EE:FF => [ACL BR/EDR:N LE:N] Headset", Map.of("address", "AA:BB:CC:DD:EE:FF", "name", "Headset")},
            {"dark_mode_on", "Night mode: no", "Night mode: yes", Map.of()},
            {"dark_mode_off", "Night mode: yes", "Night mode: no", Map.of()},
            {"connected_to_wifi_network", "Wifi is not connected", "Wifi is connected to \"Home\"", Map.of("ssid", "Home")},
            {"disconnected_from_wifi_network", "Wifi is connected to \"Home\"", "Wifi is not connected", Map.of("ssid", "Home")},
            {"keyboard_showing", "mInputShown=false", "mInputShown=true", Map.of()},
            {"keyboard_not_showing", "mInputShown=true", "mInputShown=false", Map.of()},
            {"device_locked", "(current) deviceLocked=0", "(current) deviceLocked=1", Map.of()},
            {"device_unlocked", "(current) deviceLocked=1", "(current) deviceLocked=0", Map.of()},
            {"lock_screen_showing", "KeyguardServiceDelegate\n showing=false", "KeyguardServiceDelegate\n showing=true", Map.of()},
            {"lock_screen_not_showing", "KeyguardServiceDelegate\n showing=true", "KeyguardServiceDelegate\n showing=false", Map.of()},
            {"battery_saver_on", "0", "1", Map.of()},
            {"battery_saver_off", "1", "0", Map.of()},
            {"location_on", "0", "3", Map.of()},
            {"location_off", "3", "0", Map.of()},
            {"mobile_data_on", "0", "1", Map.of()},
            {"mobile_data_off", "1", "0", Map.of()},
            {"airplane_mode_on", "0", "1", Map.of()},
            {"airplane_mode_off", "1", "0", Map.of()},
            {"dnd_on", "0", "1", Map.of()},
            {"dnd_off", "1", "0", Map.of()},
            {"headphones_connected", "Connected devices:\n", "Connected devices:\n[DeviceInfo: type:0x4 (wired_headset)]", Map.of()},
            {"headphones_disconnected", "Connected devices:\n[DeviceInfo: type:0x4 (wired_headset)]", "Connected devices:\n", Map.of()},
            {"phone_ringing", "mMode=0", "mMode=1", Map.of()},
            {"screen_orientation", "mCurrentOrientation=1", "mCurrentOrientation=0", Map.of("orientation", "portrait")},
            {"no_app_open", "mMode=0\nKeyguardServiceDelegate\n showing=false\ntopResumedActivity=ActivityRecord{x u0 other.app/.Main}\nhome.app/.Home", "mMode=0\nKeyguardServiceDelegate\n showing=false\ntopResumedActivity=ActivityRecord{x u0 home.app/.Home}\nhome.app/.Home", Map.of()},
            {"charging_wired", "AC powered: false\nUSB powered: false", "AC powered: true\nUSB powered: false", Map.of()},
            {"charging_wireless", "Wireless powered: false", "Wireless powered: true", Map.of()},
            {"battery_above", "level: 20", "level: 80", Map.of("percent", "50")},
            {"battery_below", "level: 80", "level: 20", Map.of("percent", "50")},
            {"ringer_normal", "1", "2", Map.of()},
            {"ringer_vibrate", "2", "1", Map.of()},
            {"ringer_silent", "2", "0", Map.of()}
        });
    }
    private final String type, falseOutput, trueOutput;
    private final Map<String, String> params;
    public ConstraintNegationTest(String type, String falseOutput, String trueOutput, Map<String, String> params) {
        this.type = type; this.falseOutput = falseOutput; this.trueOutput = trueOutput; this.params = params;
    }
    private boolean evaluate(String output) {
        Map<String, String> p = new HashMap<>(params);
        p.put("negate", "true");
        return new ConstraintEvaluator(null, null, command -> output,
                () -> "true".equals(output) ? Boolean.TRUE : "false".equals(output) ? Boolean.FALSE : null).isSatisfied(new BridgeConfig.Constraint(type, p), true);
    }
    @Test public void knownFalseBecomesTrue() { assertTrue(evaluate(falseOutput)); }
    @Test public void knownTrueBecomesFalse() { assertFalse(evaluate(trueOutput)); }
    @Test public void unreadableNeverBecomesTrue() {
        assertFalse(evaluate(""));
        assertFalse(evaluate("Permission Denial: cannot read state"));
    }
}
