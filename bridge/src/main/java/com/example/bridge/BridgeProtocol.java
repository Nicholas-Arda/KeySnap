package com.example.bridge;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Constants shared by the app and the detached bridge process.
 *
 * <p>The app and the bridge run as different UIDs and never share memory, so every name here
 * is part of a wire/disk contract. Changing one requires reinstalling the bridge payload.
 */
public final class BridgeProtocol {

    private BridgeProtocol() { }

    /** Bumped whenever the on-disk payload or the socket protocol changes incompatibly, or when
     *  :bridge behavior changes at all — an already-running detached bridge has no way to pick up
     *  a source-only fix otherwise. */
    public static final int VERSION = 15;

    /** Process name given to app_process, used by `pidof` for liveness checks. */
    public static final String NICE_NAME = "arda_bridge";

    /** Shell-owned working directory. The app UID cannot read this; it is for the bridge only. */
    public static final String BASE_DIR = "/data/local/tmp/ardamapper";

    public static final String DEX_NAME = "bridge.dex";
    public static final String START_SCRIPT_NAME = "start.sh";
    public static final String LOG_NAME = "bridge.log";

    /** Written by the app into its external files dir; read by the bridge (shell is in ext_data_rw). */
    public static final String CONFIG_NAME = "bridge_config.json";

    /** Entry point class launched by app_process. */
    public static final String MAIN_CLASS = "com.example.bridge.BridgeMain";

    /** Printed by start.sh once the detached process has been spawned. */
    public static final String SPAWN_MARKER = "ARDA_SPAWN";

    // ---- Loopback socket protocol (app connects as client; bridge listens) ----

    public static final String HOST = "127.0.0.1";

    /** Client -> bridge. */
    public static final String CMD_HELLO = "HELLO";
    public static final String CMD_PING = "PING";
    public static final String CMD_RELOAD = "RELOAD";
    public static final String CMD_SHUTDOWN = "SHUTDOWN";
    /**
     * RUN <json>: run one action chain now, for the editor's Run button and automations. Answered
     * with OK ran total actionResults — see {@link #encodeActionResults}; a bridge older than v11
     * omits the last field.
     */
    public static final String CMD_RUN = "RUN";

    /** Bridge -> client. */
    public static final String MSG_OK = "OK";
    public static final String MSG_PONG = "PONG";
    public static final String MSG_ERROR = "ERROR";
    public static final String MSG_EVENT = "EVENT";
    /** TRIGGER ts keyCode pressType scriptId success actionResults — see {@link #encodeActionResults}. */
    public static final String MSG_TRIGGER = "TRIGGER";
    public static final String MSG_DEVICE = "DEVICE";
    public static final String MSG_BYE = "BYE";

    /**
     * The TRIGGER line's last field: one {@code type=1|0} per configured action, in script order,
     * comma-separated. Only catalog type ids travel here, never an action's params.
     */
    public static String encodeActionResults(List<String> types, boolean[] ok) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(types.get(i)).append('=').append(ok[i] ? '1' : '0');
        }
        return out.toString();
    }

    /** Null when the field is missing (an older bridge) or malformed; the app then trusts the success flag. */
    public static List<Map.Entry<String, Boolean>> decodeActionResults(String field) {
        if (field == null) {
            return null;
        }
        List<Map.Entry<String, Boolean>> results = new ArrayList<>();
        if (field.isEmpty()) {
            return results;
        }
        for (String pair : field.split(",", -1)) {
            int eq = pair.lastIndexOf('=');
            if (eq <= 0 || eq != pair.length() - 2) {
                return null;
            }
            char flag = pair.charAt(eq + 1);
            if (flag != '0' && flag != '1') {
                return null;
            }
            results.add(new AbstractMap.SimpleImmutableEntry<>(pair.substring(0, eq), flag == '1'));
        }
        return results;
    }

    public static String configPath(String externalFilesDir) {
        return externalFilesDir + "/" + CONFIG_NAME;
    }
}
