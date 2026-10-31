package com.example.bridge;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    // 5 missed 1 s polls: the app's rewrite never removes the file, so this only trips on uninstall.
    private final MissingConfigWatch missingConfig = new MissingConfigWatch(5);
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
    private BrightnessScale brightnessScale;
    private final BridgeModes modes;
    private final ConstraintEvaluator constraints;
    /** Script id -> elapsedRealtime of its last run, for the cooldown constraint. */
    private final Map<String, Long> lastRunByScript = new ConcurrentHashMap<>();
    private PressTimingMachine pressTiming;
    private final ChordTracker chords = new ChordTracker();
    private EventServer server;
    private GeteventReader reader;

    private volatile BridgeConfig config;
    private volatile long configLastModified;
    private long lastFeedbackRunId;
    private final java.util.concurrent.atomic.AtomicBoolean feedbackWakePending =
            new java.util.concurrent.atomic.AtomicBoolean();

    @Override
    public void onFeedbackWakeRequested() {
        BridgeConfig current = config;
        if (current == null || !current.capsuleFeedbackEnabled || !current.globallyEnabled
                || !current.packageName.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")
                || !feedbackWakePending.compareAndSet(false, true)) return;
        scheduler.execute(() -> {
            try {
                // Fixed internal component, no payload/credentials in the intent or process log.
                Process process = new ProcessBuilder("am", "startservice", "-a",
                        "com.example.capsule.SHOW_FEEDBACK", "-n",
                        current.packageName + "/com.example.capsule.CapsuleFeedbackService")
                        .redirectOutput(new File("/dev/null"))
                        .redirectError(new File("/dev/null")).start();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroy();
                }
            } catch (Exception ignored) {
                BridgeLog.w("Capsule service wake unavailable");
            } finally {
                feedbackWakePending.set(false);
            }
        });
    }

    private BridgeMain(File configFile) {
        this.configFile = configFile;
        // Sibling of the disposable bridge directory, created by the app before config publication.
        this.modes = BridgeModes.open(configFile);
        this.constraints = new ConstraintEvaluator(torch, modes);
    }

    /** This bridge's working directory, from start.sh's --base; older scripts omit it. */
    private static volatile String baseDir = BridgeProtocol.BASE_DIR;

    static String baseDir() {
        return baseDir;
    }

    public static void main(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--base".equals(args[i]) && BridgeProtocol.isValidBaseDir(args[i + 1])) {
                baseDir = args[i + 1];
            }
        }
        File base = new File(baseDir);
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
        brightnessScale = BrightnessSettings.scale(context);
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
        try (FileWriter writer = new FileWriter(new File(baseDir, "bridge.pid"), false)) {
            writer.write(String.valueOf(android.os.Process.myPid()));
        } catch (Exception e) {
            BridgeLog.w("Could not write pid file: " + e);
        }
    }

    private void reloadConfigIfChanged() {
        try {
            long modified = configFile.lastModified();
            if (missingConfig.shouldShutDown(configFile.exists())) {
                BridgeLog.w("Config file gone (app uninstalled?); shutting down");
                shutDown();
                return;
            }
            if (modified == configLastModified || modified == 0L) {
                return;
            }
            BridgeConfig updated = BridgeConfig.read(configFile);
            configLastModified = modified;
            if (updated.port != config.port || !updated.token.equals(config.token)) {
                BridgeLog.w("Control port/token changed in config; a bridge restart is required to apply it");
            }
            config = updated;
            // Same lock as onKeyEvent, so a reset never lands between a chord decision and its commands.
            synchronized (chords) {
                chords.reset();
                pressTiming.reset();
            }
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
        // Lock order: chords, then pressTiming's own lock. onGesture never takes chords.
        synchronized (chords) {
            for (ChordTracker.Command command : chords.onEvent(key.androidKeyCode, down, now, current.chords(), current.soloKeys())) {
                if (command.kind == ChordTracker.CANCEL) {
                    pressTiming.cancel(command.keyCode);
                } else {
                    pressTiming.onEvent(command.keyCode, command.down, now,
                            current.doublePressWindowFor(command.keyCode), current.longPressThresholdFor(command.keyCode));
                }
            }
        }
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
        // Cooldowns use the physical gesture's time, even when earlier actions delay this task.
        long now = SystemClock.elapsedRealtime();
        actionExecutor.execute(() -> {
            // The previous gesture's mode pulses may still be playing after its trigger report.
            // Wait before matching, so mode_is and this gesture's own haptic keep their order.
            if (!modes.awaitBuzz()) return;
            // Evaluate after earlier gestures finish, including a queued cycle_mode. Otherwise a
            // rapid second key could select the old mode while the first action is still queued.
            List<BridgeConfig.Script> matched = new ArrayList<>();
            for (BridgeConfig.Script script : current.scriptsFor(keyCode, pressType)) {
                if (!constraintsSatisfied(script, current.foregroundReadable) || !cooldownAllows(script, now)) {
                    continue;
                }
                lastRunByScript.put(script.id, now);
                matched.add(script);
            }
            // Select all shortcuts before running any: a mode change in this gesture must not
            // make another mode's shortcut on the SAME gesture match as well.
            List<Long> runIds = new ArrayList<>();
            for (BridgeConfig.Script script : matched) {
                long runId = lastFeedbackRunId = Math.max(lastFeedbackRunId + 1, SystemClock.uptimeMillis());
                runIds.add(runId);
                if (current.capsuleFeedbackEnabled) {
                    server.broadcastPending(System.currentTimeMillis(), keyCode, pressType, script.id,
                            BridgeProtocol.encodeActionResults(typesOf(script.actions), new boolean[script.actions.size()]), runId);
                }
            }
            for (int i = 0; i < matched.size(); i++) {
                runScript(matched.get(i), keyCode, pressType, runIds.get(i));
                // The trigger is already out. Hold the queue so the next script cannot replace the pulses.
                if (!modes.awaitBuzz()) return;
            }
        });
    }

    private boolean constraintsSatisfied(BridgeConfig.Script script, boolean foregroundReadable) {
        for (BridgeConfig.Constraint constraint : script.constraints) {
            if (!constraints.isSatisfied(constraint, foregroundReadable)) {
                return false;
            }
        }
        return true;
    }

    /** The per-script half of the cooldown constraint. Mirrors HardwareKeyTriggerCoordinator.passesCooldown. */
    private boolean cooldownAllows(BridgeConfig.Script script, long now) {
        for (BridgeConfig.Constraint constraint : script.constraints) {
            if (BridgeConfig.CONSTRAINT_COOLDOWN.equals(constraint.type)
                    && !ConstraintNegation.cooldownAllows(lastRunByScript.get(script.id),
                            constraint.params, now)) {
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
    private void runScript(BridgeConfig.Script script, int keyCode, String pressType, long runId) {
        // One result per configured action for the app's run log; a "repeat_previous" counts as ok
        // only if every repetition was.
        // The bridge owns the trigger haptic: the app process may be dead (OEM kill) and could not
        // buzz on the TRIGGER broadcast. Once per press; the app no longer vibrates for these.
        BridgeConfig current = config;
        if (current != null && current.shouldVibrate(script)) {
            long duration = current.hapticDurationFor(script);
            vibrate(duration);
            long modePause = BridgeModes.triggerPause(script.actions, duration);
            if (modePause > 0 && !sleepFor(Long.toString(modePause))) return;
        }
        boolean[] ok = new boolean[script.actions.size()];
        java.util.Arrays.fill(ok, true);
        runActions(script.actions, (index, action, success) -> {
            ok[index] &= success;
            logActionResult(script, keyCode, pressType, action, success);
        });
        // One broadcast per physical press, not per action, mirroring the app process's own
        // per-script counting in HardwareKeyTriggerCoordinator.executeActions — otherwise a
        // multi-action script vibrates and increments "Triggers today" once per action.
        server.broadcastTrigger(System.currentTimeMillis(), keyCode, pressType, script.id, true,
                BridgeProtocol.encodeActionResults(typesOf(script.actions), ok), runId);
    }

    private static List<String> typesOf(List<BridgeConfig.Action> actions) {
        List<String> types = new ArrayList<>();
        for (BridgeConfig.Action action : actions) {
            types.add(action.type);
        }
        return types;
    }

    /** Runs one chain, expanding "repeat_previous", and reports how many actions succeeded. */
    private int runActions(List<BridgeConfig.Action> actions, ActionReporter reporter) {
        int ran = 0;
        BridgeConfig.Action previous = null;
        for (int index = 0; index < actions.size(); index++) {
            BridgeConfig.Action action = actions.get(index);
            if (BridgeConfig.ACTION_REPEAT_PREVIOUS.equals(action.type)) {
                Integer count = parseInt(action.params.get("count"));
                int clamped = count == null ? 0 : Math.max(0, Math.min(MAX_REPEAT_COUNT, count));
                if (previous == null || clamped <= 0) {
                    reporter.report(index, action, false);
                } else {
                    for (int i = 0; i < clamped; i++) {
                        boolean success = executeAction(previous);
                        ran += success ? 1 : 0;
                        reporter.report(index, previous, success);
                    }
                }
                continue;
            }
            boolean success = executeAction(action);
            ran += success ? 1 : 0;
            reporter.report(index, action, success);
            previous = action;
        }
        return ran;
    }

    /** [index] is the configured action's position; a repeat reports once per repetition. */
    private interface ActionReporter {
        void report(int index, BridgeConfig.Action action, boolean success);
    }

    /**
     * The editor's Run button and the app's automations, so both do what a real press does rather
     * than what the app process alone can do. No trigger broadcast: the app counts and buzzes for
     * an automation itself, and a test press must not do either. The reply ends with the same
     * per-action field a TRIGGER carries, for the automation's run log.
     */
    @Override
    public String onTestActionRequested(String payloadJson) {
        long start = System.nanoTime();
        ActionTestResult result;
        try {
            List<BridgeConfig.Action> actions = BridgeConfig.parseActions(payloadJson);
            if (actions.size() != 1) throw new IllegalArgumentException();
            BridgeConfig.Action action = actions.get(0);
            switch (action.type) {
                case BridgeConfig.ACTION_SHELL_COMMAND:
                    result = ShellKeyInjector.shellCommand(action.params, true);
                    break;
                case BridgeConfig.ACTION_HTTP_REQUEST:
                    result = HttpAction.execute(action.params);
                    break;
                default:
                    result = ActionTestResult.outcome(executeAction(action), start);
            }
        } catch (Exception ignored) {
            result = ActionTestResult.outcome(false, start);
        }
        try {
            return result.toWire();
        } catch (Exception ignored) {
            return "{}";
        }
    }

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
        boolean[] ok = new boolean[actions.size()];
        java.util.Arrays.fill(ok, true);
        int ran = runActions(actions, (index, action, success) -> {
            ok[index] &= success;
            BridgeLog.i("Run from the app: action=" + action.type + " success=" + success);
        });
        modes.awaitBuzz();
        return ran + "\t" + actions.size() + "\t" + BridgeProtocol.encodeActionResults(typesOf(actions), ok);
    }

    /**
     * send_message's "Send automatically". The payload is the chat app and the message the app just
     * typed into it: user data, so only the outcome word is logged. Runs on this control connection's
     * own thread, so a press never waits behind a script's actions or holds them up.
     */
    @Override
    public String onPressSendRequested(String payloadJson) {
        String result;
        try {
            org.json.JSONObject payload = new org.json.JSONObject(payloadJson);
            String packageName = payload.optString("packageName", "");
            String text = payload.optString("text", "");
            // The recipient as the app knows it, matched against the chat's header; never logged.
            String contactName = payload.optString("contactName", "");
            String number = payload.optString("number", "");
            result = packageName.isEmpty() || text.trim().isEmpty() ? "bad-request"
                    : SendButton.press(packageName, text, contactName, number);
        } catch (Throwable t) {
            result = "error";
        }
        BridgeLog.i("press_send: " + result);
        return result;
    }

    @Override
    public boolean onShowAssistantRequested() {
        return AssistantLauncher.show();
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
    private static boolean vibrate(long ms) {
        // Android 12+ moved vibration to the "vibrator_manager" service; "vibrator" is gone
        // on some 13+ builds. Try the current API first, fall back to the pre-12 one.
        String clamped = String.valueOf(ms);
        return ShellKeyInjector.runShell("cmd", "vibrator_manager", "synced",
                "-d", "arda_mapper", "oneshot", clamped)
                || ShellKeyInjector.runShell("cmd", "vibrator", "vibrate", clamped, "arda_mapper");
    }

    private boolean executeAction(BridgeConfig.Action action) {
        // A cycle_mode earlier in this chain started its pulses without blocking the trigger.
        // The next action waits here so it cannot replace that waveform.
        if (!modes.awaitBuzz()) return false;
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
        if (BridgeConfig.ACTION_CYCLE_MODE.equals(action.type)) {
            return modes.cycle(action.params, server::broadcastMode) >= 1;
        }
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
        // Through NotificationManager: it owns zen_mode and reverts a direct `settings put`
        // (verified on ColorOS 2026-10-23: the write "succeeded" and DND stayed off).
        if (BridgeConfig.ACTION_ENABLE_DND.equals(action.type)) {
            return ShellKeyInjector.runShell("cmd", "notification", "set_dnd", "priority");
        }
        if (BridgeConfig.ACTION_DISABLE_DND.equals(action.type)) {
            return ShellKeyInjector.runShell("cmd", "notification", "set_dnd", "off");
        }
        if (BridgeConfig.ACTION_TOGGLE_DND.equals(action.type)) {
            return ShellKeyInjector.runShell("cmd", "notification", "set_dnd",
                    ConstraintEvaluator.isDndOn() ? "off" : "priority");
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
            return AppLauncher.launch(action.params.get("packageName"), new AppLauncher.Commands() {
                public String output(String... command) { return ShellKeyInjector.runShellForOutput(command); }
                public boolean start(String... command) { return ShellKeyInjector.runShellUnlogged(command); }
            });
        }
        if (BridgeConfig.ACTION_OPEN_URL.equals(action.type)) {
            String url = action.params.get("url");
            if (url == null || url.isEmpty()) {
                return false;
            }
            return ShellKeyInjector.runShellUnlogged("am", "start", "-a", "android.intent.action.VIEW", "-d", url);
        }
        // Open the active assistant without depending on a live app subscriber. The launcher
        // rejects KeySnap's own voice service; the app then discovers an alternative instead.
        if (BridgeConfig.ACTION_LAUNCH_VOICE_ASSISTANT.equals(action.type)
                || BridgeConfig.ACTION_LAUNCH_DEVICE_ASSISTANT.equals(action.type)) {
            return AssistantLauncher.show();
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
            return ShellKeyInjector.shellCommand(action.params, false).ok;
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
            return setBrightness(brightnessScale.fromLegacy(value));
        }
        if (BridgeConfig.ACTION_TOGGLE_AUTO_BRIGHTNESS.equals(action.type)) {
            return ShellKeyInjector.runShell("settings", "put", "system", "screen_brightness_mode",
                    isAutoBrightnessOn() ? "0" : "1");
        }
        if (BridgeConfig.ACTION_INCREASE_BRIGHTNESS.equals(action.type)) {
            return adjustBrightness(1);
        }
        if (BridgeConfig.ACTION_DECREASE_BRIGHTNESS.equals(action.type)) {
            return adjustBrightness(-1);
        }
        if (BridgeConfig.ACTION_VIBRATE.equals(action.type)) {
            Integer duration = parseInt(action.params.get("duration"));
            if (duration == null || duration <= 0) {
                return false;
            }
            return vibrate(Math.min(duration, MAX_DELAY_MS));
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
        // Posted from the app so it carries KeySnap's name, the "Shortcut notifications" channel
        // the user can mute, and the tap target; shell's `cmd notification post` would show it as
        // coming from Shell with none of those. See ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE.
        if (BridgeConfig.ACTION_SHOW_NOTIFICATION.equals(action.type)) {
            return false;
        }
        // The picked image is a content:// URI whose read grant belongs to the app's UID; this
        // shell-UID process cannot open it. Only reachable through WallpaperManager in the app path.
        if (BridgeConfig.ACTION_SET_WALLPAPER.equals(action.type)) {
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
        // Replying needs a bound NotificationListenerService and the chat app's PendingIntent;
        // this process has neither. Left to the app path. See ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE.
        if (BridgeConfig.ACTION_REPLY_TO_NOTIFICATION.equals(action.type)) {
            return false;
        }
        // Choosing how to open the chat needs the app's PackageManager (the default SMS app, which
        // apps take an smsto: link) and the SIM country for the number; the activity is started
        // from the app process. Left to the app path. See ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE.
        // Its "Send automatically" reaches this process only as CMD_PRESS_SEND from the app.
        if (BridgeConfig.ACTION_SEND_MESSAGE.equals(action.type)) {
            return false;
        }
        BridgeLog.w("Unknown action type: " + action.type);
        return false;
    }

    /** Locking rotation always disables auto-rotate first, matching the quick-settings tile. */
    private static boolean lockToRotation(int rotation) {
        boolean lockedAutoRotate = ShellKeyInjector.runShell("settings", "put", "system", "accelerometer_rotation", "0");
        boolean setRotation = ShellKeyInjector.runShell("settings", "put", "system", "user_rotation", String.valueOf(rotation));
        return lockedAutoRotate && setRotation;
    }

    private boolean setBrightness(int value) {
        if (!brightnessScale.contains(currentBrightness())) return false;
        boolean manualMode = ShellKeyInjector.runShell("settings", "put", "system", "screen_brightness_mode", "0");
        if (!manualMode) return false;
        boolean setValue = ShellKeyInjector.runShell("settings", "put", "system", "screen_brightness", String.valueOf(value));
        return manualMode && setValue;
    }

    private boolean adjustBrightness(int direction) {
        int current = currentBrightness();
        return brightnessScale.contains(current) && setBrightness(brightnessScale.step(current, direction));
    }

    private static boolean isAutoRotateOn() {
        return "1".equals(ShellKeyInjector.runShellForOutput("settings", "get", "system", "accelerometer_rotation").trim());
    }

    private static boolean isAutoBrightnessOn() {
        return "1".equals(ShellKeyInjector.runShellForOutput("settings", "get", "system", "screen_brightness_mode").trim());
    }

    private static int currentBrightness() {
        Integer value = parseInt(ShellKeyInjector.runShellForOutput("settings", "get", "system", "screen_brightness").trim());
        return value != null ? value : -1;
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
        try {
            return ShellKeyInjector.runShellUnlogged(ActionPayloads.settingCommand(action.params));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** "key=value" pairs separated by ';', each sent as a String extra via `am broadcast --es`. */
    private static boolean sendIntent(BridgeConfig.Action action) {
        try {
            return ShellKeyInjector.runShellUnlogged(ActionPayloads.intentCommand(action.params));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean httpRequest(BridgeConfig.Action action) {
        return HttpAction.execute(action.params).ok;
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
        shutDown();
    }

    private void shutDown() {
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
        File base = new File(baseDir);
        File[] contents = base.listFiles();
        if (contents != null) {
            for (File file : contents) {
                file.delete();
            }
        }
        base.delete();
    }
}
