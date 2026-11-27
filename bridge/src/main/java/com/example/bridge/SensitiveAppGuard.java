package com.example.bridge;

import android.app.ActivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces Settings → Sensitive apps. Registered as the system's activity controller (what
 * {@code am monitor} uses), it hears every activity launch before it happens and turns developer
 * options and KeySnap's Accessibility entry off for a listed app, then back on once another app
 * comes up. The decisions are {@link SensitiveApps}'; this class only runs them.
 *
 * <p>Switching apps with no launch (the home gesture, recents) never reaches the controller, so a
 * process observer rings on every change in which processes have activities on screen and the task
 * in front is read again ({@link SensitiveApps#onFront}). While that works, the controller acts
 * only for listed apps, before their launch; leaving one waits for a reading.
 *
 * <p>Safety: every launch is answered "allow", always, within {@link #LAUNCH_BUDGET_MS} however the
 * shell behaves, because system_server waits for the answer while holding its window lock. What was
 * turned off is noted in a file in this bridge's own directory before the write, so a bridge killed
 * mid-way (reboot included) puts it back on its next start, and exactly that, nothing the user
 * changed. Android drops the controller when this process dies: its next launch call gets
 * DeadObjectException at once and system_server clears the slot.
 *
 * <p>Wireless debugging is the one switch that is never put back: a listed app with
 * {@link SensitiveApps#WIFI} in front ends this bridge ({@link #leave}). After its other switches,
 * the app is told, adb_wifi_enabled goes to 0 and the process exits with nothing restored and its
 * directory kept, so the marker brings the rest back when the user sets Advanced Mode up again.
 * That is the only write of adb_wifi_enabled, never to 1; adb_enabled is never written. While adbd
 * also listens on TCP ("Keep running without Wi-Fi") the 0 is written after the step and the bridge
 * stays: the app is told "aside" as for any listed app, and nothing ends.
 */
final class SensitiveAppGuard extends Binder {

    interface Reporter {
        void report(String packageName, boolean steppedAside);

        /**
         * This bridge is about to turn wireless debugging off and end for [packageName]: tell the live
         * app, flushed before returning, and let go of what the process holds outside itself.
         */
        void leaving(String packageName);
    }

    private static final String DESCRIPTOR = "android.app.IActivityController";
    /** A listed app's launch waits for its switches, never longer than this. */
    private static final long LAUNCH_BUDGET_MS = 400L;
    private static final int SHELL_TIMEOUT_S = 2;
    /** The capture keeps the tail of longer lines only, which would not be the whole list. */
    private static final int MAX_VALUE_CHARS = 2000;
    private static final String MARKER_NAME = "sensitive_restore";
    private static final String OBSERVER = "android.app.IProcessObserver";
    /** Each launch report is checked against the task in front this much later: a blocked launch is put right. */
    private static final long CONFIRM_MS = 1_000L;

    private final File marker;
    private final Reporter reporter;
    private final SensitiveApps state = new SensitiveApps(); // worker thread only
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "arda-bridge-sensitive");
        thread.setDaemon(true);
        return thread;
    });

    private final int txStarting = code("activityStarting", 0);
    private final int txResuming = code("activityResuming", 1);
    private final int txCrashed = code("appCrashed", 2);
    private final int txEarlyAnr = code("appEarlyNotResponding", 3);
    private final int txAnr = code("appNotResponding", 4);
    private final int txSystemAnr = code("systemNotResponding", 5);

    private final int txForegroundActivities = code(OBSERVER, "onForegroundActivitiesChanged", 0);
    /**
     * Reads the task in front, never on the worker: getTasks waits for the window lock, which
     * system_server holds during a launch report while that report waits for the worker.
     */
    private final ScheduledExecutorService front = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "arda-bridge-front");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean readQueued = new AtomicBoolean();
    /** Launch reports so far, counted on the binder thread: a reading taken before the latest one is never applied. */
    private final AtomicLong launches = new AtomicLong();
    private volatile boolean tracking;
    private volatile Object activityManager;
    private volatile Method getTasks;
    private Binder observer; // Worker thread; kept referenced while registered.

    private volatile Map<String, Integer> apps = Collections.emptyMap();
    private volatile String packageName = "";
    private volatile boolean idle = true;
    /** Set first in {@link #leave}: from then on nothing is put back, not even by the shutdown hook. */
    private volatile boolean leaving;

    // Worker thread only.
    private String removedEntries = "";
    private String reported;
    private boolean registerTried;
    private boolean nonceSeen;
    private long lastNonce;

    SensitiveAppGuard(File baseDir, Reporter reporter) {
        this.marker = new File(baseDir, MARKER_NAME);
        this.reporter = reporter;
        attachInterface(null, DESCRIPTOR);
    }

    /** Before anything else: puts back what a bridge that died while stepped aside left off. */
    void restoreLeftovers(String ownPackage) {
        Properties saved = new Properties();
        try (FileInputStream in = new FileInputStream(marker)) {
            saved.load(in);
        } catch (Exception missing) {
            return;
        }
        int bits = parseInt(saved.getProperty("changed"));
        String entries = SensitiveApps.ownEntries(saved.getProperty("a11y", ""), ownPackage);
        BridgeLog.i("Sensitive apps: restoring switches left off (" + bits + ")");
        await(worker.submit(() -> {
            removedEntries = entries;
            for (int bit : SensitiveApps.BITS) {
                if ((bits & bit) != 0) state.hidden(bit, true);
            }
            run(state.onConfig(Collections.emptyMap()));
        }), 5_000);
    }

    /** Every config read. The first non-empty list starts watching launches; until then nothing is registered. */
    void onConfig(BridgeConfig config) {
        apps = config.sensitiveApps;
        packageName = config.packageName;
        long nonce = config.sensitiveRestoreNonce;
        execute(() -> {
            boolean restoreNow = nonceSeen && nonce != lastNonce;
            nonceSeen = true;
            lastNonce = nonce;
            run(restoreNow ? state.onRestoreNow() : state.onConfig(apps));
            if (!registerTried && !apps.isEmpty()) {
                registerTried = true;
                register();
            }
        });
    }

    /**
     * Shutdown: everything back on, waiting at most [waitMs]. Later launches change nothing. A no-op
     * once {@link #leave} began: the app that asked for wireless debugging off is still in front.
     */
    void restoreAll(long waitMs) {
        if (leaving) return;
        apps = Collections.emptyMap();
        try {
            await(worker.submit(() -> run(state.onConfig(Collections.emptyMap()))), waitMs);
        } catch (Exception alreadyStopped) {
            // The worker is gone; the marker file still covers the next start.
        }
    }

    // ---- activity controller ----

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == txStarting || code == txResuming) {
            String pkg = null;
            try {
                data.enforceInterface(DESCRIPTOR);
                if (code == txStarting && data.readInt() != 0) {
                    android.content.Intent.CREATOR.createFromParcel(data);
                }
                pkg = data.readString();
            } catch (Throwable unreadable) {
                // Still allowed; nothing is switched for a call that cannot be read.
            }
            if (pkg != null) onForeground(pkg);
            return answer(reply, 1);
        }
        if (code == txCrashed) return answer(reply, 1); // the normal crash dialog
        if (code == txEarlyAnr || code == txAnr) return answer(reply, 0); // the normal ANR handling
        if (code == txSystemAnr) return answer(reply, -1); // the watchdog's normal kill, never "keep waiting"
        return super.onTransact(code, data, reply, flags);
    }

    private static boolean answer(Parcel reply, int value) {
        if (reply != null) {
            reply.writeNoException();
            reply.writeInt(value);
        }
        return true;
    }

    /** Binder thread, inside system_server's launch: bounded whatever happens. */
    private void onForeground(String pkg) {
        boolean listed = apps.containsKey(pkg);
        if (SensitiveApps.PASS_THROUGH.contains(pkg) || (!listed && idle)) return;
        boolean tracked = tracking;
        if (tracked) {
            launches.incrementAndGet();
            confirmLater();
        }
        Future<?> step;
        try {
            step = worker.submit(() -> run(state.onLaunch(pkg, apps, tracked)));
        } catch (Exception stopped) {
            return;
        }
        // The listed app reads the switches as it starts, so its launch waits; putting them back can follow.
        if (!listed) return;
        try {
            step.get(LAUNCH_BUDGET_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException slow) {
            BridgeLog.w("Sensitive apps: switches not settled within " + LAUNCH_BUDGET_MS + " ms; launch let through");
        } catch (Exception ignored) {
            // Let the launch through regardless.
        }
    }

    private void register() {
        Object am;
        try {
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "activity");
            am = Class.forName("android.app.IActivityManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, binder);
            Object controller = Class.forName(DESCRIPTOR + "$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, this);
            am.getClass().getMethod("setActivityController", Class.forName(DESCRIPTOR), boolean.class)
                    .invoke(am, controller, false);
            BridgeLog.i("Sensitive apps: watching app launches");
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            BridgeLog.w("Sensitive apps unavailable: cannot watch app launches (" + cause.getClass().getSimpleName() + ")");
            return;
        }
        watchFront(am);
    }

    /**
     * The second source. IProcessObserver needs SET_ACTIVITY_WATCHER, like the controller; the
     * reading needs getTasks to see other apps' tasks (REAL_GET_TASKS). Fails open: without it only
     * launches are seen, as before.
     */
    private void watchFront(Object am) {
        try {
            Method tasks = am.getClass().getMethod("getTasks", int.class);
            List<?> probe = (List<?>) tasks.invoke(am, 1);
            if (probe == null || probe.isEmpty()) throw new IllegalStateException();
            activityManager = am;
            getTasks = tasks;
            Binder doorbell = new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                    if (code != txForegroundActivities) return super.onTransact(code, data, reply, flags);
                    ringFront(); // Oneway: nothing to answer, and it returns at once.
                    return true;
                }
            };
            doorbell.attachInterface(null, OBSERVER);
            Object proxy = Class.forName(OBSERVER + "$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, doorbell);
            am.getClass().getMethod("registerProcessObserver", Class.forName(OBSERVER)).invoke(am, proxy);
            observer = doorbell;
            tracking = true;
            BridgeLog.i("Sensitive apps: watching app switches");
            ringFront(); // A listed app may be in front already.
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            BridgeLog.w("Sensitive apps: cannot watch app switches (" + cause.getClass().getSimpleName() + "); launches only");
        }
    }

    /** Something changed on screen: read the task in front soon. A burst collapses into one reading; never blocks. */
    private void ringFront() {
        if (!readQueued.compareAndSet(false, true)) return;
        try {
            front.execute(this::readFront);
        } catch (Throwable stopped) {
            readQueued.set(false);
        }
    }

    private void confirmLater() {
        try {
            front.schedule(this::ringFront, CONFIRM_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable stopped) {
            // Shutting down.
        }
    }

    /**
     * Front thread. "In front" = the first task getTasks returns (the focused, visible one on
     * Android 13+; with the screen off or locked still the last one used): its top activity's
     * package, or the listed app that opened that task.
     */
    private void readFront() {
        readQueued.set(false);
        if (apps.isEmpty() && idle) return;
        long seen = launches.get();
        String top;
        String base;
        try {
            List<?> tasks = (List<?>) getTasks.invoke(activityManager, 1);
            if (tasks == null || tasks.isEmpty()) return;
            ActivityManager.RunningTaskInfo task = (ActivityManager.RunningTaskInfo) tasks.get(0);
            if (task.topActivity == null) return;
            top = task.topActivity.getPackageName();
            base = task.baseActivity == null ? null : task.baseActivity.getPackageName();
        } catch (Throwable unreadable) {
            return;
        }
        execute(() -> {
            // A launch reported meanwhile is newer than this reading, and its own check follows.
            if (seen == launches.get()) run(state.onFront(top, base, apps));
        });
    }

    private static int code(String method, int index) {
        return code(DESCRIPTOR, method, index);
    }

    /** The AIDL transaction code; AOSP's declaration order when the stub's constant cannot be read. */
    private static int code(String descriptor, String method, int index) {
        try {
            Field field = Class.forName(descriptor + "$Stub").getDeclaredField("TRANSACTION_" + method);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Throwable t) {
            return IBinder.FIRST_CALL_TRANSACTION + index;
        }
    }

    // ---- switches (worker thread only) ----

    private void run(int[] step) {
        String leavingFor = state.leavingFor(); // Decided with [step], which is computed before this runs.
        boolean staying = leavingFor != null
                && SensitiveApps.adbOverTcp(ShellKeyInjector.runShellForOutput("getprop", "service.adb.tcp.port"));
        if (staying) {
            state.stayed();
            leavingFor = null;
        }
        try {
            apply(step);
        } catch (Throwable t) {
            BridgeLog.w("Sensitive apps: step failed (" + t.getClass().getSimpleName() + "); putting switches back");
            apply(new int[] {0, state.changed()});
        } finally {
            saveMarker(state.changed());
            if (leavingFor == null) report(); // Otherwise the app hears "leaving", not "aside".
            idle = state.idle();
        }
        if (staying) {
            BridgeLog.i("Sensitive apps: turning wireless debugging off; adbd is on TCP, bridge stays");
            if (!ShellKeyInjector.runShellUnlogged(SHELL_TIMEOUT_S, false, SensitiveApps.wirelessDebuggingOff()).ok) {
                BridgeLog.w("Sensitive apps: could not turn wireless debugging off");
            }
        }
        if (leavingFor != null) leave(leavingFor);
    }

    /**
     * Worker thread, after [pkg]'s step settled its other switches (or timed out, as any step) and
     * noted them in the marker. In this order: nothing is restored from here on, the app is told
     * (flushed), wireless debugging goes off, the process ends. The base directory and the marker
     * stay, so the next bridge start puts the other switches back. A launch waiting on this step is
     * let through by its budget, or by the DeadObjectException when the process is gone.
     */
    private void leave(String pkg) {
        leaving = true;
        BridgeLog.i("Sensitive apps: turning wireless debugging off; bridge exiting");
        if (reported != null && !reported.equals(pkg)) reporter.report(reported, false);
        reporter.leaving(pkg);
        if (!ShellKeyInjector.runShellUnlogged(SHELL_TIMEOUT_S, false, SensitiveApps.wirelessDebuggingOff()).ok) {
            BridgeLog.w("Sensitive apps: could not turn wireless debugging off");
        }
        System.exit(0);
    }

    /**
     * The touched switches are read side by side, then written side by side, so a listed app's
     * launch waits for two shell rounds however many switches it hides (two read + write pairs one
     * after the other overran {@link #LAUNCH_BUDGET_MS}). Always a fresh read, never a cached value:
     * the user may have changed a switch since, and only what this bridge turned off is turned back on.
     * An unreadable switch is left alone, kept noted, and tried again on the next step.
     */
    private void apply(int[] step) {
        String own = packageName;
        int[] bits = SensitiveApps.BITS;
        String[][] reads = new String[bits.length][];
        for (int i = 0; i < bits.length; i++) {
            if (((step[0] | step[1]) & bits[i]) != 0) reads[i] = SensitiveApps.read(bits[i]);
        }
        ActionTestResult[] read = ShellKeyInjector.runAllUnlogged(SHELL_TIMEOUT_S, true, reads);
        String[] current = new String[bits.length];
        for (int i = 0; i < bits.length; i++) current[i] = value(bits[i], read[i]);
        String[][] writes = state.writes(step, current, own, removedEntries);
        int hiding = 0;
        for (int i = 0; i < bits.length; i++) {
            if (writes[i] == null || (step[0] & bits[i]) == 0) continue;
            hiding |= bits[i];
            if (bits[i] == SensitiveApps.A11Y) removedEntries = SensitiveApps.ownEntries(current[i], own);
        }
        // Noted before the writes: a bridge killed between the two still puts them back on its next start.
        if (hiding != 0) saveMarker(state.changed() | hiding);
        ActionTestResult[] written = ShellKeyInjector.runAllUnlogged(SHELL_TIMEOUT_S, false, writes);
        for (int i = 0; i < bits.length; i++) {
            if (writes[i] == null || !written[i].ok) continue;
            if ((hiding & bits[i]) != 0) {
                state.hidden(bits[i], true);
                BridgeLog.i("Sensitive apps: switch " + bits[i] + " off");
            } else {
                state.restored(bits[i]);
                BridgeLog.i("Sensitive apps: switch " + bits[i] + " back on");
            }
        }
    }

    /** The setting's value, or null when it could not be read as one. The accessibility list is never logged. */
    private static String value(int bit, ActionTestResult result) {
        if (result == null || !result.ok) return null;
        List<String> lines = result.output;
        String line = lines.isEmpty() ? "" : lines.get(0);
        return line.length() > MAX_VALUE_CHARS ? null : SensitiveApps.parse(bit, line);
    }

    private void saveMarker(int changed) {
        if (changed == 0) {
            marker.delete();
            return;
        }
        Properties saved = new Properties();
        saved.setProperty("changed", Integer.toString(changed));
        if ((changed & SensitiveApps.A11Y) != 0) saved.setProperty("a11y", removedEntries);
        try (FileOutputStream out = new FileOutputStream(marker)) {
            saved.store(out, null);
        } catch (Exception e) {
            BridgeLog.w("Sensitive apps: could not note switches (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Feedback only: asks the live app for its notice; never replayed, never drives state. */
    private void report() {
        String now = state.changed() == 0 ? null : state.owner() != null ? state.owner() : reported;
        if (reported != null && !reported.equals(now)) reporter.report(reported, false);
        if (now != null && !now.equals(reported)) reporter.report(now, true);
        reported = now;
    }

    private void execute(Runnable task) {
        try {
            worker.execute(task);
        } catch (Exception stopped) {
            // Shutting down.
        }
    }

    private static void await(Future<?> task, long waitMs) {
        try {
            task.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Bounded; whatever is left stays noted in the marker.
        }
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
