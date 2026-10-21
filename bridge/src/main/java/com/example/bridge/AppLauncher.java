package com.example.bridge;

/** Resolve a launcher component before starting it: am's implicit start requires DEFAULT,
 * which many perfectly valid launcher activities (including ColorOS Calculator) omit. */
final class AppLauncher {
    interface Commands {
        String output(String... command);
        boolean start(String... command);
    }

    static boolean launch(String packageName, Commands commands) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) return false;
        String resolved = commands.output("cmd", "package", "resolve-activity", "--brief", "--user", "current",
                "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", packageName);
        for (String line : resolved.split("\\r?\\n")) {
            String component = line.trim();
            if (!component.startsWith(packageName + "/")) continue;
            String activity = component.substring(packageName.length() + 1);
            if (!activity.matches("[A-Za-z0-9_.$]+")) continue;
            return commands.start("am", "start", "--user", "current", "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.LAUNCHER", "-n", component, "-f", "0x10200000");
        }
        return false;
    }

    private AppLauncher() {}
}
