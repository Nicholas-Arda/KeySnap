package com.example.bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A switch the user taught this phone ("Teach a setting"): the setting keys that changed together and
 * their two values. Encoded in the action's {@code writes} param as
 * {@code ns/key=on|off;ns/key=on|off}; the first key is the on/off state. Shared by the app (editor,
 * Undo) and the bridge (runs it), so both read it the same way.
 */
public final class LearnedSetting {
    public static final String TYPE = "learned_setting";
    public static final String PARAM_WRITES = "writes";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_NAME = "name";
    public static final int MAX_KEYS = 3;

    static final List<String> NAMESPACES = Arrays.asList("system", "secure", "global");
    /** Same shape as the app's PrivilegedEditor.SAFE_KEY. */
    static final Pattern KEY = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    /** Short single tokens only: no lists, names, SSIDs or JSON, so no user data is ever stored. */
    static final Pattern VALUE = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    /** Writing these turns off USB/wireless debugging, which kills the bridge itself. */
    static final Set<String> BLOCKED = new HashSet<>(Arrays.asList("adb_enabled", "adb_wifi_enabled", "development_settings_enabled"));

    public static final class Write {
        public final String namespace, key, on, off;

        public Write(String namespace, String key, String on, String off) {
            this.namespace = namespace;
            this.key = key;
            this.on = on;
            this.off = off;
        }
    }

    private LearnedSetting() { }

    public static boolean allowed(String namespace, String key, String on, String off) {
        return NAMESPACES.contains(namespace) && key != null && KEY.matcher(key).matches() && !BLOCKED.contains(key)
                && on != null && VALUE.matcher(on).matches() && off != null && VALUE.matcher(off).matches() && !on.equals(off);
    }

    /** Null when [writes] is malformed, empty, too long or names a blocked key. */
    public static List<Write> parse(String writes) {
        if (writes == null || writes.trim().isEmpty()) return null;
        List<Write> result = new ArrayList<>();
        for (String part : writes.split(";")) {
            int slash = part.indexOf('/'), eq = part.indexOf('='), bar = part.lastIndexOf('|');
            if (slash <= 0 || eq <= slash + 1 || bar <= eq + 1) return null;
            Write write = new Write(part.substring(0, slash), part.substring(slash + 1, eq),
                    part.substring(eq + 1, bar), part.substring(bar + 1));
            if (!allowed(write.namespace, write.key, write.on, write.off)) return null;
            result.add(write);
        }
        return result.size() > MAX_KEYS ? null : Collections.unmodifiableList(result);
    }

    public static String encode(List<Write> writes) {
        StringBuilder out = new StringBuilder();
        for (Write w : writes) {
            if (out.length() > 0) out.append(';');
            out.append(w.namespace).append('/').append(w.key).append('=').append(w.on).append('|').append(w.off);
        }
        return out.toString();
    }

    /** The writes as a preset, probed on the state key: a key gone after an update fails cleanly. */
    public static List<PresetSettings.Variant> variants(String writes) {
        List<Write> parsed = parse(writes);
        if (parsed == null) return null;
        PresetSettings.Write[] list = new PresetSettings.Write[parsed.size()];
        for (int i = 0; i < parsed.size(); i++) {
            Write w = parsed.get(i);
            list[i] = new PresetSettings.Write(w.namespace, w.key, w.on, w.off);
        }
        Write state = parsed.get(0);
        return Collections.singletonList(new PresetSettings.Variant(state.namespace, state.key, list));
    }
}
