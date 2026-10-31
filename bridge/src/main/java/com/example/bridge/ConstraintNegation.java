package com.example.bridge;

import java.util.Map;

/** Shared negation contract. Unknown is never evidence for a negated predicate. */
public final class ConstraintNegation {
    public static final String PARAM = "negate";
    private ConstraintNegation() {}

    public static boolean requested(Map<String, String> params) {
        return "true".equals(params.get(PARAM));
    }

    /** mode_is intentionally does not support negation: ModeState.matches conflates unknown and false. */
    public static boolean supported(String type) { return !"mode_is".equals(type); }

    public static boolean apply(Boolean knownState) {
        return Boolean.FALSE.equals(knownState);
    }

    /** Read the positive state once, then account for the existing inverse type. */
    public static Boolean predicate(String type, Boolean positiveState) {
        return positiveState == null ? null : (type.equals(positiveType(type)) ? positiveState : !positiveState);
    }

    public static String positiveType(String type) {
        switch (type) {
            case "screen_off": return "screen_on";
            case "wifi_off": return "wifi_on";
            case "bluetooth_off": return "bluetooth_on";
            case "discharging": return "charging";
            case "not_in_phone_call": return "in_phone_call";
            case "flashlight_off": return "flashlight_on";
            case "app_not_in_foreground": return "app_in_foreground";
            case "no_media_playing": return "media_is_playing";
            case "bluetooth_device_disconnected": return "bluetooth_device_connected";
            case "dark_mode_off": return "dark_mode_on";
            case "disconnected_from_wifi_network": return "connected_to_wifi_network";
            case "keyboard_not_showing": return "keyboard_showing";
            case "device_unlocked": return "device_locked";
            case "lock_screen_not_showing": return "lock_screen_showing";
            case "battery_saver_off": return "battery_saver_on";
            case "location_off": return "location_on";
            case "mobile_data_off": return "mobile_data_on";
            case "airplane_mode_off": return "airplane_mode_on";
            case "dnd_off": return "dnd_on";
            case "headphones_disconnected": return "headphones_connected";
            default: return type;
        }
    }

    /** Per-script gate; cooldown stays neutral in the evaluator and never joins an automation edge. */
    public static boolean cooldownAllows(Long lastRunMs, Map<String, String> params, long nowMs) {
        if (!requested(params)) return ConstraintEvaluator.cooldownAllows(lastRunMs, params.get("minutes"), nowMs);
        try {
            Long.parseLong(params.get("minutes").trim());
        } catch (RuntimeException e) {
            return false;
        }
        if (lastRunMs != null && nowMs < lastRunMs) return false;
        return !ConstraintEvaluator.cooldownAllows(lastRunMs, params.get("minutes"), nowMs);
    }
}
