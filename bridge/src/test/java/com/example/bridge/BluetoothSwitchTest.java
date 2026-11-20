package com.example.bridge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BluetoothSwitchTest {

    private static String dialog(String pkg, String allowId) {
        return "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"0\">"
                + "<node text=\"\" resource-id=\"\" class=\"android.widget.FrameLayout\" package=\"" + pkg + "\" clickable=\"false\" bounds=\"[0,0][1272,2772]\">"
                + "<node text=\"An app wants to turn bluetooth on\" resource-id=\"" + pkg + ":id/alertTitle\" class=\"android.widget.TextView\" package=\"" + pkg + "\" clickable=\"false\" bounds=\"[148,2300][1124,2400]\" />"
                + "<node text=\"Decline\" resource-id=\"android:id/button2\" class=\"android.widget.Button\" package=\"" + pkg + "\" clickable=\"true\" bounds=\"[148,2498][624,2629]\" />"
                + "<node text=\"Allow\" resource-id=\"" + allowId + "\" class=\"android.widget.Button\" package=\"" + pkg + "\" clickable=\"true\" bounds=\"[648,2498][1124,2629]\" />"
                + "</node></hierarchy>";
    }

    @Test public void tapsAllowOnColorOsAndAosp() {
        assertArrayEquals(new int[] {886, 2563}, BluetoothSwitch.allowButton(dialog("com.oplus.wirelesssettings", "android:id/button3")));
        assertArrayEquals(new int[] {886, 2563}, BluetoothSwitch.allowButton(dialog("com.android.settings", "android:id/button1")));
    }

    @Test public void focusMustBeTheBluetoothRequestOfAnAllowedPackage() {
        assertTrue(BluetoothSwitch.dialogFocused("  mCurrentFocus=null\n"
                + "  mCurrentFocus=Window{57b773f u0 com.oplus.wirelesssettings/com.android.settings.bluetooth.RequestPermissionActivity}\n"));
        assertFalse(BluetoothSwitch.dialogFocused("  mCurrentFocus=Window{1 u0 com.example.evil/com.example.bluetooth.RequestPermissionActivity}\n"));
        assertFalse(BluetoothSwitch.dialogFocused("  mCurrentFocus=Window{2 u0 com.android.settings/com.android.settings.Settings}\n"));
        assertFalse(BluetoothSwitch.dialogFocused(null));
    }

    @Test public void neverTapsAnotherAppsDialogOrDecline() {
        assertNull(BluetoothSwitch.allowButton(dialog("com.example.evil", "android:id/button1")));
        assertNull(BluetoothSwitch.allowButton(dialog("com.android.settings", "android:id/button2")
                .replaceFirst("android:id/button2", "android:id/other")));
        assertNull(BluetoothSwitch.allowButton("<hierarchy/>"));
    }
}
