package com.example.bridge;

import android.content.Context;
import android.os.Looper;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point of the detached System Bridge.
 *
 * <p>Launched by app_process as the shell UID from a session that has already been detached
 * (setsid + nohup), so this process has no parent in the app's process tree and is unaffected
 * when the OEM lifecycle manager kills the app. Everything it needs — the mapping config, the
 * control port and the auth token — is read from a file the app owns, so it never calls back
 * into the app to stay alive.
 */
public final class BridgeMain implements GeteventReader.Listener, EventServer.Listener,
        PressTimingMachine.Listener {

    private static final long CONFIG_POLL_MS = 1000L;
    private static final long MAX_DELAY_MS = 5000L;
    private static final int MAX_REPEAT_COUNT = 20;

    private final File configFile;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "arda-bridge-timer");
                thread.setDaemon(true);
                return thread;
            });

    private final ExecutorService actionExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "arda-bridge-actions");
                thread.setDaemon(true);
                return thread;
            });

    private final TorchController torch = new TorchController();
    private final RingerController ringer = new RingerController();
    private final ConstraintEvaluator constraints = new ConstraintEvaluator(torch);
    private PressTimingMachine pressTiming;
    private EventServer server;
    private GeteventReader reader;

    private volatile BridgeConfig config;
    private volatile long configLastModified;

    private BridgeMain(File configFile) {
        this.configFile = configFile;
    }

    public static void main(String[] args) {
        File base = new File(BridgeProtocol.BASE_DIR);
        if (!base.exists() && !base.mkdirs()) {
            System.err.println("Cannot create " + base);
        }
        BridgeLog.init(new File(base, BridgeProtocol.LOG_NAME));

        String configPath = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                configPath = args[i + 1];
            }
        }
        if (configPath == null) {
            BridgeLog.w("Missing --config argument; refusing to start");
            System.exit(2);
            return;
        }
        try {
            new BridgeMain(new File(configPath)).start();
        } catch (Throwable t) {
            BridgeLog.e("Bridge failed to start", t);
            System.exit(1);
        }
    }

    private void start() throws Exception {
        Looper.prepareMainLooper();

        config = BridgeConfig.read(configFile);
        configLastModified = configFile.lastModified();
        if (config.port <= 0 || config.token.isEmpty()) {
            BridgeLog.w("Config has no control port/token; refusing to start");
            System.exit(2);
            return;
        }
        BridgeLog.i("Bridge v" + BridgeProtocol.VERSION + " starting: pid="
                + android.os.Process.myPid() + " uid=" + android.os.Process.myUid()
                + " scripts=" + config.scripts.size() + " port=" + config.port);

        server = new EventServer(config.port, config.token, this);
        try {
            server.bind();
        } catch (Exception e) {
            // Another bridge instance already owns the port. Leave it running and exit quietly:
            // relaunching would leave two readers competing for the same gestures.
            BridgeLog.w("Control port " + config.port + " already bound; another bridge is running. Exiting.");
            System.exit(3);
            return;
        }
        writePidFile();

        pressTiming = new PressTimingMachine(scheduler, this);

        ringer.initialize();

        Context context = SystemContext.create();
        if (context != null) {
            torch.initialize(context);
        } else {
            BridgeLog.w("No system context; torch actions will be unavailable");
        }

        Thread serverThread = new Thread(server, "arda-bridge-server");
        serverThread.setDaemon(true);
        serverThread.start();

        reader = new GeteventReader(this);
        Thread readerThread = new Thread(reader, "arda-bridge-getevent");
        readerThread.setDaemon(true);
        readerThread.start();

        scheduler.scheduleWithFixedDelay(this::reloadConfigIfChanged,
                CONFIG_POLL_MS, CONFIG_POLL_MS, TimeUnit.MILLISECONDS);

        requestBatteryWhitelist(config.packageName);

        server.setStatus("running");
        BridgeLog.i("Bridge running");

        // The main thread owns the Looper so CameraManager torch callbacks are delivered.
        Looper.loop();
    }

    /**
     * Exempts the app from Doze while the bridge is set up.
     *
     * The shell UID may change the whitelist, the app UID may not, so this is done here. It only
     * helps the app: this process is not managed by the app standby machinery at all, and its
     * survival comes from being detached rather than from any exemption.
     */
    private void requestBatteryWhitelist(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        try {
            Process process = new ProcessBuilder(
                    "dumpsys", "deviceidle", "whitelist", "+" + packageName)
                    .redirectErrorStream(true)
                    .start();
            process.waitFor();
            BridgeLog.i("deviceidle whitelist +" + packageName + " exited " + process.exitValue());
        } catch (Exception e) {
            // Optional hardening: an OEM build may refuse or rename the command.
            BridgeLog.w("Could not add " + packageName + " to the deviceidle whitelist: " + e);
        }
    }

    private void writePidFile() {
        try (FileWriter writer = new FileWriter(new File(BridgeProtocol.BASE_DIR, "bridge.pid"), false)) {
            writer.write(String.valueOf(android.os.Process.myPid()));
        } catch (Exception e) {
            BridgeLog.w("Could not write pid file: " + e);
        }
    }

    private void reloadConfigIfChanged() {
        try {
            long modified = configFile.lastModified();
            if (modified == configLastModified || modified == 0L) {
                return;
            }
            BridgeConfig updated = BridgeConfig.read(configFile);
            configLastModified = modified;
            if (updated.port != config.port || !updated.token.equals(config.token)) {
                BridgeLog.w("Control port/token changed in config; a bridge restart is required to apply it");
            }
            config = updated;
            pressTiming.reset();
            BridgeLog.i("Config reloaded: scripts=" + updated.scripts.size()
                    + " globallyEnabled=" + updated.globallyEnabled);
        } catch (Exception e) {
            BridgeLog.w("Config reload failed, keeping previous config: " + e);
        }
    }

    // ---- getevent ----

    @Override
    public void onKeyEvent(KeyEventMapping.ParsedKey key, String deviceName) {
        BridgeConfig current = config;
        long now = System.currentTimeMillis();
        boolean down = key.action == KeyEventMapping.ACTION_DOWN;
        boolean mapped = current.isMappedKey(key.androidKeyCode);

        String source = "Advanced Mode (" + key.kernelName
                + (deviceName != null && !deviceName.isEmpty() ? "; " + deviceName : "")
                + (key.device != null ? "; " + key.device : "") + ")";
        server.broadcastKeyEvent(now, key.androidKeyCode, down, mapped, source);

        if (!mapped) {
            return;
        }
        pressTiming.onEvent(key.androidKeyCode, down, now,
                current.doublePressWindowFor(key.androidKeyCode), current.longPressThresholdFor(key.androidKeyCode));
    }

    @Override
    public void onDevicesChanged(Map<String, String> devicesByPath) {
        for (Map.Entry<String, String> entry : devicesByPath.entrySet()) {
            server.broadcastDevice(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void onStreamError(String message) {
        server.setStatus("getevent-restarting");
    }

    // ---- gestures ----

    /**
     * Actions run off the caller's thread: this is invoked from the getevent reader thread, and an
     * action chain can block for seconds (a delay, or an {@code input keyevent} subprocess). The
     * executor is single-threaded so chained actions still run in order.
     */
    @Override
    public void onGesture(int keyCode, String pressType) {
        BridgeConfig current = config;
        for (BridgeConfig.Script script : current.scriptsFor(keyCode, pressType)) {
            if (!constraintsSatisfied(script)) {
                continue;
            }
            actionExecutor.execute(() -> runScript(script, keyCode, pressType));
        }
    }

    private boolean constraintsSatisfied(BridgeConfig.Script script) {
        for (BridgeConfig.Constraint constraint : script.constraints) {
            if (!constraints.isSatisfied(constraint)) {
                return false;
            }
        }
        return true;
    }

    /**
     * "repeat_previous" has no meaning as a standalone action; it replays whichever real action
     * immediately preceded it in this same list, so it's expanded here rather than passed to
     * {@link #executeAction}. A leading "repeat_previous" (no previous action yet) fails safe.
     * Mirrors the equivalent expansion in the app's ScriptActionExecutor.execute(List).
     */
    private void runScript(BridgeConfig.Script script, int keyCode, String pressType) {
        runActions(script.actions, (action, success) ->
                logActionResult(script, keyCode, pressType, action, success));
        // One broadcast per physical press, not per action, mirroring the app process's own
        // per-script counting in HardwareKeyTriggerCoordinator.executeActions — otherwise a
        // multi-action script vibrates and increments "Triggers today" once per action.
        server.broadcastTrigger(System.currentTimeMillis(), keyCode, pressType, script.id, true);
    }

    /** Runs one chain, expanding "repeat_previous", and reports how many actions succeeded. */
    private int runActions(List<BridgeConfig.Action> actions, ActionReporter reporter) {
        int ran = 0;
        BridgeConfig.Action previous = null;
        for (BridgeConfig.Action action : actions) {
            if (BridgeConfig.ACTION_REPEAT_PREVIOUS.equals(action.type)) {
                Integer count = parseInt(action.params.get("count"));
                int clamped = count == null ? 0 : Math.max(0, Math.min(MAX_REPEAT_COUNT, count));
                if (previous == null || clamped <= 0) {
                    reporter.report(action, false);
                } else {
                    for (int i = 0; i < clamped; i++) {
                        boolean success = executeAction(previous);
                        ran += success ? 1 : 0;
                        reporter.report(previous, success);
                    }
                }
                continue;
            }
            boolean success = executeAction(action);
            ran += success ? 1 : 0;
            reporter.report(action, success);
            previous = action;
        }
        return ran;
    }

    private interface ActionReporter {
        void report(BridgeConfig.Action action, boolean success);
    }

    /**
     * The editor's Run button, so it shows what a real press does rather than what the app process
     * alone can do. No trigger broadcast: a test press must not move the counter or buzz.
     */
    @Override
    public String onRunRequested(String payloadJson) {
        List<BridgeConfig.Action> actions;
        try {
            actions = BridgeConfig.parseActions(payloadJson);
        } catch (Exception e) {
            // The payload carries the actions being tested, so a JSONTokener message would
            // echo the user's shell command or request body; log only the failure kind.
            BridgeLog.w("RUN payload could not be parsed: " + e.getClass().getSimpleName());
            return "0	0";
        }
        int ran = runActions(actions, (action, success) ->
                BridgeLog.i("Run from the app: action=" + action.type + " success=" + success));
        return ran + "	" + actions.size();
    }

    private void logActionResult(BridgeConfig.Script script, int keyCode, String pressType,
            BridgeConfig.Action action, boolean success) {
        BridgeLog.i("Triggered script=" + script.id + " key=" + keyCode
                + " press=" + pressType + " action=" + action.type + " success=" + success);
    }

    /**
     * Mirrors the AVAILABLE subset of the app's ScriptActionCatalog. Every other catalog entry is
     * COMING_SOON in the app UI and is deliberately inert here, so an old persisted script
     * referencing a not-yet-implemented action fails safe instead of crashing the bridge.
     */
    private boolean executeAction(BridgeConfig.Action action) {
        try {
            return runAction(action);
        } catch (Throwable t) {
            // The action executor runs on its own thread, where an uncaught throwable takes the
            // whole bridge process down with it and Advanced Mode silently stops working.
            BridgeLog.e("Action " + action.type + " threw", t);
            return false;
        }
    }

    private boolean runAction(BridgeConfig.Action action) {
        if (BridgeConfig.ACTION_TOGGLE_FLASHLIGHT.equals(action.type)) {
            return torch.toggle();
        }
        if (BridgeConfig.ACTION_ENABLE_FLASHLIGHT.equals(action.type)) {
            return torch.set(true);
        }
        if (BridgeConfig.ACTION_DISABLE_FLASHLIGHT.equals(action.type)) {
            return torch.set(false);
        }
        if (BridgeConfig.ACTION_MEDIA_PLAY_PAUSE.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MEDIA_PLAY_PAUSE);
        }
        if (BridgeConfig.ACTION_MEDIA_NEXT.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MEDIA_NEXT);
        }
        if (BridgeConfig.ACTION_MEDIA_PREVIOUS.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MEDIA_PREVIOUS);
        }
        if (BridgeConfig.ACTION_MEDIA_PLAY.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MEDIA_PLAY);
        }
        if (BridgeConfig.ACTION_MEDIA_PAUSE.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MEDIA_PAUSE);
        }
        if (BridgeConfig.ACTION_MEDIA_STOP.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MEDIA_STOP);
        }
        if (BridgeConfig.ACTION_MEDIA_FAST_FORWARD.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MEDIA_FAST_FORWARD);
        }
        if (BridgeConfig.ACTION_MEDIA_REWIND.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MEDIA_REWIND);
        }
        if (BridgeConfig.ACTION_VOLUME_TOGGLE_MUTE.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_VOLUME_MUTE);
        }
        if (BridgeConfig.ACTION_VOLUME_UP.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_VOLUME_UP);
        }
        if (BridgeConfig.ACTION_VOLUME_DOWN.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_VOLUME_DOWN);
        }
        if (BridgeConfig.ACTION_DELAY.equals(action.type)) {
            return sleepFor(action.params.get("duration"));
        }
        if (BridgeConfig.ACTION_GO_BACK.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_BACK);
        }
        if (BridgeConfig.ACTION_GO_HOME.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_HOME);
        }
        // KEYCODE_APP_SWITCH is unbound on several gesture-navigation ROMs, where the shell key
        // is silently swallowed; Accessibility's GLOBAL_ACTION_RECENTS works everywhere. Left to
        // the app path. See ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE.
        if (BridgeConfig.ACTION_OPEN_RECENTS.equals(action.type)) {
            return false;
        }
        if (BridgeConfig.ACTION_OPEN_MENU.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_MENU);
        }
        if (BridgeConfig.ACTION_EXPAND_NOTIFICATION_DRAWER.equals(action.type)) {
            return ShellKeyInjector.runShell("cmd", "statusbar", "expand-notifications");
        }
        if (BridgeConfig.ACTION_EXPAND_QUICK_SETTINGS.equals(action.type)) {
            return ShellKeyInjector.runShell("cmd", "statusbar", "expand-settings");
        }
        if (BridgeConfig.ACTION_COLLAPSE_STATUS_BAR.equals(action.type)) {
            return ShellKeyInjector.runShell("cmd", "statusbar", "collapse");
        }
        // Most OEM builds bind the screenshot to the power+volume-down chord rather than to
        // KEYCODE_SYSRQ, so the shell key does nothing on them; Accessibility's
        // GLOBAL_ACTION_TAKE_SCREENSHOT is the supported API. Left to the app path.
        if (BridgeConfig.ACTION_TAKE_SCREENSHOT.equals(action.type)) {
            return false;
        }
        if (BridgeConfig.ACTION_INPUT_KEY_CODE.equals(action.type)
                || BridgeConfig.ACTION_INPUT_KEY_EVENT.equals(action.type)) {
            Integer keyCode = parseInt(action.params.get("keyCode"));
            if (keyCode == null) {
                return false;
            }
            return ShellKeyInjector.sendKey(keyCode);
        }
        // `input text` is ASCII-only -- it drops or mangles every non-ASCII character, so a
        // Turkish, Russian or Arabic string arrives corrupted -- and it types into the field
        // instead of replacing its contents. The app's AccessibilityNodeInfo ACTION_SET_TEXT does
        // neither. Left to the app path.
        if (BridgeConfig.ACTION_INPUT_TEXT.equals(action.type)) {
            return false;
        }
        if (BridgeConfig.ACTION_TAP_SCREEN.equals(action.type)) {
            Integer x = parseInt(action.params.get("x"));
            Integer y = parseInt(action.params.get("y"));
            if (x == null || y == null) {
                return false;
            }
            int count = BridgeConfig.tapCount(action.params);
            long interval = BridgeConfig.tapIntervalMs(action.params);
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                if (!ShellKeyInjector.runShell("input", "tap", String.valueOf(x), String.valueOf(y))) {
                    return false;
                }
            }
            return true;
        }
        if (BridgeConfig.ACTION_SWIPE_SCREEN.equals(action.type)) {
            Integer x1 = parseInt(action.params.get("x1"));
            Integer y1 = parseInt(action.params.get("y1"));
            Integer x2 = parseInt(action.params.get("x2"));
            Integer y2 = parseInt(action.params.get("y2"));
            if (x1 == null || y1 == null || x2 == null || y2 == null) {
                return false;
            }
            // Mirrors the app's swipeScreen clamp exactly (see BridgeConfig.swipeDurationMs) so the
            // same script behaves identically on both sides.
            long clampedDuration = BridgeConfig.swipeDurationMs(action.params);
            return ShellKeyInjector.runShell("input", "swipe", String.valueOf(x1), String.valueOf(y1),
                    String.valueOf(x2), String.valueOf(y2), String.valueOf(clampedDuration));
        }
        // Android's `input` tool has no multi-touch primitive; only Accessibility's concurrent
        // GestureDescription strokes can do a real pinch. Only reachable through the app/
        // Accessibility path.
        if (BridgeConfig.ACTION_PINCH_SCREEN.equals(action.type)) {
            return false;
        }
        // No shell primitive toggles split-screen from the shell UID; only reachable through
        // Accessibility's GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN in the app path.
        if (BridgeConfig.ACTION_TOGGLE_SPLIT_SCREEN.equals(action.type)) {
            return false;
        }
        // Both need a live AccessibilityNodeInfo to read/set text selection on the focused field;
        // only reachable through the app/Accessibility path.
        if (BridgeConfig.ACTION_MOVE_CURSOR_TO_END.equals(action.type)
                || BridgeConfig.ACTION_SELECT_WORD_AT_CURSOR.equals(action.type)) {
            return false;
        }
        // `input keyevent`'s KEYCODE_VOLUME_MUTE only toggles (see ACTION_VOLUME_TOGGLE_MUTE
        // above); there is no distinct mute/unmute keycode and no stream-set primitive wired
        // through ShellKeyInjector. Only reachable through AudioManager in the app path.
        if (BridgeConfig.ACTION_VOLUME_MUTE.equals(action.type)
                || BridgeConfig.ACTION_VOLUME_UNMUTE.equals(action.type)
                || BridgeConfig.ACTION_SHOW_VOLUME_DIALOG.equals(action.type)
                || BridgeConfig.ACTION_CHANGE_VOLUME_STREAM.equals(action.type)) {
            return false;
        }
        // Silent without the DND icon: the app process only has the public setRingerMode, whose
        // external path always creates a manual Zen rule. See RingerController.
        if (BridgeConfig.ACTION_CYCLE_RINGER_MODE.equals(action.type)) {
            return ringer.cycle();
        }
        if (BridgeConfig.ACTION_CHANGE_RINGER_MODE.equals(action.type)) {
            return ringer.set(RingerController.modeFor(action.params.get("mode")));
        }
        if (BridgeConfig.ACTION_ENABLE_BLUETOOTH.equals(action.type)) {
            return ShellKeyInjector.runShell("svc", "bluetooth", "enable");
        }
        if (BridgeConfig.ACTION_DISABLE_BLUETOOTH.equals(action.type)) {
            return ShellKeyInjector.runShell("svc", "bluetooth", "disable");
        }
        if (BridgeConfig.ACTION_TOGGLE_BLUETOOTH.equals(action.type)) {
            return ShellKeyInjector.runShell("svc", "bluetooth",
                    ConstraintEvaluator.isBluetoothOn() ? "disable" : "enable");
        }
        if (BridgeConfig.ACTION_ENABLE_WIFI.equals(action.type)) {
            return ShellKeyInjector.runShell("svc", "wifi", "enable");
        }
        if (BridgeConfig.ACTION_DISABLE_WIFI.equals(action.type)) {
            return ShellKeyInjector.runShell("svc", "wifi", "disable");
        }
        if (BridgeConfig.ACTION_TOGGLE_WIFI.equals(action.type)) {
            return ShellKeyInjector.runShell("svc", "wifi", ConstraintEvaluator.isWifiOn() ? "disable" : "enable");
        }
        if (BridgeConfig.ACTION_TOGGLE_MOBILE_DATA.equals(action.type)) {
            return ShellKeyInjector.runShell("svc", "data", ConstraintEvaluator.isMobileDataOn() ? "disable" : "enable");
        }
        if (BridgeConfig.ACTION_TOGGLE_AIRPLANE_MODE.equals(action.type)) {
            return ShellKeyInjector.runShell("cmd", "connectivity", "airplane-mode",
                    ConstraintEvaluator.isAirplaneModeOn() ? "disable" : "enable");
        }
        if (BridgeConfig.ACTION_TOGGLE_LOCATION.equals(action.type)) {
            return ShellKeyInjector.runShell("cmd", "location", "set-location-enabled",
                    ConstraintEvaluator.isLocationOn() ? "false" : "true");
        }
        // KEYCODE_SLEEP only turns the screen off -- the same thing ACTION_SCREEN_OFF does --
        // and leaves locking to the lock-screen timeout. Accessibility's GLOBAL_ACTION_LOCK_SCREEN
        // locks immediately, which is what the action promises. Left to the app path.
        if (BridgeConfig.ACTION_LOCK_DEVICE.equals(action.type)) {
            return false;
        }
        // No shell primitive reliably simulates a long power-button press across Android versions;
        // only reachable through Accessibility's GLOBAL_ACTION_POWER_DIALOG in the app path.
        if (BridgeConfig.ACTION_POWER_DIALOG.equals(action.type)) {
            return false;
        }
        if (BridgeConfig.ACTION_ENABLE_DND.equals(action.type)) {
            return ShellKeyInjector.runShell("settings", "put", "global", "zen_mode", "1");
        }
        if (BridgeConfig.ACTION_DISABLE_DND.equals(action.type)) {
            return ShellKeyInjector.runShell("settings", "put", "global", "zen_mode", "0");
        }
        if (BridgeConfig.ACTION_TOGGLE_DND.equals(action.type)) {
            return ShellKeyInjector.runShell("settings", "put", "global", "zen_mode",
                    ConstraintEvaluator.isDndOn() ? "0" : "1");
        }
        // KEYCODE_CUT/COPY/PASTE only reach a view that handles those key codes itself, which
        // WebViews and many custom editors don't; the app's AccessibilityNodeInfo ACTION_CUT/
        // COPY/PASTE act on the focused node directly. Left to the app path.
        if (BridgeConfig.ACTION_TEXT_CUT.equals(action.type)
                || BridgeConfig.ACTION_TEXT_COPY.equals(action.type)
                || BridgeConfig.ACTION_TEXT_PASTE.equals(action.type)) {
            return false;
        }
        if (BridgeConfig.ACTION_SWITCH_KEYBOARD.equals(action.type)) {
            return switchToNextInputMethod();
        }
        // showInputMethodPicker() shows UI anchored to the caller's own window; the shell UID has
        // none to anchor it to. Only reachable through InputMethodManager in the app path.
        if (BridgeConfig.ACTION_SHOW_KEYBOARD_PICKER.equals(action.type)) {
            return false;
        }
        if (BridgeConfig.ACTION_LAUNCH_APP.equals(action.type)) {
            String packageName = action.params.get("packageName");
            if (packageName == null || packageName.isEmpty()) {
                return false;
            }
            // monkey is a test tool: it fails on apps with no LAUNCHER category and spams
            // logcat. am start resolves the launcher activity directly.
            return ShellKeyInjector.runShell("am", "start", "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.LAUNCHER", "-p", packageName);
        }
        if (BridgeConfig.ACTION_OPEN_URL.equals(action.type)) {
            String url = action.params.get("url");
            if (url == null || url.isEmpty()) {
                return false;
            }
            return ShellKeyInjector.runShellUnlogged("am", "start", "-a", "android.intent.action.VIEW", "-d", url);
        }
        if (BridgeConfig.ACTION_LAUNCH_VOICE_ASSISTANT.equals(action.type)) {
            return ShellKeyInjector.runShell("am", "start", "-a", "android.intent.action.VOICE_COMMAND");
        }
        if (BridgeConfig.ACTION_LAUNCH_DEVICE_ASSISTANT.equals(action.type)) {
            return ShellKeyInjector.runShell("am", "start", "-a", "android.intent.action.ASSIST");
        }
        if (BridgeConfig.ACTION_OPEN_CAMERA.equals(action.type)) {
            return ShellKeyInjector.runShell("am", "start", "-a", "android.media.action.STILL_IMAGE_CAMERA");
        }
        if (BridgeConfig.ACTION_OPEN_SETTINGS.equals(action.type)) {
            return ShellKeyInjector.runShell("am", "start", "-a", "android.settings.SETTINGS");
        }
        if (BridgeConfig.ACTION_FORCE_STOP_APP.equals(action.type)) {
            String packageName = action.params.get("packageName");
            if (packageName == null || packageName.isEmpty()) {
                return false;
            }
            return ShellKeyInjector.runShell("am", "force-stop", packageName);
        }
        if (BridgeConfig.ACTION_MODIFY_SETTING.equals(action.type)) {
            return modifySetting(action);
        }
        if (BridgeConfig.ACTION_SEND_INTENT.equals(action.type)) {
            return sendIntent(action);
        }
        if (BridgeConfig.ACTION_HTTP_REQUEST.equals(action.type)) {
            return httpRequest(action);
        }
        if (BridgeConfig.ACTION_SHELL_COMMAND.equals(action.type)) {
            String command = action.params.get("command");
            if (command == null || command.isEmpty()) {
                return false;
            }
            return ShellKeyInjector.runShellUnlogged("sh", "-c", command);
        }
        if (BridgeConfig.ACTION_SCREEN_ON.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_WAKEUP);
        }
        if (BridgeConfig.ACTION_SCREEN_OFF.equals(action.type)) {
            return ShellKeyInjector.sendKey(ShellKeyInjector.KEYCODE_SLEEP);
        }
        if (BridgeConfig.ACTION_TOGGLE_SCREEN.equals(action.type)) {
            return ShellKeyInjector.sendKey(ConstraintEvaluator.isScreenOn()
                    ? ShellKeyInjector.KEYCODE_SLEEP : ShellKeyInjector.KEYCODE_WAKEUP);
        }
        if (BridgeConfig.ACTION_TOGGLE_AUTO_ROTATE.equals(action.type)) {
            return ShellKeyInjector.runShell("settings", "put", "system", "accelerometer_rotation",
                    isAutoRotateOn() ? "0" : "1");
        }
        if (BridgeConfig.ACTION_PORTRAIT_MODE.equals(action.type)) {
            return lockToRotation(0);
        }
        if (BridgeConfig.ACTION_LANDSCAPE_MODE.equals(action.type)) {
            return lockToRotation(1);
        }
        if (BridgeConfig.ACTION_CYCLE_ROTATIONS.equals(action.type)) {
            return lockToRotation((currentUserRotation() + 1) % 4);
        }
        if (BridgeConfig.ACTION_CHANGE_BRIGHTNESS.equals(action.type)) {
            Integer value = parseInt(action.params.get("value"));
            if (value == null) {
                return false;
            }
            return setBrightness(value);
        }
        if (BridgeConfig.ACTION_TOGGLE_AUTO_BRIGHTNESS.equals(action.type)) {
            return ShellKeyInjector.runShell("settings", "put", "system", "screen_brightness_mode",
                    isAutoBrightnessOn() ? "0" : "1");
        }
        if (BridgeConfig.ACTION_INCREASE_BRIGHTNESS.equals(action.type)) {
            return setBrightness(currentBrightness() + BRIGHTNESS_STEP);
        }
        if (BridgeConfig.ACTION_DECREASE_BRIGHTNESS.equals(action.type)) {
            return setBrightness(currentBrightness() - BRIGHTNESS_STEP);
        }
        if (BridgeConfig.ACTION_VIBRATE.equals(action.type)) {
            Integer duration = parseInt(action.params.get("duration"));
            if (duration == null || duration <= 0) {
                return false;
            }
            // Android 12+ moved vibration to the "vibrator_manager" service; "vibrator" is gone
            // on some 13+ builds. Try the current API first, fall back to the pre-12 one.
            String clamped = String.valueOf(Math.min(duration, MAX_DELAY_MS));
            return ShellKeyInjector.runShell("cmd", "vibrator_manager", "synced",
                    "-d", "arda_mapper", "oneshot", clamped)
                    || ShellKeyInjector.runShell("cmd", "vibrator", "vibrate", clamped, "arda_mapper");
        }
        // Speaking/playing audio needs a bound engine (TTS engine app, or the audio framework for
        // arbitrary content URIs); this process has no package identity to bind as and no window,
        // the same limitation documented for AudioManager in ShellKeyInjector. Only reachable
        // through the app's TextToSpeech/MediaPlayer in the Accessibility/Activity path.
        if (BridgeConfig.ACTION_TEXT_TO_SPEECH.equals(action.type)
                || BridgeConfig.ACTION_STOP_TEXT_TO_SPEECH.equals(action.type)
                || BridgeConfig.ACTION_PLAY_SOUND.equals(action.type)) {
            return false;
        }
        // The app owns shortcut_scripts_v1 and rewrites bridge_config.json from it; this process
        // only reads that file and has no path to write a pause/resume back into the app's state.
        // Only reachable through ScriptRepository in the app/Accessibility path.
        if (BridgeConfig.ACTION_TOGGLE_MAPPING.equals(action.type)
                || BridgeConfig.ACTION_PAUSE_MAPPING.equals(action.type)
                || BridgeConfig.ACTION_RESUME_MAPPING.equals(action.type)) {
            return false;
        }
        BridgeLog.w("Unknown action type: " + action.type);
        return false;
    }

    private static final int BRIGHTNESS_STEP = 26;

    /** Locking rotation always disables auto-rotate first, matching the quick-settings tile. */
    private static boolean lockToRotation(int rotation) {
        boolean lockedAutoRotate = ShellKeyInjector.runShell("settings", "put", "system", "accelerometer_rotation", "0");
        boolean setRotation = ShellKeyInjector.runShell("settings", "put", "system", "user_rotation", String.valueOf(rotation));
        return lockedAutoRotate && setRotation;
    }

    private static boolean setBrightness(int value) {
        int clamped = Math.max(0, Math.min(255, value));
        boolean manualMode = ShellKeyInjector.runShell("settings", "put", "system", "screen_brightness_mode", "0");
        boolean setValue = ShellKeyInjector.runShell("settings", "put", "system", "screen_brightness", String.valueOf(clamped));
        return manualMode && setValue;
    }

    private static boolean isAutoRotateOn() {
        return "1".equals(ShellKeyInjector.runShellForOutput("settings", "get", "system", "accelerometer_rotation").trim());
    }

    private static boolean isAutoBrightnessOn() {
        return "1".equals(ShellKeyInjector.runShellForOutput("settings", "get", "system", "screen_brightness_mode").trim());
    }

    private static int currentBrightness() {
        Integer value = parseInt(ShellKeyInjector.runShellForOutput("settings", "get", "system", "screen_brightness").trim());
        return value != null ? value : 128;
    }

    private static int currentUserRotation() {
        Integer value = parseInt(ShellKeyInjector.runShellForOutput("settings", "get", "system", "user_rotation").trim());
        return value != null ? value : 0;
    }

    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean modifySetting(BridgeConfig.Action action) {
        String namespace = action.params.get("namespace");
        String key = action.params.get("key");
        String value = action.params.get("value");
        if (key == null || key.isEmpty() || value == null
                || !("system".equals(namespace) || "secure".equals(namespace) || "global".equals(namespace))) {
            return false;
        }
        return ShellKeyInjector.runShellUnlogged("settings", "put", namespace, key, value);
    }

    /** "key=value" pairs separated by ';', each sent as a String extra via `am broadcast --es`. */
    private static boolean sendIntent(BridgeConfig.Action action) {
        String intentAction = action.params.get("action");
        if (intentAction == null || intentAction.isEmpty()) {
            return false;
        }
        List<String> command = new ArrayList<>();
        command.add("am");
        command.add("broadcast");
        command.add("-a");
        command.add(intentAction);
        String uri = action.params.get("uri");
        if (uri != null && !uri.isEmpty()) {
            command.add("-d");
            command.add(uri);
        }
        String extras = action.params.get("extras");
        if (extras != null && !extras.isEmpty()) {
            for (String pair : extras.split(";")) {
                int index = pair.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                command.add("--es");
                command.add(pair.substring(0, index).trim());
                command.add(pair.substring(index + 1).trim());
            }
        }
        return ShellKeyInjector.runShellUnlogged(command.toArray(new String[0]));
    }

    private static boolean httpRequest(BridgeConfig.Action action) {
        String method = action.params.get("method");
        String url = action.params.get("url");
        String body = action.params.get("body");
        if (method == null || method.isEmpty() || url == null || url.isEmpty()) {
            return false;
        }
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setRequestMethod(method.toUpperCase(java.util.Locale.ROOT));
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            String upperMethod = method.toUpperCase(java.util.Locale.ROOT);
            if (body != null && !body.isEmpty() && ("POST".equals(upperMethod) || "PUT".equals(upperMethod))) {
                connection.setDoOutput(true);
                try (java.io.OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            // The URL can carry an API token in its query string; log only the failure kind.
            BridgeLog.w("http_request failed: " + e.getClass().getSimpleName());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Cycles to the next enabled input method, mirroring what a hardware "next keyboard" key does.
     * Reads the enabled set and current default from Settings (the same source {@code ime list -s}
     * and {@code default_input_method} always agree with) since this process has no IMM either.
     */
    private boolean switchToNextInputMethod() {
        String current = ShellKeyInjector.runShellForOutput(
                "settings", "get", "secure", "default_input_method").trim();
        List<String> enabled = new ArrayList<>();
        for (String line : ShellKeyInjector.runShellForOutput("ime", "list", "-s").split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                enabled.add(trimmed);
            }
        }
        if (enabled.size() < 2) {
            return false;
        }
        int index = enabled.indexOf(current);
        String next = enabled.get(index < 0 ? 0 : (index + 1) % enabled.size());
        return ShellKeyInjector.runShell("ime", "set", next);
    }

    /** Capped to match the app-side executor, so a bad config cannot stall the gesture thread. */
    private boolean sleepFor(String durationParam) {
        long duration;
        try {
            duration = durationParam == null ? 0L : Long.parseLong(durationParam.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        duration = Math.max(0L, Math.min(MAX_DELAY_MS, duration));
        try {
            Thread.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---- control socket ----

    @Override
    public void onReloadRequested() {
        configLastModified = 0L;
        reloadConfigIfChanged();
    }

    @Override
    public void onShutdownRequested() {
        BridgeLog.i("Shutdown requested over control socket");
        new Thread(() -> {
            try {
                if (torch.isOn()) {
                    torch.set(false);
                }
            } catch (Throwable ignored) {
                // Shutting down regardless.
            }
            if (reader != null) {
                reader.stop();
            }
            if (server != null) {
                server.stop();
            }
            BridgeLog.i("Bridge exiting");
            // The user asked for Advanced Mode to stop, so leave nothing runnable behind. Only the
            // shell UID can clear its own directory; the app cannot reach it at all.
            deleteBaseDir();
            System.exit(0);
        }, "arda-bridge-shutdown").start();
    }

    private static void deleteBaseDir() {
        File base = new File(BridgeProtocol.BASE_DIR);
        File[] contents = base.listFiles();
        if (contents != null) {
            for (File file : contents) {
                file.delete();
            }
        }
        base.delete();
    }
}
