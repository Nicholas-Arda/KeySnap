package com.example.bridge;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Other apps' launcher shortcuts ("New Incognito tab", "New voice note"), through the launcher
 * apps service: the shell holds ACCESS_SHORTCUTS, the app does not. Nothing here parses dumpsys
 * text, so a shortcut's label cannot pose as another app's shortcut.
 *
 * <p>Starting one is two halves. The service starts a shortcut as its publisher, and a publisher in
 * the background is refused (background activity launch), so this side only asks for the
 * shortcut's PendingIntent and hands it to the app's {@code ShortcutLaunchReceiver}, which sends
 * it: the app has a system-bound Accessibility service, which exempts it from that limit.
 */
final class AppShortcuts {
    private AppShortcuts() {}

    static final String RECEIVER = "com.example.service.ShortcutLaunchReceiver";
    static final String EXTRA_PENDING = "pending";
    private static final String SHELL = "com.android.shell";
    // LauncherApps.ShortcutQuery FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST.
    private static final int MATCH_ALL = 1 | 2 | 8;

    /** {id, label} of each enabled shortcut [packageName] publishes; null when the service is unreachable. */
    static List<String[]> list(String packageName) {
        try {
            Object service = launcherApps();
            Object query = Class.forName("android.content.pm.LauncherApps$ShortcutQuery").getConstructor().newInstance();
            query.getClass().getMethod("setPackage", String.class).invoke(query, packageName);
            query.getClass().getMethod("setQueryFlags", int.class).invoke(query, MATCH_ALL);
            Class<?> wrapper = Class.forName("android.content.pm.ShortcutQueryWrapper");
            Object wrapped = wrapper.getConstructor(query.getClass()).newInstance(query);
            Class<?> userHandle = Class.forName("android.os.UserHandle");
            Object slice = service.getClass().getMethod("getShortcuts", String.class, wrapper, userHandle)
                    .invoke(service, SHELL, wrapped, user());
            List<String[]> out = new ArrayList<>();
            for (Object info : (List<?>) slice.getClass().getMethod("getList").invoke(slice)) {
                if (!(boolean) info.getClass().getMethod("isEnabled").invoke(info)) continue;
                Object label = info.getClass().getMethod("getShortLabel").invoke(info);
                out.add(new String[]{(String) info.getClass().getMethod("getId").invoke(info), label == null ? "" : label.toString()});
            }
            return out;
        } catch (Throwable t) {
            BridgeLog.w("Shortcut list unavailable: " + t.getClass().getSimpleName());
            return null;
        }
    }

    /** Hands the shortcut's PendingIntent to [appPackage]'s receiver; false when it does not exist. */
    static boolean launch(String packageName, String shortcutId, String appPackage) {
        if (blank(packageName) || blank(shortcutId) || blank(appPackage)) return false;
        try {
            Object service = launcherApps();
            Method get = null;
            for (Method m : service.getClass().getMethods()) if (m.getName().equals("getShortcutIntent")) get = m;
            if (get == null) return false;
            Object pending = get.invoke(service, SHELL, packageName.trim(), shortcutId, null, user());
            if (pending == null) return false;
            Class<?> intentClass = Class.forName("android.content.Intent");
            Object intent = intentClass.getConstructor().newInstance();
            intentClass.getMethod("setClassName", String.class, String.class).invoke(intent, appPackage, RECEIVER);
            intentClass.getMethod("putExtra", String.class, Class.forName("android.os.Parcelable")).invoke(intent, EXTRA_PENDING, pending);
            return broadcast(intent);
        } catch (Throwable t) {
            BridgeLog.w("Shortcut launch failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** IActivityManager.broadcastIntent*, arguments filled by type so the version's signature does not matter. */
    private static boolean broadcast(Object intent) throws Exception {
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Method target = null;
        for (Method m : am.getClass().getMethods()) {
            if (m.getName().equals("broadcastIntentWithFeature") || (target == null && m.getName().equals("broadcastIntent"))) target = m;
        }
        if (target == null) return false;
        Class<?>[] types = target.getParameterTypes();
        Object[] args = new Object[types.length];
        // ints in order: resultCode, appOp (OP_NONE), userId.
        int[] ints = {0, -1, 0};
        int nextInt = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) args[i] = ints[Math.min(nextInt++, ints.length - 1)];
            else if (types[i] == boolean.class) args[i] = false;
            else if (types[i].isInstance(intent)) args[i] = intent;
        }
        target.invoke(am, args);
        return true;
    }

    private static Object launcherApps() throws Exception {
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "launcherapps");
        return Class.forName("android.content.pm.ILauncherApps$Stub")
                .getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, binder);
    }

    private static Object user() throws Exception {
        return Class.forName("android.os.UserHandle").getMethod("of", int.class).invoke(null, 0);
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
