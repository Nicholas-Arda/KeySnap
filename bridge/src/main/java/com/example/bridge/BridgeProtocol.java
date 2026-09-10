package com.example.bridge;

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
    public static final int VERSION = 10;

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
    /** RUN <json>: run one action chain now, for the editor's Run button. */
    public static final String CMD_RUN = "RUN";

    /** Bridge -> client. */
    public static final String MSG_OK = "OK";
    public static final String MSG_PONG = "PONG";
    public static final String MSG_ERROR = "ERROR";
    public static final String MSG_EVENT = "EVENT";
    public static final String MSG_TRIGGER = "TRIGGER";
    public static final String MSG_DEVICE = "DEVICE";
    public static final String MSG_BYE = "BYE";

    public static String configPath(String externalFilesDir) {
        return externalFilesDir + "/" + CONFIG_NAME;
    }
}
