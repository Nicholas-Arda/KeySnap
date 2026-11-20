package com.example.bridge;

import java.util.regex.Pattern;

/**
 * "Default payment app": who answers a contactless tap. Android 15+ (SDK 35) keeps it in the
 * single-holder WALLET role and the NFC stack follows the role; `nfc_payment_default_component`
 * only mirrors it there. Android 11–14 read that setting directly. Verified on ColorOS 17
 * (PLG110, 2026-11-17): the role moved and NFC's "current wallet payment service" followed;
 * a package without a payment service is refused by the role, so a wrong pick changes nothing.
 */
final class PaymentApp {
    private PaymentApp() { }

    static final String ROLE = "android.app.role.WALLET";
    private static final String HCE_ACTION = "android.nfc.cardemulation.action.HOST_APDU_SERVICE";
    private static final String SETTING = "nfc_payment_default_component";
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    /** [pkg] alone sets it; with [other] it switches between the two, landing on [pkg] from anything else. */
    static boolean apply(String pkg, String other) {
        if (!valid(pkg) || (other != null && !other.isEmpty() && !valid(other))) return false;
        boolean role = sdk() >= 35;
        String target = target(role ? roleHolder() : packageOf(setting()), pkg, other);
        if (role) return ShellKeyInjector.runShell("cmd", "role", "add-role-holder", ROLE, target);
        String component = firstComponent(ShellKeyInjector.runShellForOutput(
                "cmd", "package", "query-services", "--components", "-a", HCE_ACTION, "-p", target));
        return component != null && ShellKeyInjector.runShell("settings", "put", "secure", SETTING, component);
    }

    static boolean valid(String pkg) {
        return pkg != null && PACKAGE.matcher(pkg).matches();
    }

    static String target(String current, String pkg, String other) {
        boolean switching = other != null && !other.isEmpty();
        return switching && pkg.equals(current) ? other : pkg;
    }

    /**
     * First line of `query-services --components`, expanded from `pkg/.Short` to `pkg/pkg.Short`.
     * ponytail: on Android 11–14 a package with several HCE services (Play services) gets its first
     * one; parse the services' payment category if a phone ever lists a non-payment one first.
     */
    static String firstComponent(String output) {
        if (output == null) return null;
        for (String line : output.split("\n")) {
            String s = line.trim();
            int slash = s.indexOf('/');
            if (slash <= 0 || !valid(s.substring(0, slash))) continue;
            String cls = s.substring(slash + 1);
            return s.substring(0, slash) + "/" + (cls.startsWith(".") ? s.substring(0, slash) + cls : cls);
        }
        return null;
    }

    static String packageOf(String component) {
        if (component == null) return "";
        int slash = component.indexOf('/');
        return slash > 0 ? component.substring(0, slash).trim() : component.trim();
    }

    private static String roleHolder() {
        String out = ShellKeyInjector.runShellForOutput("cmd", "role", "get-role-holders", ROLE);
        return out == null ? "" : out.trim().split("\n")[0].trim();
    }

    private static String setting() {
        return ShellKeyInjector.runShellForOutput("settings", "get", "secure", SETTING);
    }

    private static int sdk() {
        try {
            return Integer.parseInt(ShellKeyInjector.runShellForOutput("getprop", "ro.build.version.sdk").trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
