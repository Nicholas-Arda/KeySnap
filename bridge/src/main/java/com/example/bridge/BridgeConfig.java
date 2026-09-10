package com.example.bridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bridge's view of the user's mapping configuration.
 *
 * <p>The app owns this file and rewrites it on every change; the bridge only reads it. Press
 * timings are carried in the file rather than duplicated as constants so the 300 ms / 500 ms
 * behaviour cannot drift between the in-app matcher and the detached process.
 */
public final class BridgeConfig {

    public static final String PRESS_SINGLE = "SINGLE_PRESS";
    public static final String PRESS_DOUBLE = "DOUBLE_PRESS";
    public static final String PRESS_LONG = "LONG_PRESS";

    public static final String ACTION_TOGGLE_FLASHLIGHT = "toggle_flashlight";
    public static final String ACTION_ENABLE_FLASHLIGHT = "enable_flashlight";
    public static final String ACTION_DISABLE_FLASHLIGHT = "disable_flashlight";
    public static final String ACTION_MEDIA_PLAY_PAUSE = "media_play_pause";
    public static final String ACTION_MEDIA_NEXT = "media_next";
    public static final String ACTION_MEDIA_PREVIOUS = "media_previous";
    public static final String ACTION_MEDIA_PLAY = "media_play";
    public static final String ACTION_MEDIA_PAUSE = "media_pause";
    public static final String ACTION_MEDIA_STOP = "media_stop";
    public static final String ACTION_MEDIA_FAST_FORWARD = "media_fast_forward";
    public static final String ACTION_MEDIA_REWIND = "media_rewind";
    public static final String ACTION_VOLUME_TOGGLE_MUTE = "volume_toggle_mute";
    public static final String ACTION_VOLUME_UP = "volume_up";
    public static final String ACTION_VOLUME_DOWN = "volume_down";
    public static final String ACTION_DELAY = "delay";
    public static final String ACTION_TOGGLE_WIFI = "toggle_wifi";
    public static final String ACTION_ENABLE_WIFI = "enable_wifi";
    public static final String ACTION_DISABLE_WIFI = "disable_wifi";
    public static final String ACTION_TOGGLE_MOBILE_DATA = "toggle_mobile_data";
    public static final String ACTION_TOGGLE_AIRPLANE_MODE = "toggle_airplane_mode";
    public static final String ACTION_TOGGLE_LOCATION = "toggle_location";
    public static final String ACTION_TOGGLE_BLUETOOTH = "toggle_bluetooth";
    public static final String ACTION_ENABLE_BLUETOOTH = "enable_bluetooth";
    public static final String ACTION_DISABLE_BLUETOOTH = "disable_bluetooth";
    public static final String ACTION_LOCK_DEVICE = "lock_device";
    public static final String ACTION_POWER_DIALOG = "power_dialog";
    public static final String ACTION_TOGGLE_DND = "toggle_dnd";
    public static final String ACTION_ENABLE_DND = "enable_dnd";
    public static final String ACTION_DISABLE_DND = "disable_dnd";
    public static final String ACTION_TEXT_CUT = "text_cut";
    public static final String ACTION_TEXT_COPY = "text_copy";
    public static final String ACTION_TEXT_PASTE = "text_paste";
    public static final String ACTION_SWITCH_KEYBOARD = "switch_keyboard";
    public static final String ACTION_SHOW_KEYBOARD_PICKER = "show_keyboard_picker";
    public static final String ACTION_LAUNCH_APP = "launch_app";
    public static final String ACTION_OPEN_URL = "open_url";
    public static final String ACTION_LAUNCH_VOICE_ASSISTANT = "launch_voice_assistant";
    public static final String ACTION_LAUNCH_DEVICE_ASSISTANT = "launch_device_assistant";
    public static final String ACTION_OPEN_CAMERA = "open_camera";
    public static final String ACTION_OPEN_SETTINGS = "open_settings";
    public static final String ACTION_FORCE_STOP_APP = "force_stop_app";
    public static final String ACTION_MODIFY_SETTING = "modify_setting";
    public static final String ACTION_SEND_INTENT = "send_intent";
    public static final String ACTION_HTTP_REQUEST = "http_request";
    public static final String ACTION_SHELL_COMMAND = "shell_command";
    public static final String ACTION_SCREEN_ON = "screen_on";
    public static final String ACTION_SCREEN_OFF = "screen_off";
    public static final String ACTION_TOGGLE_SCREEN = "toggle_screen";
    public static final String ACTION_TOGGLE_AUTO_ROTATE = "toggle_auto_rotate";
    public static final String ACTION_PORTRAIT_MODE = "portrait_mode";
    public static final String ACTION_LANDSCAPE_MODE = "landscape_mode";
    public static final String ACTION_CYCLE_ROTATIONS = "cycle_rotations";
    public static final String ACTION_CHANGE_BRIGHTNESS = "change_brightness";
    public static final String ACTION_TOGGLE_AUTO_BRIGHTNESS = "toggle_auto_brightness";
    public static final String ACTION_INCREASE_BRIGHTNESS = "increase_brightness";
    public static final String ACTION_DECREASE_BRIGHTNESS = "decrease_brightness";
    public static final String ACTION_VIBRATE = "vibrate";
    public static final String ACTION_TEXT_TO_SPEECH = "text_to_speech";
    public static final String ACTION_STOP_TEXT_TO_SPEECH = "stop_text_to_speech";
    public static final String ACTION_PLAY_SOUND = "play_sound";
    public static final String ACTION_REPEAT_PREVIOUS = "repeat_previous";
    public static final String ACTION_TOGGLE_MAPPING = "toggle_mapping";
    public static final String ACTION_PAUSE_MAPPING = "pause_mapping";
    public static final String ACTION_RESUME_MAPPING = "resume_mapping";
    public static final String ACTION_GO_BACK = "go_back";
    public static final String ACTION_GO_HOME = "go_home";
    public static final String ACTION_OPEN_RECENTS = "open_recents";
    public static final String ACTION_OPEN_MENU = "open_menu";
    public static final String ACTION_EXPAND_NOTIFICATION_DRAWER = "expand_notification_drawer";
    public static final String ACTION_EXPAND_QUICK_SETTINGS = "expand_quick_settings";
    public static final String ACTION_COLLAPSE_STATUS_BAR = "collapse_status_bar";
    public static final String ACTION_TAKE_SCREENSHOT = "take_screenshot";
    public static final String ACTION_TOGGLE_SPLIT_SCREEN = "toggle_split_screen";
    public static final String ACTION_MOVE_CURSOR_TO_END = "move_cursor_to_end";
    public static final String ACTION_SELECT_WORD_AT_CURSOR = "select_word_at_cursor";
    public static final String ACTION_VOLUME_MUTE = "volume_mute";
    public static final String ACTION_VOLUME_UNMUTE = "volume_unmute";
    public static final String ACTION_SHOW_VOLUME_DIALOG = "show_volume_dialog";
    public static final String ACTION_CHANGE_VOLUME_STREAM = "change_volume_stream";
    public static final String ACTION_CYCLE_RINGER_MODE = "cycle_ringer_mode";
    public static final String ACTION_CHANGE_RINGER_MODE = "change_ringer_mode";
    public static final String ACTION_INPUT_KEY_CODE = "input_key_code";
    public static final String ACTION_INPUT_KEY_EVENT = "input_key_event";
    public static final String ACTION_INPUT_TEXT = "input_text";
    public static final String ACTION_TAP_SCREEN = "tap_screen";
    public static final String ACTION_SWIPE_SCREEN = "swipe_screen";
    public static final String ACTION_PINCH_SCREEN = "pinch_screen";

    /** Mirrors ScriptActionExecutor's MAX_TAP_COUNT and DEFAULT_TAP_INTERVAL_MS exactly. */
    public static final int MAX_TAP_COUNT = 20;
    public static final long DEFAULT_TAP_INTERVAL_MS = 100L;
    public static final long MAX_TAP_INTERVAL_MS = 5000L;

    public static int tapCount(Map<String, String> params) {
        Long parsed = parseLongOrNull(params.get("count"));
        if (parsed == null) {
            return 1;
        }
        return (int) Math.max(1L, Math.min(MAX_TAP_COUNT, parsed));
    }

    public static long tapIntervalMs(Map<String, String> params) {
        Long parsed = parseLongOrNull(params.get("interval"));
        if (parsed == null) {
            return DEFAULT_TAP_INTERVAL_MS;
        }
        return Math.max(0L, Math.min(MAX_TAP_INTERVAL_MS, parsed));
    }

    /** Mirrors ScriptActionExecutor.swipeScreen's clamp and default exactly: 1..5000 ms, 300 ms
     *  when the "duration" param is absent or unparsable. */
    public static final long DEFAULT_SWIPE_DURATION_MS = 300L;
    public static final long MAX_SWIPE_DURATION_MS = 5000L;

    public static long swipeDurationMs(Map<String, String> params) {
        Long parsed = parseLongOrNull(params.get("duration"));
        if (parsed == null) {
            return DEFAULT_SWIPE_DURATION_MS;
        }
        return Math.max(1L, Math.min(MAX_SWIPE_DURATION_MS, parsed));
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static final String CONSTRAINT_SCREEN_ON = "screen_on";
    public static final String CONSTRAINT_SCREEN_OFF = "screen_off";
    public static final String CONSTRAINT_WIFI_ON = "wifi_on";
    public static final String CONSTRAINT_WIFI_OFF = "wifi_off";
    public static final String CONSTRAINT_BLUETOOTH_ON = "bluetooth_on";
    public static final String CONSTRAINT_BLUETOOTH_OFF = "bluetooth_off";
    public static final String CONSTRAINT_CHARGING = "charging";
    public static final String CONSTRAINT_DISCHARGING = "discharging";
    public static final String CONSTRAINT_IN_PHONE_CALL = "in_phone_call";
    public static final String CONSTRAINT_NOT_IN_PHONE_CALL = "not_in_phone_call";
    public static final String CONSTRAINT_FLASHLIGHT_ON = "flashlight_on";
    public static final String CONSTRAINT_FLASHLIGHT_OFF = "flashlight_off";
    public static final String CONSTRAINT_APP_IN_FOREGROUND = "app_in_foreground";
    public static final String CONSTRAINT_APP_NOT_IN_FOREGROUND = "app_not_in_foreground";
    public static final String CONSTRAINT_MEDIA_IS_PLAYING = "media_is_playing";
    public static final String CONSTRAINT_NO_MEDIA_PLAYING = "no_media_playing";
    public static final String CONSTRAINT_BLUETOOTH_DEVICE_CONNECTED = "bluetooth_device_connected";
    public static final String CONSTRAINT_BLUETOOTH_DEVICE_DISCONNECTED = "bluetooth_device_disconnected";
    public static final String CONSTRAINT_SCREEN_ORIENTATION = "screen_orientation";
    public static final String CONSTRAINT_DARK_MODE_ON = "dark_mode_on";
    public static final String CONSTRAINT_DARK_MODE_OFF = "dark_mode_off";
    public static final String CONSTRAINT_CONNECTED_TO_WIFI_NETWORK = "connected_to_wifi_network";
    public static final String CONSTRAINT_DISCONNECTED_FROM_WIFI_NETWORK = "disconnected_from_wifi_network";
    public static final String CONSTRAINT_KEYBOARD_SHOWING = "keyboard_showing";
    public static final String CONSTRAINT_KEYBOARD_NOT_SHOWING = "keyboard_not_showing";
    public static final String CONSTRAINT_DEVICE_LOCKED = "device_locked";
    public static final String CONSTRAINT_DEVICE_UNLOCKED = "device_unlocked";
    public static final String CONSTRAINT_LOCK_SCREEN_SHOWING = "lock_screen_showing";
    public static final String CONSTRAINT_LOCK_SCREEN_NOT_SHOWING = "lock_screen_not_showing";
    public static final String CONSTRAINT_PHONE_RINGING = "phone_ringing";
    public static final String CONSTRAINT_BATTERY_SAVER_ON = "battery_saver_on";
    public static final String CONSTRAINT_BATTERY_SAVER_OFF = "battery_saver_off";
    public static final String CONSTRAINT_CHARGING_WIRED = "charging_wired";
    public static final String CONSTRAINT_CHARGING_WIRELESS = "charging_wireless";
    public static final String CONSTRAINT_BATTERY_ABOVE = "battery_above";
    public static final String CONSTRAINT_BATTERY_BELOW = "battery_below";
    public static final String CONSTRAINT_LOCATION_ON = "location_on";
    public static final String CONSTRAINT_LOCATION_OFF = "location_off";
    public static final String CONSTRAINT_TIME = "time";
    public static final String CONSTRAINT_DAY_OF_WEEK = "day_of_week";
    public static final String CONSTRAINT_MOBILE_DATA_ON = "mobile_data_on";
    public static final String CONSTRAINT_MOBILE_DATA_OFF = "mobile_data_off";
    public static final String CONSTRAINT_AIRPLANE_MODE_ON = "airplane_mode_on";
    public static final String CONSTRAINT_AIRPLANE_MODE_OFF = "airplane_mode_off";
    public static final String CONSTRAINT_RINGER_NORMAL = "ringer_normal";
    public static final String CONSTRAINT_RINGER_VIBRATE = "ringer_vibrate";
    public static final String CONSTRAINT_RINGER_SILENT = "ringer_silent";
    public static final String CONSTRAINT_DND_ON = "dnd_on";
    public static final String CONSTRAINT_DND_OFF = "dnd_off";
    public static final String CONSTRAINT_HEADPHONES_CONNECTED = "headphones_connected";
    public static final String CONSTRAINT_HEADPHONES_DISCONNECTED = "headphones_disconnected";

    public final int port;
    public final String token;
    /** The app's package, used for the battery-optimisation whitelist. */
    public final String packageName;
    public final boolean globallyEnabled;
    public final long doublePressWindowMs;
    public final long longPressThresholdMs;
    public final List<Script> scripts;

    public static final class Trigger {
        public final List<Integer> keyCodes;
        public final String pressType;
        /** Null defers to the config-wide default; see {@link #doublePressWindowFor}. */
        public final Long doublePressWindowMs;
        public final Long longPressThresholdMs;

        Trigger(List<Integer> keyCodes, String pressType, Long doublePressWindowMs, Long longPressThresholdMs) {
            this.keyCodes = keyCodes;
            this.pressType = pressType;
            this.doublePressWindowMs = doublePressWindowMs;
            this.longPressThresholdMs = longPressThresholdMs;
        }

        boolean matchesSingleKey(int keyCode) {
            return keyCodes.size() == 1 && keyCodes.get(0) == keyCode;
        }
    }

    public static final class Action {
        public final String type;
        public final Map<String, String> params;

        Action(String type, Map<String, String> params) {
            this.type = type;
            this.params = params;
        }
    }

    public static final class Constraint {
        public final String type;
        public final Map<String, String> params;

        Constraint(String type, Map<String, String> params) {
            this.type = type;
            this.params = params;
        }
    }

    public static final class Script {
        public final String id;
        public final String name;
        public final boolean enabled;
        public final List<Trigger> triggers;
        public final List<Action> actions;
        public final List<Constraint> constraints;

        Script(String id, String name, boolean enabled, List<Trigger> triggers, List<Action> actions, List<Constraint> constraints) {
            this.id = id;
            this.name = name;
            this.enabled = enabled;
            this.triggers = triggers;
            this.actions = actions;
            this.constraints = constraints;
        }
    }

    private BridgeConfig(int port, String token, String packageName, boolean globallyEnabled,
                         long doublePressWindowMs, long longPressThresholdMs, List<Script> scripts) {
        this.port = port;
        this.token = token;
        this.packageName = packageName;
        this.globallyEnabled = globallyEnabled;
        this.doublePressWindowMs = doublePressWindowMs;
        this.longPressThresholdMs = longPressThresholdMs;
        this.scripts = scripts;
    }

    /** Every key code, across all enabled scripts, that the bridge should react to. */
    public List<Script> scriptsFor(int keyCode, String pressType) {
        List<Script> matched = new ArrayList<>();
        if (!globallyEnabled) {
            return matched;
        }
        for (Script script : scripts) {
            if (!script.enabled) {
                continue;
            }
            for (Trigger trigger : script.triggers) {
                if (trigger.matchesSingleKey(keyCode) && trigger.pressType.equals(pressType)) {
                    matched.add(script);
                    break;
                }
            }
        }
        return matched;
    }

    /** True when at least one enabled script listens to this key, regardless of press type. */
    public boolean isMappedKey(int keyCode) {
        if (!globallyEnabled) {
            return false;
        }
        for (Script script : scripts) {
            if (!script.enabled) {
                continue;
            }
            for (Trigger trigger : script.triggers) {
                if (trigger.matchesSingleKey(keyCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Mirrors the Kotlin {@code resolveTimingForKey} in {@code app/src/main/java/com/example/data/
     * ScriptTiming.kt} exactly: the first explicit override on a trigger of the matching press
     * type wins, then the first explicit override on any trigger for that key, then the config
     * default. The two must never diverge, since a script authored in the app is later matched by
     * this process.
     */
    // Mirrors app/src/main/java/com/example/data/KeyModels.kt's TriggerTimingSpecs exactly.
    private static final long DOUBLE_WINDOW_MIN = 150L, DOUBLE_WINDOW_MAX = 750L, DOUBLE_WINDOW_STEP = 50L;
    private static final long LONG_THRESHOLD_MIN = 300L, LONG_THRESHOLD_MAX = 1500L, LONG_THRESHOLD_STEP = 100L;

    public long doublePressWindowFor(int keyCode) {
        List<Trigger> triggersForKey = triggersForKeyLocked(keyCode);
        Long fromMatchingType = firstOverride(triggersForKey, PRESS_DOUBLE, true);
        Long resolved = fromMatchingType != null ? fromMatchingType : firstOverride(triggersForKey, null, true);
        long value = resolved != null ? resolved : doublePressWindowMs;
        return normalize(value, DOUBLE_WINDOW_MIN, DOUBLE_WINDOW_MAX, DOUBLE_WINDOW_STEP);
    }

    public long longPressThresholdFor(int keyCode) {
        List<Trigger> triggersForKey = triggersForKeyLocked(keyCode);
        Long fromMatchingType = firstOverride(triggersForKey, PRESS_LONG, false);
        Long resolved = fromMatchingType != null ? fromMatchingType : firstOverride(triggersForKey, null, false);
        long value = resolved != null ? resolved : longPressThresholdMs;
        return normalize(value, LONG_THRESHOLD_MIN, LONG_THRESHOLD_MAX, LONG_THRESHOLD_STEP);
    }

    /** Mirrors normalizeTimingValue in app/src/main/java/com/example/data/KeyModels.kt exactly. */
    private static long normalize(long value, long min, long max, long step) {
        long clamped = Math.max(min, Math.min(max, value));
        long snapped = min + ((clamped - min + step / 2) / step) * step;
        return Math.max(min, Math.min(max, snapped));
    }

    private List<Trigger> triggersForKeyLocked(int keyCode) {
        List<Trigger> result = new ArrayList<>();
        for (Script script : scripts) {
            if (!script.enabled) {
                continue;
            }
            for (Trigger trigger : script.triggers) {
                if (trigger.matchesSingleKey(keyCode)) {
                    result.add(trigger);
                }
            }
        }
        return result;
    }

    private static Long firstOverride(List<Trigger> triggers, String pressType, boolean wantDoubleWindow) {
        for (Trigger trigger : triggers) {
            if (pressType != null && !pressType.equals(trigger.pressType)) {
                continue;
            }
            Long value = wantDoubleWindow ? trigger.doublePressWindowMs : trigger.longPressThresholdMs;
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static BridgeConfig read(File file) throws Exception {
        String json = readFully(file);
        JSONObject root = new JSONObject(json);

        int port = root.optInt("port", 0);
        String token = root.optString("token", "");
        String packageName = root.optString("packageName", "");
        boolean globallyEnabled = root.optBoolean("globallyEnabled", true);
        long doubleWindow = root.optLong("doublePressWindowMs", 300L);
        long longThreshold = root.optLong("longPressThresholdMs", 500L);

        List<Script> scripts = new ArrayList<>();
        JSONArray scriptArray = root.optJSONArray("scripts");
        if (scriptArray != null) {
            for (int i = 0; i < scriptArray.length(); i++) {
                Script script = parseScript(scriptArray.optJSONObject(i));
                if (script != null) {
                    scripts.add(script);
                }
            }
        }
        return new BridgeConfig(port, token, packageName, globallyEnabled, doubleWindow, longThreshold,
                Collections.unmodifiableList(scripts));
    }

    /** Actions out of a bare {"actions":[...]} object, as the control port's RUN command sends. */
    public static List<Action> parseActions(String json) throws Exception {
        return parseActions(new JSONObject(json).optJSONArray("actions"));
    }

    private static List<Action> parseActions(JSONArray actionArray) {
        List<Action> actions = new ArrayList<>();
        if (actionArray == null) {
            return actions;
        }
        for (int i = 0; i < actionArray.length(); i++) {
            JSONObject a = actionArray.optJSONObject(i);
            if (a == null) {
                continue;
            }
            String type = a.optString("type", "");
            if (type.isEmpty()) {
                continue;
            }
            Map<String, String> params = new LinkedHashMap<>();
            JSONObject paramsObj = a.optJSONObject("params");
            if (paramsObj != null) {
                java.util.Iterator<String> keys = paramsObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    params.put(key, paramsObj.optString(key, ""));
                }
            }
            actions.add(new Action(type, params));
        }
        return actions;
    }

    private static Script parseScript(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        String id = obj.optString("id", "");
        if (id.isEmpty()) {
            return null;
        }
        List<Trigger> triggers = new ArrayList<>();
        JSONArray triggerArray = obj.optJSONArray("triggers");
        if (triggerArray != null) {
            for (int i = 0; i < triggerArray.length(); i++) {
                JSONObject t = triggerArray.optJSONObject(i);
                if (t == null) {
                    continue;
                }
                String pressType = t.optString("pressType", "");
                if (!PRESS_SINGLE.equals(pressType) && !PRESS_DOUBLE.equals(pressType)
                        && !PRESS_LONG.equals(pressType)) {
                    continue;
                }
                List<Integer> keyCodes = new ArrayList<>();
                JSONArray codes = t.optJSONArray("keyCodes");
                if (codes != null) {
                    for (int k = 0; k < codes.length(); k++) {
                        int code = codes.optInt(k, Integer.MIN_VALUE);
                        if (code != Integer.MIN_VALUE && !keyCodes.contains(code)) {
                            keyCodes.add(code);
                        }
                    }
                }
                if (!keyCodes.isEmpty()) {
                    Long doubleOverride = t.has("doublePressWindowMs") && !t.isNull("doublePressWindowMs")
                            ? t.optLong("doublePressWindowMs") : null;
                    Long longOverride = t.has("longPressThresholdMs") && !t.isNull("longPressThresholdMs")
                            ? t.optLong("longPressThresholdMs") : null;
                    triggers.add(new Trigger(keyCodes, pressType, doubleOverride, longOverride));
                }
            }
        }
        List<Action> actions = parseActions(obj.optJSONArray("actions"));
        List<Constraint> constraints = new ArrayList<>();
        JSONArray constraintArray = obj.optJSONArray("constraints");
        if (constraintArray != null) {
            for (int i = 0; i < constraintArray.length(); i++) {
                JSONObject c = constraintArray.optJSONObject(i);
                if (c == null) {
                    continue;
                }
                String type = c.optString("type", "");
                if (type.isEmpty()) {
                    continue;
                }
                Map<String, String> params = new LinkedHashMap<>();
                JSONObject paramsObj = c.optJSONObject("params");
                if (paramsObj != null) {
                    java.util.Iterator<String> keys = paramsObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        params.put(key, paramsObj.optString(key, ""));
                    }
                }
                constraints.add(new Constraint(type, params));
            }
        }
        if (triggers.isEmpty() || actions.isEmpty()) {
            return null;
        }
        return new Script(id, obj.optString("name", "Unnamed script"), obj.optBoolean("enabled", true),
                triggers, actions, Collections.unmodifiableList(constraints));
    }

    private static String readFully(File file) throws Exception {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), Charset.forName("UTF-8"));
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
                // Nothing useful to do while closing a read-only config stream.
            }
        }
    }
}
