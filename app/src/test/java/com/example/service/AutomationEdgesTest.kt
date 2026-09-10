package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEdgesTest {
    @Test fun firesOnlyOnTheFalseToTrueEdge() {
        val edges = AutomationEdges()
        assertFalse("first sighting only records", edges.update("s", true))
        assertFalse("still true is not an edge", edges.update("s", true))
        assertFalse(edges.update("s", false))
        assertTrue(edges.update("s", true))
        assertFalse("a duplicate broadcast for the same change is harmless", edges.update("s", true))
    }

    @Test fun aScriptThatStartsFalseFiresOnItsFirstRise() {
        val edges = AutomationEdges()
        assertFalse(edges.update("s", false))
        assertTrue(edges.update("s", true))
    }

    @Test fun resetForgetsEveryEdgeSoTheNextSightingOnlyRecords() {
        val edges = AutomationEdges()
        edges.update("s", false)
        edges.reset()
        assertFalse(edges.update("s", true))
    }

    @Test fun scriptsAreTrackedIndependently() {
        val edges = AutomationEdges()
        edges.update("a", false)
        edges.update("b", true)
        assertTrue(edges.update("a", true))
        assertFalse(edges.update("b", true))
    }
}
