package com.example.service

import com.example.data.ScriptConstraint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationMatchesTest {
    private val mom = ScriptConstraint("notification_received", mapOf("packageName" to "com.whatsapp", "contains" to "annem"))
    private val anyWhatsApp = ScriptConstraint("notification_received", mapOf("packageName" to "com.whatsapp"))

    @Before fun clear() = NotificationMatches.clearForTest()

    @Test fun matchesTheChosenAppAndTextCaseInsensitively() {
        assertTrue(NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 1_000L) { "ANNEM\nEve gel" })
        assertTrue(NotificationMatches.isRecent(mom, 1_000L))
    }

    @Test fun turkishDottedCapitalIMatchesPlainI() {
        val izmir = ScriptConstraint("notification_received", mapOf("packageName" to "com.whatsapp", "contains" to "izmir"))
        assertTrue(NotificationMatches.record(listOf(izmir), "com.whatsapp", "key", 0L) { "İzmir grubu" })
    }

    @Test fun blankTextMatchesAnyNotificationFromTheApp() {
        assertTrue(NotificationMatches.record(listOf(anyWhatsApp), "com.whatsapp", "key", 0L) { "Ali\nselam" })
    }

    @Test fun nonMatchingTextDoesNotRecord() {
        assertFalse(NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 0L) { "Ali\nselam" })
        assertFalse(NotificationMatches.isRecent(mom, 0L))
    }

    @Test fun anotherAppsContentIsNeverRead() {
        assertFalse(
            NotificationMatches.record(listOf(mom), "org.telegram.messenger", "key", 0L) {
                throw AssertionError("read the content of an app no condition names")
            },
        )
    }

    @Test fun aMatchIsRecentForTenSecondsOnly() {
        NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 0L) { "annem" }
        assertTrue(NotificationMatches.isRecent(mom, 9_999L))
        assertFalse(NotificationMatches.isRecent(mom, 10_000L))
    }

    @Test fun aClockEarlierThanTheMatchIsNotRecent() {
        NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 5_000L) { "annem" }
        assertFalse(NotificationMatches.isRecent(mom, 4_000L))
    }

    @Test fun aConditionNoLongerInAnyScriptIsForgotten() {
        NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 0L) { "annem" }
        NotificationMatches.record(listOf(anyWhatsApp), "com.other", "key", 1L) { "" }
        assertFalse(NotificationMatches.isRecent(mom, 1L))
    }

    /** The notification a blank-chat reply answers: the newest match inside the window, else none. */
    @Test fun theTriggeringKeyIsTheNewestRecentMatch() {
        NotificationMatches.record(listOf(mom, anyWhatsApp), "com.whatsapp", "k-1", 0L) { "annem" }
        NotificationMatches.record(listOf(mom, anyWhatsApp), "com.whatsapp", "k-2", 2_000L) { "Ali" }
        assertEquals("k-2", NotificationMatches.triggeringKey(listOf(mom, anyWhatsApp), 3_000L))
        assertEquals("k-1", NotificationMatches.triggeringKey(listOf(mom), 3_000L))
        assertNull(NotificationMatches.triggeringKey(listOf(mom), 10_000L))
        assertNull(NotificationMatches.triggeringKey(listOf(ScriptConstraint("charging")), 3_000L))
    }
}
