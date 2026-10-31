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

    // App-originated gestures occupy a separate positive range: Android codes are small,
    // OEM hashes start at 0x40000000 and chord ids are negative. Never decoded from getevent.
    public static final int ASSISTANT_GESTURE = 0x20000001;

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

    /**
     * Android's Generic.kl for keyboards, the mouse's back/forward buttons and gamepads: Linux code
     * to the Android key code the framework reports for the same press. Without it a keyboard's
     * KEY_E came out of here as a synthetic code while Accessibility reported KEYCODE_E, so the
     * coordinator could not tell they were one press and each key counted twice (2026-10-29).
     * Mouse left/right/middle stay synthetic: Android reports them as pointer buttons, not keys.
     */
    private static final Map<Integer, Integer> STANDARD_BY_LINUX;
    private static final Map<String, Integer> STANDARD_LINUX_BY_NAME;

    static {
        Map<Integer, Integer> byLinux = new HashMap<>();
        Map<String, Integer> byName = new HashMap<>();
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int[] letterLinux = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};
        for (int i = 0; i < letters.length(); i++) {
            standard(byLinux, byName, letterLinux[i], 29 + i, "KEY_" + letters.charAt(i));
        }
        for (int digit = 1; digit <= 9; digit++) {
            standard(byLinux, byName, 1 + digit, 7 + digit, "KEY_" + digit);
        }
        standard(byLinux, byName, 11, 7, "KEY_0");
        for (int f = 1; f <= 10; f++) {
            standard(byLinux, byName, 58 + f, 130 + f, "KEY_F" + f);
        }
        standard(byLinux, byName, 87, 141, "KEY_F11");
        standard(byLinux, byName, 88, 142, "KEY_F12");
        int[][] keys = {
                {1, 111}, {12, 69}, {13, 70}, {14, 67}, {15, 61}, {26, 71}, {27, 72}, {28, 66},
                {29, 113}, {39, 74}, {40, 75}, {41, 68}, {42, 59}, {43, 73}, {51, 55}, {52, 56},
                {53, 76}, {54, 60}, {55, 155}, {56, 57}, {57, 62}, {58, 115}, {69, 143}, {70, 116},
                {71, 151}, {72, 152}, {73, 153}, {74, 156}, {75, 148}, {76, 149}, {77, 150}, {78, 157},
                {79, 145}, {80, 146}, {81, 147}, {82, 144}, {83, 158}, {96, 160}, {97, 114}, {98, 154},
                {99, 120}, {100, 58}, {103, 19}, {104, 92}, {105, 21}, {106, 22}, {107, 123}, {108, 20},
                {109, 93}, {110, 124}, {111, 112}, {119, 121}, {125, 117}, {126, 118}, {127, 82},
        };
        String[] keyNames = {
                "KEY_ESC", "KEY_MINUS", "KEY_EQUAL", "KEY_BACKSPACE", "KEY_TAB", "KEY_LEFTBRACE", "KEY_RIGHTBRACE", "KEY_ENTER",
                "KEY_LEFTCTRL", "KEY_SEMICOLON", "KEY_APOSTROPHE", "KEY_GRAVE", "KEY_LEFTSHIFT", "KEY_BACKSLASH", "KEY_COMMA", "KEY_DOT",
                "KEY_SLASH", "KEY_RIGHTSHIFT", "KEY_KPASTERISK", "KEY_LEFTALT", "KEY_SPACE", "KEY_CAPSLOCK", "KEY_NUMLOCK", "KEY_SCROLLLOCK",
                "KEY_KP7", "KEY_KP8", "KEY_KP9", "KEY_KPMINUS", "KEY_KP4", "KEY_KP5", "KEY_KP6", "KEY_KPPLUS",
                "KEY_KP1", "KEY_KP2", "KEY_KP3", "KEY_KP0", "KEY_KPDOT", "KEY_KPENTER", "KEY_RIGHTCTRL", "KEY_KPSLASH",
                "KEY_SYSRQ", "KEY_RIGHTALT", "KEY_UP", "KEY_PAGEUP", "KEY_LEFT", "KEY_RIGHT", "KEY_END", "KEY_DOWN",
                "KEY_PAGEDOWN", "KEY_INSERT", "KEY_DELETE", "KEY_PAUSE", "KEY_LEFTMETA", "KEY_RIGHTMETA", "KEY_COMPOSE",
        };
        for (int i = 0; i < keys.length; i++) {
            standard(byLinux, byName, keys[i][0], keys[i][1], keyNames[i]);
        }
        // Mouse back/forward: Android turns these pointer buttons into BACK and FORWARD keys.
        standard(byLinux, byName, 275, 4, "BTN_SIDE");
        standard(byLinux, byName, 278, 4, "BTN_BACK");
        standard(byLinux, byName, 276, 125, "BTN_EXTRA");
        standard(byLinux, byName, 277, 125, "BTN_FORWARD");
        // Gamepad face, shoulder and stick buttons, under every name getevent may print.
        standard(byLinux, byName, 304, 96, "BTN_A", "BTN_SOUTH", "BTN_GAMEPAD");
        standard(byLinux, byName, 305, 97, "BTN_B", "BTN_EAST");
        standard(byLinux, byName, 306, 98, "BTN_C");
        standard(byLinux, byName, 307, 99, "BTN_X", "BTN_NORTH");
        standard(byLinux, byName, 308, 100, "BTN_Y", "BTN_WEST");
        standard(byLinux, byName, 309, 101, "BTN_Z");
        standard(byLinux, byName, 310, 102, "BTN_TL");
        standard(byLinux, byName, 311, 103, "BTN_TR");
        standard(byLinux, byName, 312, 104, "BTN_TL2");
        standard(byLinux, byName, 313, 105, "BTN_TR2");
        standard(byLinux, byName, 314, 109, "BTN_SELECT");
        standard(byLinux, byName, 315, 108, "BTN_START");
        standard(byLinux, byName, 316, 110, "BTN_MODE");
        standard(byLinux, byName, 317, 106, "BTN_THUMBL");
        standard(byLinux, byName, 318, 107, "BTN_THUMBR");
        STANDARD_BY_LINUX = Collections.unmodifiableMap(byLinux);
        STANDARD_LINUX_BY_NAME = Collections.unmodifiableMap(byName);
    }

    private static void standard(Map<Integer, Integer> byLinux, Map<String, Integer> byName,
                                 int linux, int android, String... names) {
        byLinux.put(linux, android);
        for (String name : names) {
            byName.put(name, linux);
        }
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
            Integer code = LINUX_CODES_BY_NAME.get(identifier);
            return code != null ? code : STANDARD_LINUX_BY_NAME.get(identifier);
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
        return linuxCode != null ? STANDARD_BY_LINUX.get(linuxCode) : null;
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

    /**
     * True for a code {@link #parse} invented for a button Android has no key code for. The app
     * uses it to warn that the maker's own action on that button still fires: capture is
     * read-only and never grabs the key.
     */
    public static boolean isSynthetic(int keyCode) {
        return keyCode >= SYNTHETIC_KEY_BASE;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().isEmpty() ? null : value;
    }
}
