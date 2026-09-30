package com.example.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * The TRIGGER line's per-action field is decoded by the app with this same class, so a bridge and
 * the app that launched it can never disagree about its format.
 */
public class BridgeProtocolTest {

    @Test public void actionResultsRoundTripInScriptOrder() {
        String field = BridgeProtocol.encodeActionResults(
                Arrays.asList("toggle_wifi", "delay", "toggle_wifi"), new boolean[] {true, true, false});
        assertEquals("toggle_wifi=1,delay=1,toggle_wifi=0", field);

        List<Map.Entry<String, Boolean>> decoded = BridgeProtocol.decodeActionResults(field);
        assertEquals(3, decoded.size());
        assertEquals("toggle_wifi", decoded.get(0).getKey());
        assertTrue(decoded.get(0).getValue());
        assertEquals("toggle_wifi", decoded.get(2).getKey());
        assertFalse(decoded.get(2).getValue());
    }

    @Test public void aMissingFieldFromAnOlderBridgeDecodesToNull() {
        assertNull(BridgeProtocol.decodeActionResults(null));
    }

    @Test public void aMalformedFieldDecodesToNullRatherThanAPartialList() {
        assertNull(BridgeProtocol.decodeActionResults("toggle_wifi=1,delay"));
        assertNull(BridgeProtocol.decodeActionResults("toggle_wifi=yes"));
        assertNull(BridgeProtocol.decodeActionResults("=1"));
    }

    @Test public void noActionsIsAnEmptyListNotNull() {
        assertEquals(0, BridgeProtocol.decodeActionResults("").size());
    }
}
