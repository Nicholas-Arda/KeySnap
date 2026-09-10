package com.example.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Locks the app/bridge key-code contract.
 *
 * A key recorded in the app is persisted using the code produced here and later matched by the
 * detached bridge using the same class. These assertions pin the values so a refactor cannot
 * silently invalidate every mapping a user has already saved.
 */
public class KeyEventMappingTest {

    @Test
    public void decodesNamedHardwareKeys() {
        KeyEventMapping.ParsedKey parsed =
                KeyEventMapping.parse("/dev/input/event1: EV_KEY KEY_VOLUMEDOWN DOWN");
        assertEquals(25, parsed.androidKeyCode);
        assertEquals(KeyEventMapping.ACTION_DOWN, parsed.action);
        assertEquals("/dev/input/event1", parsed.device);
        assertEquals(Integer.valueOf(114), parsed.linuxCode);
    }

    @Test
    public void decodesTimestampedGeteventOutput() {
        KeyEventMapping.ParsedKey parsed =
                KeyEventMapping.parse("[   12345.678901] /dev/input/event0: EV_KEY KEY_POWER UP");
        assertEquals(26, parsed.androidKeyCode);
        assertEquals(KeyEventMapping.ACTION_UP, parsed.action);
    }

    @Test
    public void decodesRawHexCodes() {
        // 0x0072 is 114, KEY_VOLUMEDOWN, reported numerically when getevent has no label.
        KeyEventMapping.ParsedKey parsed =
                KeyEventMapping.parse("/dev/input/event1: 0001 0072 00000001");
        assertEquals(25, parsed.androidKeyCode);
        assertEquals(KeyEventMapping.ACTION_DOWN, parsed.action);
    }

    @Test
    public void repeatIsDistinguishedFromDown() {
        assertEquals(KeyEventMapping.ACTION_REPEAT,
                KeyEventMapping.parse("/dev/input/event1: EV_KEY KEY_VOLUMEUP 00000002").action);
    }

    @Test
    public void touchContactsAreFlagged() {
        assertTrue(KeyEventMapping.parse("/dev/input/event2: EV_KEY BTN_TOUCH DOWN").touchContact);
        assertTrue(KeyEventMapping.parse("/dev/input/event2: EV_KEY BTN_TOOL_FINGER DOWN").touchContact);
    }

    @Test
    public void nonKeyLinesAreRejected() {
        assertNull(KeyEventMapping.parse("add device 1: /dev/input/event7"));
        assertNull(KeyEventMapping.parse("/dev/input/event2: EV_ABS ABS_MT_POSITION_X 000004a1"));
        assertNull(KeyEventMapping.parse(""));
        assertNull(KeyEventMapping.parse(null));
    }

    @Test
    public void syntheticCodesAreStableAndDistinct() {
        // An unlabelled OEM button must keep one code across runs, processes and reboots,
        // otherwise a saved mapping stops matching after a restart.
        int first = KeyEventMapping.parse("/dev/input/event4: EV_KEY KEY_F13 DOWN").androidKeyCode;
        int again = KeyEventMapping.parse("/dev/input/event4: EV_KEY KEY_F13 UP").androidKeyCode;
        assertEquals(first, again);

        int other = KeyEventMapping.parse("/dev/input/event4: EV_KEY KEY_F14 DOWN").androidKeyCode;
        assertNotEquals(first, other);

        // Synthetic codes live above the real KeyEvent range so they cannot collide with one.
        assertTrue((first & 0x40000000) != 0);
    }

    @Test
    public void sameLinuxCodeMapsToSameSyntheticCodeRegardlessOfLabel() {
        int byName = KeyEventMapping.parse("/dev/input/event4: EV_KEY KEY_PROG1 DOWN").androidKeyCode;
        int byHex = KeyEventMapping.parse("/dev/input/event4: EV_KEY 0094 00000001").androidKeyCode;
        // KEY_PROG1 is Linux code 148 == 0x94; both spellings must resolve identically or the
        // same physical button would map differently depending on getevent's labelling.
        assertEquals(byName, byHex);
    }
}
