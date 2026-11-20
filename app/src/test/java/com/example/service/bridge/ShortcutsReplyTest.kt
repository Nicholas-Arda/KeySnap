package com.example.service.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShortcutsReplyTest {
    @Test fun theListKeepsIdsAndFallsBackToTheIdForALabel() {
        assertEquals(
            listOf("new-tab" to "New tab", "x" to "x"),
            parseShortcutsReply("""[{"id":"new-tab","label":"New tab"},{"id":"x","label":""},{"id":"","label":"No id"}]"""),
        )
    }

    @Test fun anUnreadableListIsNotAnEmptyOne() {
        assertNull(parseShortcutsReply("unavailable"))
        assertNull(parseShortcutsReply(null))
        assertEquals(emptyList<Pair<String, String>>(), parseShortcutsReply("[]"))
    }
}
