package com.example.service.adb

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetEventParserTest {
    @Test
    fun parsesTimestampDeviceAndSymbolicPowerDown() {
        val event = GetEventParser.parse("[  123.456789] /dev/input/event2: EV_KEY       KEY_POWER        DOWN")

        assertNotNull(event)
        assertEquals("/dev/input/event2", event!!.device)
        assertEquals("KEY_POWER", event.kernelName)
        assertEquals(116, event.linuxCode)
        assertEquals(KeyEvent.KEYCODE_POWER, event.androidKeyCode)
        assertEquals(ParsedGetEventKey.Action.DOWN, event.action)
    }

    @Test
    fun parsesRawHexCodeAndUpValue() {
        val event = GetEventParser.parse("/dev/input/event4: 0001 0073 00000000")

        assertNotNull(event)
        assertEquals(115, event!!.linuxCode)
        assertEquals(KeyEvent.KEYCODE_VOLUME_UP, event.androidKeyCode)
        assertEquals(ParsedGetEventKey.Action.UP, event.action)
    }

    @Test
    fun repeatIsRepresentedSeparatelyFromRelease() {
        val event = GetEventParser.parse("/dev/input/event0: EV_KEY KEY_WAKEUP REPEAT")

        assertEquals(KeyEvent.KEYCODE_WAKEUP, event!!.androidKeyCode)
        assertEquals(ParsedGetEventKey.Action.REPEAT, event.action)
    }

    @Test
    fun unknownOemNamesReceiveStablePositiveSyntheticCodes() {
        val first = GetEventParser.parse("/dev/input/event9: EV_KEY KEY_VENDOR DOWN")!!
        val again = GetEventParser.parse("EV_KEY KEY_VENDOR UP")!!
        val other = GetEventParser.parse("EV_KEY KEY_PROG1 DOWN")!!

        assertTrue(first.androidKeyCode > 0)
        assertEquals(first.androidKeyCode, again.androidKeyCode)
        assertNotEquals(first.androidKeyCode, other.androidKeyCode)
        assertEquals("KEY_VENDOR", first.kernelName)
    }

    @Test
    fun parsesGeteventLtParenthesizedTimestamp() {
        val event = GetEventParser.parse("(  123.456789) /dev/input/event7: EV_KEY KEY_PROG1 DOWN")

        assertNotNull(event)
        assertEquals("/dev/input/event7", event!!.device)
        assertEquals(148, event.linuxCode)
        assertEquals(ParsedGetEventKey.Action.DOWN, event.action)
    }

    @Test
    fun preservesUnknownBtnNamesForOemButtons() {
        val down = GetEventParser.parse("(  12.000001) /dev/input/event8: EV_KEY BTN_TRIGGER_HAPPY1 DOWN")!!
        val up = GetEventParser.parse("(  12.100001) /dev/input/event8: EV_KEY BTN_TRIGGER_HAPPY1 UP")!!

        assertEquals("BTN_TRIGGER_HAPPY1", down.kernelName)
        assertEquals(down.androidKeyCode, up.androidKeyCode)
        assertTrue(down.androidKeyCode > 0)
    }

    @Test
    fun filtersTouchContactsButKeepsPhysicalButtons() {
        val touch = GetEventParser.parse("/dev/input/event2: EV_KEY BTN_TOUCH DOWN")!!
        val stylus = GetEventParser.parse("/dev/input/event2: EV_KEY BTN_TOOL_FINGER DOWN")!!
        val camera = GetEventParser.parse("/dev/input/event4: EV_KEY KEY_CAMERA DOWN")!!

        assertTrue(touch.isTouchContact)
        assertTrue(stylus.isTouchContact)
        assertTrue(!camera.isTouchContact)
    }

    @Test
    fun ignoresNonKeyAndMalformedLines() {
        assertNull(GetEventParser.parse("/dev/input/event2: EV_ABS ABS_MT_POSITION_X 00000001"))
        assertNull(GetEventParser.parse("/dev/input/event2: EV_KEY KEY_POWER UNKNOWN"))
    }
}
