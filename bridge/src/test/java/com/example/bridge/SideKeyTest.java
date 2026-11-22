package com.example.bridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class SideKeyTest {
    @Test public void aPhoneWithoutTheSideKeySettingSendsNothing() {
        assertFalse(SideKey.isKnownValue(null));
        assertFalse(SideKey.isKnownValue(""));
        assertFalse(SideKey.isKnownValue("null"));
    }

    @Test public void onlyAPlainSettingValueIsWrittenBack() {
        assertTrue(SideKey.isKnownValue("nothing"));
        assertTrue(SideKey.isKnownValue("flash_memory"));
        assertFalse(SideKey.isKnownValue("nothing; reboot"));
    }
}
