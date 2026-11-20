package com.example.ui.scripts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPromptTest {
    @Test fun followsColorOsPermissionMonitorThenBrand() {
        assertTrue(bluetoothPromptExpected("true", "Google"))
        assertFalse(bluetoothPromptExpected("false", "OPPO"))
        assertTrue(bluetoothPromptExpected(null, "OPPO"))
        assertTrue(bluetoothPromptExpected(null, "OnePlus"))
        assertFalse(bluetoothPromptExpected(null, "samsung"))
    }
}
