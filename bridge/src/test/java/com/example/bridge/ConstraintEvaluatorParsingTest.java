package com.example.bridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Feeds canned shell command output to ConstraintEvaluator's parsing helpers, since the real
 * predicates shell out to dumpsys/settings, which don't exist in a JVM test environment.
 *
 * <p>The multi-line samples are trimmed real output captured from an Android 16 phone and
 * emulator, with SSIDs, addresses and device names replaced by placeholders.
 */
public class ConstraintEvaluatorParsingTest {

    @Test public void parseScreenOnRecognizesAwake() {
        assertTrue(ConstraintEvaluator.parseScreenOn("  mWakefulness=Awake\n  mWakeLockSummary=0x0"));
    }

    @Test public void parseScreenOnRecognizesAsleep() {
        assertFalse(ConstraintEvaluator.parseScreenOn("  mWakefulness=Asleep"));
    }

    @Test public void parseScreenOnDefaultsToOnWhenFieldIsMissing() {
        assertTrue(ConstraintEvaluator.parseScreenOn(""));
    }

    @Test public void parseSettingOnRecognizesOneAndZero() {
        assertTrue(ConstraintEvaluator.parseSettingOn("1"));
        assertFalse(ConstraintEvaluator.parseSettingOn("0"));
        assertFalse(ConstraintEvaluator.parseSettingOn("null"));
    }

    @Test public void parseChargingRecognizesChargingAndFullStatus() {
        assertTrue(ConstraintEvaluator.parseCharging("  status: 2\n  health: 2"));
        assertTrue(ConstraintEvaluator.parseCharging("  status: 5"));
    }

    @Test public void parseChargingRecognizesDischargingStatus() {
        assertFalse(ConstraintEvaluator.parseCharging("  status: 3"));
    }

    @Test public void parseInPhoneCallRecognizesOffhook() {
        assertTrue(ConstraintEvaluator.parseInPhoneCall("  mCallState=2"));
    }

    @Test public void parseInPhoneCallRecognizesIdleAndRinging() {
        assertFalse(ConstraintEvaluator.parseInPhoneCall("  mCallState=0"));
        assertFalse(ConstraintEvaluator.parseInPhoneCall("  mCallState=1"));
    }

    private static final String ACTIVITIES_PHONE =
            "  ResumedActivity: ActivityRecord{34171489 u0 com.android.launcher/.Launcher t2}\n";
    private static final String ACTIVITIES_EMULATOR =
            "    topResumedActivity=ActivityRecord{114004646 u0 io.github.nicholasarda.keysnap/com.example.MainActivity t240}\n"
            + "  ResumedActivity: ActivityRecord{114004646 u0 io.github.nicholasarda.keysnap/com.example.MainActivity t240}\n";

    @Test public void parseAppInForegroundReadsOemAndAospResumedActivityLines() {
        assertTrue(ConstraintEvaluator.parseAppInForeground(ACTIVITIES_PHONE, "com.android.launcher"));
        assertFalse(ConstraintEvaluator.parseAppInForeground(ACTIVITIES_PHONE, "com.example.other"));
        assertTrue(ConstraintEvaluator.parseAppInForeground(ACTIVITIES_EMULATOR, "io.github.nicholasarda.keysnap"));
        assertFalse(ConstraintEvaluator.parseAppInForeground(ACTIVITIES_EMULATOR, "com.android.launcher"));
    }

    @Test public void parseAppInForegroundDefaultsToTrueWhenUnreadableOrUnset() {
        assertTrue(ConstraintEvaluator.parseAppInForeground("", "com.example.other"));
        assertTrue(ConstraintEvaluator.parseAppInForeground(ACTIVITIES_PHONE, ""));
        assertTrue(ConstraintEvaluator.parseAppInForeground(ACTIVITIES_PHONE, null));
    }

    private static final String MEDIA_PAUSED =
            "      active=true\n"
            + "      state=PlaybackState {state=PAUSED(2), position=238901, buffered position=0, speed=0.0, updated=83010320, actions=142260, custom actions=[], active item id=7, error=null}\n"
            + "      active=false\n"
            + "      state=PlaybackState {state=STOPPED(1), position=0, buffered position=0, speed=1.0, updated=79306955, actions=8192, custom actions=[], active item id=-1, error=null}\n";

    @Test public void parseMediaPlayingRecognizesPlayingInNewAndOldFormats() {
        assertTrue(ConstraintEvaluator.parseMediaPlaying(MEDIA_PAUSED.replace("PAUSED(2)", "PLAYING(3)")));
        assertTrue(ConstraintEvaluator.parseMediaPlaying("      state=PlaybackState {state=3, position=0, buffered position=0}\n"));
    }

    @Test public void parseMediaPlayingIgnoresPausedStoppedAndNoSessions() {
        assertFalse(ConstraintEvaluator.parseMediaPlaying(MEDIA_PAUSED));
        assertFalse(ConstraintEvaluator.parseMediaPlaying("  Sessions Stack - have 0 sessions:\n    state=null\n"));
    }

    private static final String BLUETOOTH_PHONE =
            "BluetoothRemoteDevices\n"
            + "  Bonded devices: 3\n"
            + "    XX:XX:XX:XX:21:20(Public ) => XX:XX:XX:XX:21:20(Unknown) [ DUAL ] [0x240418] [ACL BR/EDR:N LE:N] My Earbuds\n"
            + "        [BR/EDR UUIDs]: 0000110b-0000-1000-8000-00805f9b34fb\n"
            + "    XX:XX:XX:XX:12:BC(Public ) => XX:XX:XX:XX:12:BC(Unknown) [BR/EDR] [0x740420] [ACL BR/EDR:Y LE:N] MY CAR\n"
            + "    XX:XX:XX:XX:BA:AF(Public ) => XX:XX:XX:XX:BA:AF(Unknown) [ DUAL ] [0x2E010C] [ACL BR/EDR:N LE:Y] DESKTOP-PC\n";

    @Test public void parseBluetoothDeviceConnectedMatchesAnonymizedAddressCaseInsensitively() {
        assertTrue(ConstraintEvaluator.parseBluetoothDeviceConnected(BLUETOOTH_PHONE, "aa:bb:cc:dd:12:bc", ""));
        assertTrue(ConstraintEvaluator.parseBluetoothDeviceConnected(BLUETOOTH_PHONE, "AA:BB:CC:DD:BA:AF", null));
        assertFalse(ConstraintEvaluator.parseBluetoothDeviceConnected(BLUETOOTH_PHONE, "AA:BB:CC:DD:21:20", ""));
    }

    @Test public void parseBluetoothDeviceConnectedMatchesFullAddressOnUnredactedOutput() {
        String unredacted = BLUETOOTH_PHONE.replace("XX:XX:XX:XX:12:BC", "AA:BB:CC:DD:12:BC");
        assertTrue(ConstraintEvaluator.parseBluetoothDeviceConnected(unredacted, "aa:bb:cc:dd:12:bc", ""));
    }

    @Test public void parseBluetoothDeviceConnectedFallsBackToName() {
        assertTrue(ConstraintEvaluator.parseBluetoothDeviceConnected(BLUETOOTH_PHONE, "", "MY CAR"));
        assertFalse(ConstraintEvaluator.parseBluetoothDeviceConnected(BLUETOOTH_PHONE, "", "My Earbuds"));
        assertFalse(ConstraintEvaluator.parseBluetoothDeviceConnected(BLUETOOTH_PHONE, "11:22:33:44:55:66", "Unknown"));
    }

    @Test public void parseBluetoothDeviceConnectedWithNoIdentityNeverBlocks() {
        assertTrue(ConstraintEvaluator.parseBluetoothDeviceConnected(BLUETOOTH_PHONE, "", ""));
        assertTrue(ConstraintEvaluator.parseBluetoothDeviceConnected("", null, null));
    }

    @Test public void parseOrientationMapsRotationToPortraitAndLandscape() {
        String portrait = "  mViewports=[DisplayViewport{type=INTERNAL, orientation=0}]\n    mCurrentOrientation=0\n";
        assertTrue(ConstraintEvaluator.parseOrientation(portrait, "portrait"));
        assertFalse(ConstraintEvaluator.parseOrientation(portrait, "landscape"));
        assertTrue(ConstraintEvaluator.parseOrientation("    mCurrentOrientation=1\n", "landscape"));
        assertTrue(ConstraintEvaluator.parseOrientation("    mCurrentOrientation=2\n", "portrait"));
        assertTrue(ConstraintEvaluator.parseOrientation("    mCurrentOrientation=3\n", "Landscape"));
        assertFalse(ConstraintEvaluator.parseOrientation("    mCurrentOrientation=3\n", "portrait"));
    }

    @Test public void parseOrientationDefaultsToTrueWhenUnreadable() {
        assertTrue(ConstraintEvaluator.parseOrientation("", "portrait"));
        assertTrue(ConstraintEvaluator.parseOrientation("    mCurrentOrientation=0\n", "sideways"));
        assertTrue(ConstraintEvaluator.parseOrientation("    mCurrentOrientation=0\n", null));
    }

    @Test public void parseDarkModeReadsUimodeNight() {
        assertTrue(ConstraintEvaluator.parseDarkMode("Night mode: yes\n"));
        assertFalse(ConstraintEvaluator.parseDarkMode("Night mode: no\n"));
        assertFalse(ConstraintEvaluator.parseDarkMode("Night mode: auto\n"));
        assertTrue(ConstraintEvaluator.parseDarkMode(""));
    }

    private static final String WIFI_CONNECTED =
            "Wifi is enabled\n"
            + "Wifi scanning is always available\n"
            + "==== Primary ClientModeManager instance ====\n"
            + "Wifi is connected to \"MySSID\"\n";

    @Test public void parseConnectedToWifiComparesSsidExactlyAndBlankMeansAny() {
        assertTrue(ConstraintEvaluator.parseConnectedToWifi(WIFI_CONNECTED, "MySSID"));
        assertFalse(ConstraintEvaluator.parseConnectedToWifi(WIFI_CONNECTED, "myssid"));
        assertFalse(ConstraintEvaluator.parseConnectedToWifi(WIFI_CONNECTED, "MySSID_5G"));
        assertTrue(ConstraintEvaluator.parseConnectedToWifi(WIFI_CONNECTED, ""));
        assertTrue(ConstraintEvaluator.parseConnectedToWifi(WIFI_CONNECTED, null));
    }

    @Test public void parseConnectedToWifiIsFalseWhenNotConnectedAndTrueWhenUnreadable() {
        assertFalse(ConstraintEvaluator.parseConnectedToWifi("Wifi is enabled\nWifi is not connected\n", ""));
        assertFalse(ConstraintEvaluator.parseConnectedToWifi("Wifi is disabled\nWifi is not connected\n", "MySSID"));
        assertTrue(ConstraintEvaluator.parseConnectedToWifi("", "MySSID"));
    }

    @Test public void parseKeyboardShowingReadsInputShown() {
        assertTrue(ConstraintEvaluator.parseKeyboardShowing("      mImeWindowVis=2\n      mInputShown=true\n"));
        assertFalse(ConstraintEvaluator.parseKeyboardShowing("      mImeWindowVis=0\n      mInputShown=false\n"));
        assertTrue(ConstraintEvaluator.parseKeyboardShowing(""));
    }

    private static final String TRUST_LOCKED =
            "Trust manager state:\n"
            + " User \"Owner\" (id=0, flags=0x4c13) (current): trustState=UNTRUSTED, trustManaged=0, deviceLocked=1, isActiveUnlockRunning=0, strongAuthRequired=0x0\n"
            + "    Agent: bound=1, connected=1, managingTrust=0, trusted=0\n";

    @Test public void parseDeviceLockedReadsCurrentUser() {
        assertTrue(ConstraintEvaluator.parseDeviceLocked(TRUST_LOCKED));
        assertFalse(ConstraintEvaluator.parseDeviceLocked(TRUST_LOCKED.replace("deviceLocked=1", "deviceLocked=0")));
        assertTrue(ConstraintEvaluator.parseDeviceLocked(TRUST_LOCKED.replace("(current)", "")));
        assertTrue(ConstraintEvaluator.parseDeviceLocked(""));
    }

    private static final String WINDOW_POLICY_LOCKED =
            "    mKeyguardOccluded=false mKeyguardOccludedChanged=false mPendingKeyguardOccluded=false\n"
            + "    KeyguardServiceDelegate\n"
            + "      showing=true\n"
            + "      deviceHasKeyguard=true\n";

    @Test public void parseLockScreenShowingReadsKeyguardShowing() {
        assertTrue(ConstraintEvaluator.parseLockScreenShowing(WINDOW_POLICY_LOCKED));
        assertFalse(ConstraintEvaluator.parseLockScreenShowing(WINDOW_POLICY_LOCKED.replace("showing=true", "showing=false")));
        assertTrue(ConstraintEvaluator.parseLockScreenShowing(""));
    }

    @Test public void parsePhoneRingingRecognizesRingingOnly() {
        assertTrue(ConstraintEvaluator.parsePhoneRinging("    mCallState=1\n"));
        assertFalse(ConstraintEvaluator.parsePhoneRinging("    mCallState=0\n"));
        assertFalse(ConstraintEvaluator.parsePhoneRinging("    mCallState=2\n"));
        assertTrue(ConstraintEvaluator.parsePhoneRinging(""));
    }

    private static final String BATTERY_PHONE =
            "  AC powered: false\n"
            + "  USB powered: false\n"
            + "  Wireless powered: false\n"
            + "  Dock powered: false\n"
            + "  status: 3\n"
            + "  level: 8\n";

    @Test public void parseChargingWiredAndWirelessReadPoweredLines() {
        assertFalse(ConstraintEvaluator.parseChargingWired(BATTERY_PHONE));
        assertFalse(ConstraintEvaluator.parseChargingWireless(BATTERY_PHONE));
        assertTrue(ConstraintEvaluator.parseChargingWired(BATTERY_PHONE.replace("USB powered: false", "USB powered: true")));
        assertTrue(ConstraintEvaluator.parseChargingWired(BATTERY_PHONE.replace("AC powered: false", "AC powered: true")));
        assertFalse(ConstraintEvaluator.parseChargingWired(BATTERY_PHONE.replace("Wireless powered: false", "Wireless powered: true")));
        assertTrue(ConstraintEvaluator.parseChargingWireless(BATTERY_PHONE.replace("Wireless powered: false", "Wireless powered: true")));
    }

    @Test public void parseBatteryLevelComparesStrictly() {
        assertTrue(ConstraintEvaluator.parseBatteryLevel(BATTERY_PHONE, "5", true));
        assertFalse(ConstraintEvaluator.parseBatteryLevel(BATTERY_PHONE, "8", true));
        assertFalse(ConstraintEvaluator.parseBatteryLevel(BATTERY_PHONE, "8", false));
        assertTrue(ConstraintEvaluator.parseBatteryLevel(BATTERY_PHONE, "20", false));
        assertFalse(ConstraintEvaluator.parseBatteryLevel(BATTERY_PHONE, "20", true));
    }

    @Test public void parseBatteryLevelDefaultsToTrueWhenUnparsable() {
        assertTrue(ConstraintEvaluator.parseBatteryLevel("", "20", true));
        assertTrue(ConstraintEvaluator.parseBatteryLevel(BATTERY_PHONE, "", true));
        assertTrue(ConstraintEvaluator.parseBatteryLevel(BATTERY_PHONE, "lots", false));
        assertTrue(ConstraintEvaluator.parseBatteryLevel(BATTERY_PHONE, null, false));
    }

    @Test public void isWithinTimeUsesHalfOpenRange() {
        assertTrue(ConstraintEvaluator.isWithinTime("09:00", "17:00", LocalTime.of(9, 0)));
        assertTrue(ConstraintEvaluator.isWithinTime("09:00", "17:00", LocalTime.of(12, 30)));
        assertFalse(ConstraintEvaluator.isWithinTime("09:00", "17:00", LocalTime.of(17, 0)));
        assertFalse(ConstraintEvaluator.isWithinTime("09:00", "17:00", LocalTime.of(3, 0)));
    }

    @Test public void isWithinTimeWrapsMidnightWhenEndIsNotAfterStart() {
        assertTrue(ConstraintEvaluator.isWithinTime("22:00", "06:00", LocalTime.of(23, 0)));
        assertTrue(ConstraintEvaluator.isWithinTime("22:00", "06:00", LocalTime.of(2, 0)));
        assertFalse(ConstraintEvaluator.isWithinTime("22:00", "06:00", LocalTime.of(6, 0)));
        assertFalse(ConstraintEvaluator.isWithinTime("22:00", "06:00", LocalTime.of(12, 0)));
        assertTrue(ConstraintEvaluator.isWithinTime("08:00", "08:00", LocalTime.of(12, 0)));
    }

    @Test public void isWithinTimeDefaultsToTrueWhenMalformed() {
        assertTrue(ConstraintEvaluator.isWithinTime("9am", "17:00", LocalTime.of(3, 0)));
        assertTrue(ConstraintEvaluator.isWithinTime("09:00", "25:00", LocalTime.of(3, 0)));
        assertTrue(ConstraintEvaluator.isWithinTime(null, "17:00", LocalTime.of(3, 0)));
        assertTrue(ConstraintEvaluator.isWithinTime("", "", LocalTime.of(3, 0)));
    }

    @Test public void isDayOfWeekMatchesCommaSeparatedDays() {
        assertTrue(ConstraintEvaluator.isDayOfWeek("mon,tue,wed", DayOfWeek.TUESDAY));
        assertTrue(ConstraintEvaluator.isDayOfWeek("Sat, Sun", DayOfWeek.SUNDAY));
        assertFalse(ConstraintEvaluator.isDayOfWeek("sat,sun", DayOfWeek.MONDAY));
        assertTrue(ConstraintEvaluator.isDayOfWeek("", DayOfWeek.MONDAY));
        assertTrue(ConstraintEvaluator.isDayOfWeek(null, DayOfWeek.MONDAY));
    }

    private static final String AUDIO_NONE =
            "  Connected devices:\n"
            + "\n"
            + "  APM Connected device (A2DP sink only):\n"
            + "\n"
            + "  Current: 1 (earpiece): 6, 2 (speaker): 8, 10 (bt_sco): 9, 80 (bt_a2dp): 6, 4000000 (usb_headset): 6\n";

    @Test public void parseHeadphonesConnectedOnlyReadsTheConnectedDevicesSection() {
        assertFalse(ConstraintEvaluator.parseHeadphonesConnected(AUDIO_NONE));
        assertFalse(ConstraintEvaluator.parseHeadphonesConnected(""));
        String wired = AUDIO_NONE.replace("  Connected devices:\n",
                "  Connected devices:\n    [DeviceInfo: type:0x4 (wired_headset) name: addr: codec: 0]\n");
        assertTrue(ConstraintEvaluator.parseHeadphonesConnected(wired));
        String a2dp = AUDIO_NONE.replace("  Connected devices:\n",
                "  Connected devices:\n    [DeviceInfo: type:0x80 (bt_a2dp) name:My Earbuds addr:AA:BB:CC:DD:EE:FF codec: 1]\n");
        assertTrue(ConstraintEvaluator.parseHeadphonesConnected(a2dp));
        String hdmi = AUDIO_NONE.replace("  Connected devices:\n",
                "  Connected devices:\n    [DeviceInfo: type:0x400 (hdmi) name: addr: codec: 0]\n");
        assertFalse(ConstraintEvaluator.parseHeadphonesConnected(hdmi));
    }
}
