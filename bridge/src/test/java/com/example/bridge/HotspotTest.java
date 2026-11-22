package com.example.bridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class HotspotTest {
    @Test public void explicitOnAndOffNeedNoReading() {
        assertEquals(Boolean.TRUE, Hotspot.target("on", null));
        assertEquals(Boolean.FALSE, Hotspot.target("off", null));
        assertEquals(Boolean.FALSE, Hotspot.target("off", Hotspot.WIFI_AP_STATE_DISABLED));
    }

    @Test public void toggleFollowsTheSoftApState() {
        assertEquals(Boolean.FALSE, Hotspot.target("toggle", Hotspot.WIFI_AP_STATE_ENABLED));
        assertEquals(Boolean.FALSE, Hotspot.target(null, Hotspot.WIFI_AP_STATE_ENABLING));
        assertEquals(Boolean.TRUE, Hotspot.target("toggle", Hotspot.WIFI_AP_STATE_DISABLED));
        assertEquals(Boolean.TRUE, Hotspot.target("", Hotspot.WIFI_AP_STATE_DISABLING));
        assertEquals(Boolean.TRUE, Hotspot.target("toggle", Hotspot.WIFI_AP_STATE_FAILED));
    }

    /** Fail rather than guess: an unreadable or unknown state turns nothing on or off. */
    @Test public void toggleWithoutAKnownStateFails() {
        assertNull(Hotspot.target("toggle", null));
        assertNull(Hotspot.target("toggle", 0));
        assertNull(Hotspot.target(null, 99));
    }
}
