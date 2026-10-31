package com.example.bridge;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared, pure payload rules. Never include payloads in validation errors or logs. */
public final class ActionPayloads {
    private ActionPayloads() { }

    public static int timeoutSeconds(Map<String, String> params) {
        String value = params.getOrDefault("timeout_sec", "10");
        if (!Arrays.asList("5", "10", "30", "60").contains(value)) {
            throw new IllegalArgumentException("Invalid timeout");
        }
        return Integer.parseInt(value);
    }

    public static String contentType(Map<String, String> params) {
        switch (params.getOrDefault("content_type", "none")) {
            case "none": return null;
            case "json": return "application/json";
            case "form": return "application/x-www-form-urlencoded";
            case "text": return "text/plain";
            default: throw new IllegalArgumentException("Invalid content type");
        }
    }

    /** Split CRLF/LF only. Everything after the colon, including spaces, is the value. */
    public static List<Map.Entry<String, String>> headers(String raw) {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String line : raw.split("\r?\n", -1)) {
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon <= 0 || !line.substring(0, colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
                    || line.indexOf('\r') >= 0 || line.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid header");
            }
            result.add(new AbstractMap.SimpleImmutableEntry<>(line.substring(0, colon), line.substring(colon + 1)));
        }
        return result;
    }

    /** Row-level validation MUST happen before serializing to the legacy k=v;k2=v2 format. */
    public static String validateIntentExtra(String key, String value) {
        if (key == null || key.isEmpty()) return "empty_key";
        if (key.indexOf(';') >= 0 || key.indexOf('=') >= 0
                || value.indexOf(';') >= 0 || value.indexOf('=') >= 0) return "separator_in_extra";
        return null;
    }

    /** Strict legacy parser: reject malformed rows rather than silently dropping/splitting them. */
    public static List<Map.Entry<String, String>> intentExtras(String raw) {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String row : raw.split(";", -1)) {
            int eq = row.indexOf('=');
            if (eq <= 0 || validateIntentExtra(row.substring(0, eq), row.substring(eq + 1)) != null) {
                throw new IllegalArgumentException("Invalid intent extras");
            }
            result.add(new AbstractMap.SimpleImmutableEntry<>(row.substring(0, eq), row.substring(eq + 1)));
        }
        return result;
    }

    public static boolean isActivity(Map<String, String> params) {
        String target = params.getOrDefault("target", "broadcast");
        if (!"broadcast".equals(target) && !"activity".equals(target)) {
            throw new IllegalArgumentException("Invalid intent target");
        }
        return "activity".equals(target);
    }

    public static String[] intentCommand(Map<String, String> params) {
        String action = params.getOrDefault("action", "");
        if (action.isEmpty()) throw new IllegalArgumentException("Missing intent action");
        List<String> command = new ArrayList<>(Arrays.asList("am", isActivity(params) ? "start" : "broadcast", "-a", action));
        if (isActivity(params)) command.addAll(Arrays.asList("-f", "0x10000000"));
        String uri = params.getOrDefault("uri", "");
        if (!uri.isEmpty()) command.addAll(Arrays.asList("-d", uri));
        String pkg = params.getOrDefault("package", "");
        if (!pkg.isEmpty()) command.addAll(Arrays.asList("-p", pkg));
        for (Map.Entry<String, String> extra : intentExtras(params.get("extras"))) {
            command.addAll(Arrays.asList("--es", extra.getKey(), extra.getValue()));
        }
        return command.toArray(new String[0]);
    }

    public static String[] settingCommand(Map<String, String> params) {
        String ns = params.getOrDefault("namespace", "");
        String key = params.getOrDefault("key", "");
        if (!Arrays.asList("system", "secure", "global").contains(ns) || key.isEmpty() || !params.containsKey("value")) {
            throw new IllegalArgumentException("Invalid setting");
        }
        return new String[]{"settings", "put", ns, key, params.get("value")};
    }

    /** Shell quoting is display syntax only; execution passes these same arguments directly. */
    public static String commandLine(String[] command) {
        List<String> quoted = new ArrayList<>();
        for (String arg : command) {
            quoted.add(arg.matches("[A-Za-z0-9_./:@%+-]+") ? arg : "'" + arg.replace("'", "'\\''") + "'");
        }
        return String.join(" ", quoted);
    }

    public static String previewCommand(String type, Map<String, String> params) {
        switch (type) {
            case "shell_command": return params.getOrDefault("command", "");
            case "send_intent": return commandLine(intentCommand(params));
            case "modify_setting": return commandLine(settingCommand(params));
            case "http_request":
                contentType(params);
                // Only what is actually sent: no "0 headers", no "NONE" body type.
                if (params.getOrDefault("url", "").isEmpty()) return "";
                StringBuilder line = new StringBuilder(params.getOrDefault("method", "").toUpperCase(Locale.ROOT))
                        .append(' ').append(params.getOrDefault("url", ""));
                int headerCount = headers(params.get("headers")).size();
                if (headerCount > 0) line.append(" · ").append(headerCount).append(headerCount == 1 ? " header" : " headers");
                String body = params.getOrDefault("content_type", "none");
                if (!body.isEmpty() && !body.equals("none")) line.append(" · ").append(body.toUpperCase(Locale.ROOT));
                return line.toString();
            default: return "";
        }
    }
}
