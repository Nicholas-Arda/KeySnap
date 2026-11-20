package com.example.bridge;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DndArgumentTest {
    @Test public void onlyKnownTypesPassThroughAndEverythingElseIsPriority() {
        assertEquals("alarms", BridgeMain.dndArgument("alarms"));
        assertEquals("none", BridgeMain.dndArgument("none"));
        assertEquals("priority", BridgeMain.dndArgument("priority"));
        assertEquals("priority", BridgeMain.dndArgument(null));
        assertEquals("priority", BridgeMain.dndArgument("off; reboot"));
    }

    /** Alarms/none straight to another type strands the ringer on Silent, so those pass through off. */
    @Test public void onlyATypeChangeWhileOnPassesThroughOff() {
        assertTrue(BridgeMain.switchesDndType("3", "priority"));
        assertTrue(BridgeMain.switchesDndType("2", "alarms"));
        assertTrue(BridgeMain.switchesDndType("1", "none"));
        assertFalse(BridgeMain.switchesDndType("0", "priority"));
        assertFalse(BridgeMain.switchesDndType("1", "priority"));
        assertFalse(BridgeMain.switchesDndType("3", "alarms"));
        assertFalse(BridgeMain.switchesDndType("3", "off"));
        assertFalse(BridgeMain.switchesDndType("", "priority"));
    }
}
