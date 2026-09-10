package com.example.bridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers whether a single {@link BridgeConfig.Constraint} currently holds, so a script only
 * fires while every constraint on it is satisfied.
 *
 * <p>The bridge has no {@link android.content.Context}, so state is read via the same shell
 * commands the process is already authorized to run (it already shells out for input injection
 * in {@link ShellKeyInjector}). Semantics here must match {@code com.example.service
 * .AndroidConstraintEvaluator} in the app module — both answer the same predicates about real
 * device state, just through different mechanisms.
 *
 * <p>Every predicate that cannot read or parse its state answers {@code true}: a constraint the
 * bridge cannot evaluate never blocks a script. The {@code parseX} helpers are package-private so
 * the tests can feed them canned command output.
 */
final class ConstraintEvaluator {

    private final TorchController torch;

    ConstraintEvaluator(TorchController torch) {
        this.torch = torch;
    }

    boolean isSatisfied(BridgeConfig.Constraint constraint) {
        Map<String, String> p = constraint.params;
        switch (constraint.type) {
            case BridgeConfig.CONSTRAINT_SCREEN_ON:
                return isScreenOn();
            case BridgeConfig.CONSTRAINT_SCREEN_OFF:
                return !isScreenOn();
            case BridgeConfig.CONSTRAINT_WIFI_ON:
                return isWifiOn();
            case BridgeConfig.CONSTRAINT_WIFI_OFF:
                return !isWifiOn();
            case BridgeConfig.CONSTRAINT_BLUETOOTH_ON:
                return isBluetoothOn();
            case BridgeConfig.CONSTRAINT_BLUETOOTH_OFF:
                return !isBluetoothOn();
            case BridgeConfig.CONSTRAINT_CHARGING:
                return isCharging();
            case BridgeConfig.CONSTRAINT_DISCHARGING:
                return !isCharging();
            case BridgeConfig.CONSTRAINT_IN_PHONE_CALL:
                return isInPhoneCall();
            case BridgeConfig.CONSTRAINT_NOT_IN_PHONE_CALL:
                return !isInPhoneCall();
            case BridgeConfig.CONSTRAINT_FLASHLIGHT_ON:
                return torch.isOn();
            case BridgeConfig.CONSTRAINT_FLASHLIGHT_OFF:
                return !torch.isOn();
            case BridgeConfig.CONSTRAINT_APP_IN_FOREGROUND:
                return isAppInForeground(p.get("packageName"));
            case BridgeConfig.CONSTRAINT_APP_NOT_IN_FOREGROUND:
                return !isAppInForeground(p.get("packageName"));
            case BridgeConfig.CONSTRAINT_MEDIA_IS_PLAYING:
                return isMediaPlaying();
            case BridgeConfig.CONSTRAINT_NO_MEDIA_PLAYING:
                return !isMediaPlaying();
            case BridgeConfig.CONSTRAINT_BLUETOOTH_DEVICE_CONNECTED:
                return isBluetoothDeviceConnected(p.get("address"), p.get("name"));
            case BridgeConfig.CONSTRAINT_BLUETOOTH_DEVICE_DISCONNECTED:
                return !isBluetoothDeviceConnected(p.get("address"), p.get("name"));
            case BridgeConfig.CONSTRAINT_SCREEN_ORIENTATION:
                return isOrientation(p.get("orientation"));
            case BridgeConfig.CONSTRAINT_DARK_MODE_ON:
                return isDarkMode();
            case BridgeConfig.CONSTRAINT_DARK_MODE_OFF:
                return !isDarkMode();
            case BridgeConfig.CONSTRAINT_CONNECTED_TO_WIFI_NETWORK:
                return isConnectedToWifi(p.get("ssid"));
            case BridgeConfig.CONSTRAINT_DISCONNECTED_FROM_WIFI_NETWORK:
                return !isConnectedToWifi(p.get("ssid"));
            case BridgeConfig.CONSTRAINT_KEYBOARD_SHOWING:
                return isKeyboardShowing();
            case BridgeConfig.CONSTRAINT_KEYBOARD_NOT_SHOWING:
                return !isKeyboardShowing();
            case BridgeConfig.CONSTRAINT_DEVICE_LOCKED:
                return isDeviceLocked();
            case BridgeConfig.CONSTRAINT_DEVICE_UNLOCKED:
                return !isDeviceLocked();
            case BridgeConfig.CONSTRAINT_LOCK_SCREEN_SHOWING:
                return isLockScreenShowing();
            case BridgeConfig.CONSTRAINT_LOCK_SCREEN_NOT_SHOWING:
                return !isLockScreenShowing();
            case BridgeConfig.CONSTRAINT_PHONE_RINGING:
                return parsePhoneRinging(shell("dumpsys", "telephony.registry"));
            case BridgeConfig.CONSTRAINT_BATTERY_SAVER_ON:
                return parseSettingOn(shell("settings", "get", "global", "low_power"));
            case BridgeConfig.CONSTRAINT_BATTERY_SAVER_OFF:
                return !parseSettingOn(shell("settings", "get", "global", "low_power"));
            case BridgeConfig.CONSTRAINT_CHARGING_WIRED:
                return parseChargingWired(shell("dumpsys", "battery"));
            case BridgeConfig.CONSTRAINT_CHARGING_WIRELESS:
                return parseChargingWireless(shell("dumpsys", "battery"));
            case BridgeConfig.CONSTRAINT_BATTERY_ABOVE:
                return isBatteryAbove(p.get("percent"), true);
            case BridgeConfig.CONSTRAINT_BATTERY_BELOW:
                return isBatteryAbove(p.get("percent"), false);
            case BridgeConfig.CONSTRAINT_LOCATION_ON:
                return isLocationOn();
            case BridgeConfig.CONSTRAINT_LOCATION_OFF:
                return !isLocationOn();
            case BridgeConfig.CONSTRAINT_TIME:
                return isWithinTime(p.get("start"), p.get("end"), LocalTime.now());
            case BridgeConfig.CONSTRAINT_DAY_OF_WEEK:
                return isDayOfWeek(p.get("days"), LocalDate.now().getDayOfWeek());
            case BridgeConfig.CONSTRAINT_MOBILE_DATA_ON:
                return isMobileDataOn();
            case BridgeConfig.CONSTRAINT_MOBILE_DATA_OFF:
                return !isMobileDataOn();
            case BridgeConfig.CONSTRAINT_AIRPLANE_MODE_ON:
                return isAirplaneModeOn();
            case BridgeConfig.CONSTRAINT_AIRPLANE_MODE_OFF:
                return !isAirplaneModeOn();
            case BridgeConfig.CONSTRAINT_RINGER_NORMAL:
                return isRingerMode("2");
            case BridgeConfig.CONSTRAINT_RINGER_VIBRATE:
                return isRingerMode("1");
            case BridgeConfig.CONSTRAINT_RINGER_SILENT:
                return isRingerMode("0");
            case BridgeConfig.CONSTRAINT_DND_ON:
                return isDndOn();
            case BridgeConfig.CONSTRAINT_DND_OFF:
                return !isDndOn();
            case BridgeConfig.CONSTRAINT_HEADPHONES_CONNECTED:
                return parseHeadphonesConnected(shell("dumpsys", "audio"));
            case BridgeConfig.CONSTRAINT_HEADPHONES_DISCONNECTED:
                return !parseHeadphonesConnected(shell("dumpsys", "audio"));
            default:
                // A constraint type that isn't AVAILABLE never blocks a script.
                return true;
        }
    }

    static boolean isScreenOn() {
        return parseScreenOn(shell("dumpsys", "power"));
    }

    // Package-private so BridgeMain can read the same state before flipping a toggle action.

    static boolean isWifiOn() {
        return parseSettingOn(shell("settings", "get", "global", "wifi_on"));
    }

    // Package-private so BridgeMain can read the same state before flipping a toggle action.
    static boolean isBluetoothOn() {
        return parseSettingOn(shell("settings", "get", "global", "bluetooth_on"));
    }

    static boolean isMobileDataOn() {
        return parseSettingOn(shell("settings", "get", "global", "mobile_data"));
    }

    static boolean isAirplaneModeOn() {
        return parseSettingOn(shell("settings", "get", "global", "airplane_mode_on"));
    }

    /** location_providers_allowed is empty when every provider (and so location) is off. */
    static boolean isLocationOn() {
        return !shell("settings", "get", "secure", "location_providers_allowed").trim().isEmpty();
    }

    /** zen_mode is 0 when DND is off and 1/2/3 for its priority/silence/alarms-only levels. */
    static boolean isDndOn() {
        return !"0".equals(shell("settings", "get", "global", "zen_mode").trim());
    }

    private static boolean isCharging() {
        return parseCharging(shell("dumpsys", "battery"));
    }

    private static boolean isInPhoneCall() {
        return parseInPhoneCall(shell("dumpsys", "telephony.registry"));
    }

    private static boolean isAppInForeground(String packageName) {
        return parseAppInForeground(shell("dumpsys", "activity", "activities"), packageName);
    }

    private static boolean isMediaPlaying() {
        return parseMediaPlaying(shell("dumpsys", "media_session"));
    }

    private static boolean isBluetoothDeviceConnected(String address, String name) {
        return parseBluetoothDeviceConnected(shell("dumpsys", "bluetooth_manager"), address, name);
    }

    private static boolean isOrientation(String orientation) {
        return parseOrientation(shell("dumpsys", "display"), orientation);
    }

    private static boolean isDarkMode() {
        return parseDarkMode(shell("cmd", "uimode", "night"));
    }

    private static boolean isConnectedToWifi(String ssid) {
        return parseConnectedToWifi(shell("cmd", "wifi", "status"), ssid);
    }

    private static boolean isKeyboardShowing() {
        return parseKeyboardShowing(shell("dumpsys", "input_method"));
    }

    private static boolean isDeviceLocked() {
        return parseDeviceLocked(shell("dumpsys", "trust"));
    }

    private static boolean isLockScreenShowing() {
        return parseLockScreenShowing(shell("dumpsys", "window", "policy"));
    }

    private static boolean isBatteryAbove(String percent, boolean above) {
        return parseBatteryLevel(shell("dumpsys", "battery"), percent, above);
    }

    /** mode_ringer: 2 = NORMAL, 1 = VIBRATE, 0 = SILENT. Unreadable → true, like every other predicate. */
    private static boolean isRingerMode(String expected) {
        String mode = shell("settings", "get", "global", "mode_ringer").trim();
        return !mode.matches("\\d+") || expected.equals(mode);
    }

    // Package-private so tests can feed canned command output without shelling out.

    static boolean parseScreenOn(String dumpsysPowerOutput) {
        // "mWakefulness=Awake" while on; "mWakefulness=Asleep" (or Dozing) while off.
        Matcher m = Pattern.compile("mWakefulness=(\\w+)").matcher(dumpsysPowerOutput);
        if (m.find()) {
            return "Awake".equals(m.group(1));
        }
        return true;
    }

    static boolean parseSettingOn(String settingsGetOutput) {
        return "1".equals(settingsGetOutput.trim());
    }

    static boolean parseCharging(String dumpsysBatteryOutput) {
        // status: 2 = CHARGING, 5 = FULL (plugged in, not actively drawing) both count as charging.
        Matcher m = Pattern.compile("status:\\s*(\\d+)").matcher(dumpsysBatteryOutput);
        if (m.find()) {
            String status = m.group(1);
            return "2".equals(status) || "5".equals(status);
        }
        return false;
    }

    static boolean parseInPhoneCall(String dumpsysTelephonyRegistryOutput) {
        // mCallState=2 is CALL_STATE_OFFHOOK.
        Matcher m = Pattern.compile("mCallState=(\\d+)").matcher(dumpsysTelephonyRegistryOutput);
        if (m.find()) {
            return "2".equals(m.group(1));
        }
        return false;
    }

    static boolean parsePhoneRinging(String dumpsysTelephonyRegistryOutput) {
        // mCallState=1 is CALL_STATE_RINGING.
        Matcher m = Pattern.compile("mCallState=(\\d+)").matcher(dumpsysTelephonyRegistryOutput);
        return m.find() ? "1".equals(m.group(1)) : true;
    }

    /**
     * Newer builds print "topResumedActivity=ActivityRecord{... u0 pkg/.Activity t2}"; some OEM
     * builds only print "ResumedActivity: ActivityRecord{...}". Both end in "ResumedActivity".
     */
    static boolean parseAppInForeground(String dumpsysActivitiesOutput, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return true;
        }
        Matcher m = Pattern.compile("ResumedActivity[=:]\\s*ActivityRecord\\{\\S+ u\\d+ ([^/\\s]+)/")
                .matcher(dumpsysActivitiesOutput);
        return m.find() ? m.group(1).equals(packageName.trim()) : true;
    }

    /** Playback state 3 is PLAYING; printed as "state=PlaybackState {state=PLAYING(3)," or "{state=3,". */
    static boolean parseMediaPlaying(String dumpsysMediaSessionOutput) {
        return Pattern.compile("PlaybackState \\{state=(?:[A-Z_]+\\()?3(?!\\d)")
                .matcher(dumpsysMediaSessionOutput).find();
    }

    /**
     * Each bonded device is one line in BluetoothRemoteDevices with its live ACL link flags:
     * "XX:XX:XX:XX:12:BC(Public ) => ... [ACL BR/EDR:N LE:Y] Device Name". The stack prints
     * addresses anonymized (getAnonymizedAddress keeps only the last two octets), so a device is
     * matched by its full address, its anonymized address, or its name.
     */
    static boolean parseBluetoothDeviceConnected(String dumpsysBluetoothOutput, String address, String name) {
        String addr = address == null ? "" : address.trim().toUpperCase(Locale.ROOT);
        String anonymized = addr.length() == 17 ? "XX:XX:XX:XX:" + addr.substring(12) : "";
        String trimmedName = name == null ? "" : name.trim();
        if (addr.isEmpty() && trimmedName.isEmpty()) {
            return true;
        }
        Pattern acl = Pattern.compile("\\[ACL [^\\]]*:Y[^\\]]*\\]");
        for (String line : dumpsysBluetoothOutput.split("\n")) {
            if (!acl.matcher(line).find()) {
                continue;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (!addr.isEmpty() && (upper.contains(addr) || upper.contains(anonymized))) {
                return true;
            }
            if (!trimmedName.isEmpty() && line.trim().endsWith(" " + trimmedName)) {
                return true;
            }
        }
        return false;
    }

    /** Surface rotation 0/2 is portrait, 1/3 landscape; read from "mCurrentOrientation=N". */
    static boolean parseOrientation(String dumpsysDisplayOutput, String expected) {
        Matcher m = Pattern.compile("mCurrentOrientation=(\\d)").matcher(dumpsysDisplayOutput);
        if (!m.find() || expected == null) {
            return true;
        }
        boolean landscape = "1".equals(m.group(1)) || "3".equals(m.group(1));
        switch (expected.trim().toLowerCase(Locale.ROOT)) {
            case "portrait":
                return !landscape;
            case "landscape":
                return landscape;
            default:
                return true;
        }
    }

    static boolean parseDarkMode(String cmdUimodeNightOutput) {
        Matcher m = Pattern.compile("Night mode:\\s*(\\w+)").matcher(cmdUimodeNightOutput);
        return m.find() ? "yes".equals(m.group(1)) : true;
    }

    /** "Wifi is connected to \"SSID\"" while connected, "Wifi is not connected" otherwise. Blank ssid = any. */
    static boolean parseConnectedToWifi(String cmdWifiStatusOutput, String ssid) {
        if (cmdWifiStatusOutput.contains("Wifi is not connected")) {
            return false;
        }
        Matcher m = Pattern.compile("Wifi is connected to \"(.*)\"").matcher(cmdWifiStatusOutput);
        if (!m.find()) {
            return true;
        }
        String wanted = ssid == null ? "" : ssid.trim();
        return wanted.isEmpty() || wanted.equals(m.group(1));
    }

    static boolean parseKeyboardShowing(String dumpsysInputMethodOutput) {
        Matcher m = Pattern.compile("mInputShown=(\\w+)").matcher(dumpsysInputMethodOutput);
        return m.find() ? "true".equals(m.group(1)) : true;
    }

    /** dumpsys trust lists "deviceLocked=1" per user; prefer the line flagged "(current)". */
    static boolean parseDeviceLocked(String dumpsysTrustOutput) {
        Matcher m = Pattern.compile("\\(current\\)[^\\n]*deviceLocked=(\\d)").matcher(dumpsysTrustOutput);
        if (!m.find()) {
            m = Pattern.compile("deviceLocked=(\\d)").matcher(dumpsysTrustOutput);
            if (!m.find()) {
                return true;
            }
        }
        return "1".equals(m.group(1));
    }

    /** The keyguard's "showing=true" is the first field under "KeyguardServiceDelegate". */
    static boolean parseLockScreenShowing(String dumpsysWindowPolicyOutput) {
        Matcher m = Pattern.compile("KeyguardServiceDelegate\\s+showing=(\\w+)").matcher(dumpsysWindowPolicyOutput);
        return m.find() ? "true".equals(m.group(1)) : true;
    }

    static boolean parseChargingWired(String dumpsysBatteryOutput) {
        return Pattern.compile("(AC|USB) powered: true").matcher(dumpsysBatteryOutput).find();
    }

    static boolean parseChargingWireless(String dumpsysBatteryOutput) {
        return dumpsysBatteryOutput.contains("Wireless powered: true");
    }

    /** above: level > percent; below: level < percent. Either side unparsable → true. */
    static boolean parseBatteryLevel(String dumpsysBatteryOutput, String percent, boolean above) {
        Matcher m = Pattern.compile("level:\\s*(\\d+)").matcher(dumpsysBatteryOutput);
        if (!m.find() || percent == null || !percent.trim().matches("\\d+")) {
            return true;
        }
        int level = Integer.parseInt(m.group(1));
        int threshold = Integer.parseInt(percent.trim());
        return above ? level > threshold : level < threshold;
    }

    /** now ∈ [start, end); end ≤ start wraps past midnight. Malformed "HH:MM" → true. */
    static boolean isWithinTime(String start, String end, LocalTime now) {
        LocalTime from = parseTime(start);
        LocalTime to = parseTime(end);
        if (from == null || to == null) {
            return true;
        }
        if (to.compareTo(from) <= 0) {
            return !now.isBefore(from) || now.isBefore(to);
        }
        return !now.isBefore(from) && now.isBefore(to);
    }

    private static LocalTime parseTime(String value) {
        if (value == null || !value.trim().matches("\\d{1,2}:\\d{2}")) {
            return null;
        }
        try {
            String[] parts = value.trim().split(":");
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** days = "mon,tue,...,sun" (case-insensitive, three-letter). Empty → true. */
    static boolean isDayOfWeek(String days, DayOfWeek today) {
        if (days == null || days.trim().isEmpty()) {
            return true;
        }
        String key = today.name().substring(0, 3).toLowerCase(Locale.ROOT);
        for (String day : days.toLowerCase(Locale.ROOT).split(",")) {
            if (day.trim().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * AudioDeviceInventory lists "[DeviceInfo: type:0x4 (wired_headset) name:... addr:...]" per
     * attached device under "Connected devices:", ending at the next blank line.
     */
    static boolean parseHeadphonesConnected(String dumpsysAudioOutput) {
        int start = dumpsysAudioOutput.indexOf("Connected devices:");
        if (start < 0) {
            return false;
        }
        int end = dumpsysAudioOutput.indexOf("\n\n", start);
        String section = end < 0 ? dumpsysAudioOutput.substring(start) : dumpsysAudioOutput.substring(start, end);
        return Pattern.compile("\\((wired_headset|wired_headphone|usb_headset|bt_a2dp\\w*|bt_sco\\w*|ble_headset)\\)")
                .matcher(section).find();
    }

    static String shell(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            process.waitFor();
            return out.toString();
        } catch (Exception e) {
            BridgeLog.w("Constraint shell command failed: " + String.join(" ", command) + ": " + e);
            return "";
        }
    }
}
