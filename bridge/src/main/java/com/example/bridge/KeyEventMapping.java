package com.example.bridge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single source of truth for translating one `getevent -l` line into an app key code.
 *
 * <p>The app-side parser and the detached bridge must agree exactly: a key recorded while
 * Advanced Mode was active is persisted using the code produced here, and the bridge later
 * matches against that same stored code. Any divergence silently breaks existing mappings,
 * so both sides call into this class rather than keeping parallel implementations.
 *
 * <p>Kept free of android.* types so it can run inside app_process and in JVM unit tests.
 */
public final class KeyEventMapping {

    private KeyEventMapping() { }

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_REPEAT = 2;

    private static final int SYNTHETIC_KEY_BASE = 0x40000000;
    private static final int SYNTHETIC_KEY_MASK = 0x3fffffff;

    // Android KeyEvent constants, inlined so this class stays framework-free.
    private static final int KEYCODE_HOME = 3;
    private static final int KEYCODE_BACK = 4;
    private static final int KEYCODE_VOLUME_UP = 24;
    private static final int KEYCODE_VOLUME_DOWN = 25;
    private static final int KEYCODE_POWER = 26;
    private static final int KEYCODE_CAMERA = 27;
    private static final int KEYCODE_FOCUS = 80;
    private static final int KEYCODE_MENU = 82;
    private static final int KEYCODE_HEADSETHOOK = 79;
    private static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    private static final int KEYCODE_MEDIA_NEXT = 87;
    private static final int KEYCODE_MEDIA_PREVIOUS = 88;
    private static final int KEYCODE_MUTE = 91;
    private static final int KEYCODE_ASSIST = 219;
    private static final int KEYCODE_WAKEUP = 224;
    private static final int KEYCODE_APP_SWITCH = 284;

    private static final Pattern EVENT_PATTERN = Pattern.compile(
            "^\\s*(?:(?:\\[\\s*[^]]+]\\s*)|(?:\\(\\s*[^)]+\\)\\s*))?"
                    + "(?:(/dev/input/event\\d+)\\s*:\\s*)?"
                    + "(?:EV_KEY|0001)\\s+(\\S+)\\s+(\\S+)(?:\\s+.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Integer> LINUX_CODES_BY_NAME;

    static {
        Map<String, Integer> codes = new HashMap<>();
        codes.put("KEY_POWER", 116);
        codes.put("KEY_VOLUMEUP", 115);
        codes.put("KEY_VOLUMEDOWN", 114);
        codes.put("KEY_WAKEUP", 143);
        codes.put("KEY_PROG1", 148);
        codes.put("KEY_PROG2", 149);
        codes.put("KEY_VENDOR", 360);
        codes.put("KEY_CAMERA", 212);
        codes.put("KEY_HOMEPAGE", 172);
        codes.put("KEY_HOME", 102);
        codes.put("KEY_BACK", 158);
        codes.put("KEY_MENU", 139);
        codes.put("KEY_PLAYPAUSE", 164);
        codes.put("KEY_NEXTSONG", 163);
        codes.put("KEY_PREVIOUSSONG", 165);
        codes.put("KEY_HEADSETPHONE", 226);
        LINUX_CODES_BY_NAME = Collections.unmodifiableMap(codes);
    }

    /** One decoded EV_KEY transition. */
    public static final class ParsedKey {
        public final String device;
        public final String kernelName;
        /** Null when the identifier is neither a known name nor a hex code. */
        public final Integer linuxCode;
        public final int androidKeyCode;
        public final int action;
        public final boolean touchContact;

        ParsedKey(String device, String kernelName, Integer linuxCode, int androidKeyCode,
                  int action, boolean touchContact) {
            this.device = device;
            this.kernelName = kernelName;
            this.linuxCode = linuxCode;
            this.androidKeyCode = androidKeyCode;
            this.action = action;
            this.touchContact = touchContact;
        }
    }

    /** Returns null for any line that is not a decodable EV_KEY transition. */
    public static ParsedKey parse(String line) {
        if (line == null) {
            return null;
        }
        Matcher match = EVENT_PATTERN.matcher(line);
        if (!match.matches()) {
            return null;
        }
        String device = emptyToNull(match.group(1));
        String identifier = match.group(2).toUpperCase(java.util.Locale.ROOT);
        String value = match.group(3).toUpperCase(java.util.Locale.ROOT);

        int action;
        String bare = stripSurrounding(value);
        if ("DOWN".equals(bare) || "00000001".equals(bare) || "1".equals(bare)) {
            action = ACTION_DOWN;
        } else if ("UP".equals(bare) || "00000000".equals(bare) || "0".equals(bare)) {
            action = ACTION_UP;
        } else if ("REPEAT".equals(bare) || "00000002".equals(bare) || "2".equals(bare)) {
            action = ACTION_REPEAT;
        } else {
            return null;
        }

        Integer linuxCode = parseLinuxCode(identifier);
        String kernelName = (identifier.startsWith("KEY_") || identifier.startsWith("BTN_"))
                ? identifier
                : "KEY_" + identifier;

        Integer known = knownAndroidCode(identifier, linuxCode);
        int androidCode = known != null ? known : syntheticCode(identifier, linuxCode);

        boolean touchContact = "BTN_TOUCH".equals(identifier)
                || identifier.startsWith("BTN_TOOL_")
                || "BTN_STYLUS".equals(identifier)
                || "BTN_STYLUS2".equals(identifier);

        return new ParsedKey(device, kernelName, linuxCode, androidCode, action, touchContact);
    }

    private static String stripSurrounding(String value) {
        if (value.length() >= 2 && value.charAt(0) == '(' && value.charAt(value.length() - 1) == ')') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Integer parseLinuxCode(String identifier) {
        if (identifier.startsWith("KEY_") || identifier.startsWith("BTN_")) {
            return LINUX_CODES_BY_NAME.get(identifier);
        }
        String token = identifier.startsWith("0X") ? identifier.substring(2) : identifier;
        try {
            // getevent prints event codes as zero-padded hexadecimal values.
            return Integer.parseInt(token, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer knownAndroidCode(String identifier, Integer linuxCode) {
        if ("KEY_POWER".equals(identifier) || isCode(linuxCode, 116)) {
            return KEYCODE_POWER;
        }
        if ("KEY_VOLUMEUP".equals(identifier) || isCode(linuxCode, 115)) {
            return KEYCODE_VOLUME_UP;
        }
        if ("KEY_VOLUMEDOWN".equals(identifier) || isCode(linuxCode, 114)) {
            return KEYCODE_VOLUME_DOWN;
        }
        if ("KEY_CAMERA".equals(identifier) || isCode(linuxCode, 212) || isCode(linuxCode, 528)) {
            return KEYCODE_CAMERA;
        }
        if ("KEY_FOCUS".equals(identifier)) {
            return KEYCODE_FOCUS;
        }
        if ("KEY_ASSISTANT".equals(identifier) || "KEY_ASSIST".equals(identifier)
                || "KEY_VOICECOMMAND".equals(identifier)
                || isCode(linuxCode, 217) || isCode(linuxCode, 582)) {
            return KEYCODE_ASSIST;
        }
        if ("KEY_HEADSETPHONE".equals(identifier) || "KEY_HEADSETHOOK".equals(identifier)
                || isCode(linuxCode, 226)) {
            return KEYCODE_HEADSETHOOK;
        }
        if ("KEY_MUTE".equals(identifier)) {
            return KEYCODE_MUTE;
        }
        if ("KEY_WAKEUP".equals(identifier) || isCode(linuxCode, 143)) {
            return KEYCODE_WAKEUP;
        }
        if ("KEY_HOMEPAGE".equals(identifier) || "KEY_HOME".equals(identifier) || isCode(linuxCode, 102)) {
            return KEYCODE_HOME;
        }
        if ("KEY_BACK".equals(identifier) || isCode(linuxCode, 158)) {
            return KEYCODE_BACK;
        }
        if ("KEY_MENU".equals(identifier) || isCode(linuxCode, 139)) {
            return KEYCODE_MENU;
        }
        if ("KEY_APPSELECT".equals(identifier) || "KEY_ALL_APPS".equals(identifier) || isCode(linuxCode, 580)) {
            return KEYCODE_APP_SWITCH;
        }
        if ("KEY_PLAYPAUSE".equals(identifier) || isCode(linuxCode, 164)) {
            return KEYCODE_MEDIA_PLAY_PAUSE;
        }
        if ("KEY_NEXTSONG".equals(identifier) || isCode(linuxCode, 163)) {
            return KEYCODE_MEDIA_NEXT;
        }
        if ("KEY_PREVIOUSSONG".equals(identifier) || isCode(linuxCode, 165)) {
            return KEYCODE_MEDIA_PREVIOUS;
        }
        return null;
    }

    private static boolean isCode(Integer linuxCode, int expected) {
        return linuxCode != null && linuxCode == expected;
    }

    /**
     * FNV-1a over a stable identity so an unlabelled OEM button keeps the same app key code
     * across reboots and across the app/bridge boundary.
     */
    private static int syntheticCode(String identifier, Integer linuxCode) {
        String stableIdentity = linuxCode != null ? ("LINUX:" + linuxCode) : identifier;
        int hash = 0x811c9dc5;
        for (int i = 0; i < stableIdentity.length(); i++) {
            hash = (hash ^ stableIdentity.charAt(i)) * 0x01000193;
        }
        return SYNTHETIC_KEY_BASE | (hash & SYNTHETIC_KEY_MASK);
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().isEmpty() ? null : value;
    }
}
