package com.example.bridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * "Teach a setting": which settings keys follow one switch. Three snapshots of
 * {@code settings list system|secure|global}: at the start, after the user changes the switch, and
 * after they change it back. A key is kept only if the change moved it and changing back returned it,
 * which drops counters and timestamps for free. Snapshots stay in memory and are never logged: they
 * hold device names and ids.
 */
public final class SettingsTeach {
    public static final String OK = "ok", NONE = "none", TOO_MANY = "too-many", BLOCKED = "blocked";

    /** namespace -> (key -> value) */
    public static final class Snapshot {
        final Map<String, Map<String, String>> byNamespace = new HashMap<>();
    }

    /** One kept key: [changed] after the user's first change, [back] after changing it back. */
    public static final class Change {
        public final String namespace, key, changed, back;

        Change(String namespace, String key, String changed, String back) {
            this.namespace = namespace;
            this.key = key;
            this.changed = changed;
            this.back = back;
        }
    }

    public static final class Result {
        public final String status;
        public final List<Change> changes;

        Result(String status, List<Change> changes) {
            this.status = status;
            this.changes = changes;
        }
    }

    private SettingsTeach() { }

    /** One namespace's {@code settings list} output. Lines that aren't a plain key=value are skipped. */
    static Map<String, String> parse(String output) {
        Map<String, String> values = new LinkedHashMap<>();
        if (output == null) return values;
        for (String line : output.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq);
            if (LearnedSetting.KEY.matcher(key).matches()) values.put(key, line.substring(eq + 1).trim());
        }
        return values;
    }

    static void put(Snapshot snapshot, String namespace, String output) {
        snapshot.byNamespace.put(namespace, parse(output));
    }

    /** Keys the phone moves by itself while the user is in Settings. */
    static boolean drifts(String key) {
        return key.startsWith("screen_brightness") || key.startsWith("volume_") || key.equals("next_alarm_formatted");
    }

    /** How many usable keys moved between [start] and [changed]; 0 means "No change seen". */
    public static int moved(Snapshot start, Snapshot changed) {
        int count = 0;
        for (String ns : LearnedSetting.NAMESPACES) {
            Map<String, String> a = values(start, ns), b = values(changed, ns);
            for (String key : keys(a, b)) {
                String before = a.get(key), after = b.get(key);
                if (after != null && !after.equals(before) && usable(key, after, before)) count++;
            }
        }
        return count;
    }

    public static Result result(Snapshot start, Snapshot changed, Snapshot back) {
        List<Change> kept = new ArrayList<>();
        for (String ns : LearnedSetting.NAMESPACES) {
            Map<String, String> a = values(start, ns), b = values(changed, ns), c = values(back, ns);
            for (String key : keys(a, b)) {
                String before = a.get(key), after = b.get(key), returned = c.get(key);
                boolean reversed = before == null
                        // A key some phones create on the first change: both later values exist and differ.
                        ? after != null && returned != null && !after.equals(returned)
                        : after != null && !after.equals(before) && Objects.equals(returned, before);
                if (reversed && usable(key, after, returned)) kept.add(new Change(ns, key, after, returned));
            }
        }
        if (kept.isEmpty()) return new Result(NONE, kept);
        for (Change change : kept) {
            if (LearnedSetting.BLOCKED.contains(change.key)) return new Result(BLOCKED, new ArrayList<Change>());
        }
        if (kept.size() > LearnedSetting.MAX_KEYS) return new Result(TOO_MANY, new ArrayList<Change>());
        return new Result(OK, kept);
    }

    private static boolean usable(String key, String one, String other) {
        // A blocked key stays in so the user hears why, instead of "No change seen".
        return !drifts(key) && one != null && LearnedSetting.VALUE.matcher(one).matches()
                && (other == null || LearnedSetting.VALUE.matcher(other).matches());
    }

    private static Map<String, String> values(Snapshot snapshot, String ns) {
        Map<String, String> map = snapshot.byNamespace.get(ns);
        return map == null ? new HashMap<String, String>() : map;
    }

    private static Set<String> keys(Map<String, String> a, Map<String, String> b) {
        Set<String> keys = new LinkedHashSet<>(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }
}
