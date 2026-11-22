package com.example.bridge;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * toggle_hotspot: Wi-Fi hotspot on/off from the shell UID, through the tethering service's binder
 * ({@code ITetheringConnector}) with callerPkg "com.android.shell", which holds TETHER_PRIVILEGED.
 * TetheringManager sends the app's own package instead and is refused (error 14, PLG110
 * 2026-11-21); the binder call worked there and the Settings page followed it.
 *
 * <p>The current state comes from {@code IWifiManager.getWifiApEnabledState()} (ACCESS_WIFI_STATE,
 * which shell holds), the soft AP tracker the hotspot switch itself reads. Not
 * {@code Settings.Global wifi_ap_state}: it stayed 0 with the hotspot on.
 */
final class Hotspot {

    static final int WIFI_AP_STATE_DISABLING = 10;
    static final int WIFI_AP_STATE_DISABLED = 11;
    static final int WIFI_AP_STATE_ENABLING = 12;
    static final int WIFI_AP_STATE_ENABLED = 13;
    static final int WIFI_AP_STATE_FAILED = 14;

    private static final int TETHERING_WIFI = 0;
    private static final int CONNECTIVITY_SCOPE_GLOBAL = 1;
    private static final String CALLER = "com.android.shell";
    private static final String LISTENER = "android.net.IIntResultListener";
    // Android 16+ answers a start only once the soft AP is up, so allow a few seconds.
    private static final long RESULT_WAIT_MS = 8_000;

    private Hotspot() {
    }

    /** [state] is "on", "off", or anything else for toggle. */
    static boolean apply(String state) {
        boolean explicit = "on".equals(state) || "off".equals(state);
        Boolean on = target(state, explicit ? null : apState());
        if (on == null) {
            BridgeLog.w("toggle_hotspot: current hotspot state unreadable");
            return false;
        }
        return on ? call("startTethering", wifiRequest()) : call("stopTethering", TETHERING_WIFI);
    }

    /** Whether to turn it on; null when a toggle cannot read the current state (fail, never guess). */
    static Boolean target(String state, Integer apState) {
        if ("on".equals(state)) return true;
        if ("off".equals(state)) return false;
        if (apState == null) return null;
        switch (apState) {
            case WIFI_AP_STATE_ENABLING:
            case WIFI_AP_STATE_ENABLED:
                return false;
            case WIFI_AP_STATE_DISABLING:
            case WIFI_AP_STATE_DISABLED:
            case WIFI_AP_STATE_FAILED:
                return true;
            default:
                return null;
        }
    }

    private static Integer apState() {
        try {
            Object wifi = service("wifi", "android.net.wifi.IWifiManager");
            return (Integer) wifi.getClass().getMethod("getWifiApEnabledState").invoke(wifi);
        } catch (Throwable t) {
            BridgeLog.w("toggle_hotspot: getWifiApEnabledState unavailable (" + name(t) + ")");
            return null;
        }
    }

    /** TetheringRequestParcel as verified on PLG110: Wi-Fi, provisioning UI allowed, global scope. */
    private static Object wifiRequest() {
        try {
            Object request = Class.forName("android.net.TetheringRequestParcel").getConstructor().newInstance();
            Class<?> type = request.getClass();
            type.getField("tetheringType").setInt(request, TETHERING_WIFI);
            type.getField("showProvisioningUi").setBoolean(request, true);
            try {
                type.getField("connectivityScope").setInt(request, CONNECTIVITY_SCOPE_GLOBAL);
            } catch (NoSuchFieldException android11) {
                // Added in Android 12; global was the only scope before.
            }
            return request;
        } catch (Throwable t) {
            BridgeLog.w("toggle_hotspot: TetheringRequestParcel unavailable (" + name(t) + ")");
            return null;
        }
    }

    /**
     * Android 12+: {@code (arg, callerPkg, callingAttributionTag, listener)}; Android 11:
     * {@code (arg, callerPkg, listener)}. Any other shape fails with a log line, never a crash.
     */
    private static boolean call(String method, Object arg) {
        if (arg == null) return false;
        try {
            Object connector = service("tethering", "android.net.ITetheringConnector");
            Method found = null;
            for (Method m : connector.getClass().getMethods()) {
                int count = m.getParameterTypes().length;
                if (m.getName().equals(method) && (count == 3 || count == 4)) found = m;
            }
            if (found == null) {
                BridgeLog.w("toggle_hotspot: no known " + method + " signature");
                return false;
            }
            Result result = new Result();
            Object listener = Class.forName(LISTENER + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, result);
            Object[] args = found.getParameterTypes().length == 4
                    ? new Object[]{arg, CALLER, null, listener}
                    : new Object[]{arg, CALLER, listener};
            found.invoke(connector, args);
            Integer code = result.await();
            if (code == null) {
                BridgeLog.w("toggle_hotspot: " + method + " gave no result within " + RESULT_WAIT_MS + " ms");
                return false;
            }
            if (code != 0) {
                BridgeLog.w("toggle_hotspot: " + method + " error " + code);
                return false;
            }
            return true;
        } catch (Throwable t) {
            BridgeLog.w("toggle_hotspot: " + method + " failed (" + name(t) + ")");
            return false;
        }
    }

    private static Object service(String name, String iface) throws Exception {
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, name);
        Object service = Class.forName(iface + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
        if (service == null) throw new IllegalStateException("no " + name + " service");
        return service;
    }

    private static String name(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        return cause.getClass().getSimpleName();
    }

    /** IIntResultListener (oneway, one method: onResult(int)), served in this process. */
    private static final class Result extends Binder {
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile int code;

        Result() {
            attachInterface(null, LISTENER);
        }

        @Override
        protected boolean onTransact(int tx, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (tx != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(tx, data, reply, flags);
            data.enforceInterface(LISTENER);
            code = data.readInt();
            done.countDown();
            return true;
        }

        Integer await() throws InterruptedException {
            return done.await(RESULT_WAIT_MS, TimeUnit.MILLISECONDS) ? code : null;
        }
    }
}
