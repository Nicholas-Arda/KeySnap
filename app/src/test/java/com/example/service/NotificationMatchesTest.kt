package com.example.service

import com.example.data.ScriptConstraint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationMatchesTest {
    private val mom = ScriptConstraint("notification_received", mapOf("packageName" to "com.whatsapp", "contact" to "annem"))
    private val anyWhatsApp = ScriptConstraint("notification_received", mapOf("packageName" to "com.whatsapp"))

    private fun from(title: String, text: String = "") = NotificationContent(listOf(title), text)

    @Before fun clear() = NotificationMatches.clearForTest()

    @Test fun matchesTheChosenAppAndContactCaseInsensitively() {
        assertTrue(NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 1_000L) { from(" ANNEM ", "Eve gel") })
        assertTrue(NotificationMatches.isRecent(mom, 1_000L))
    }

    @Test fun aContactMustBeTheWholeChatName() {
        val ali = mapOf("contact" to "Ali")
        assertTrue(notificationMatches(ali, from("ali")))
        assertFalse(notificationMatches(ali, from("Alice")))
        // The conversation title counts too, as it does for a reply's chat.
        assertTrue(notificationMatches(mapOf("contact" to "Aile"), NotificationContent(listOf("Ali", "Aile"), "selam")))
    }

    @Test fun containsReadsTheMessageTextOnlyNotTheTitle() {
        val izmir = mapOf("contains" to "izmir")
        assertTrue(notificationMatches(izmir, from("Ali", "İzmir'e geldim")))
        assertFalse(notificationMatches(izmir, from("İzmir grubu", "selam")))
    }

    @Test fun bothSetMeansBothMustMatch() {
        val both = mapOf("contact" to "annem", "contains" to "eve gel")
        assertTrue(notificationMatches(both, from("Annem", "Eve gel")))
        assertFalse(notificationMatches(both, from("Annem", "Neredesin")))
        assertFalse(notificationMatches(both, from("Ali", "Eve gel")))
    }

    @Test fun neitherSetMatchesAnyNotificationFromTheApp() {
        assertTrue(NotificationMatches.record(listOf(anyWhatsApp), "com.whatsapp", "key", 0L) { from("Ali", "selam") })
        assertTrue(notificationMatches(mapOf("contact" to " ", "contains" to ""), from("", "")))
    }

    @Test fun nonMatchingContactDoesNotRecord() {
        assertFalse(NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 0L) { from("Ali", "annem") })
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
        NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 0L) { from("annem") }
        assertTrue(NotificationMatches.isRecent(mom, 9_999L))
        assertFalse(NotificationMatches.isRecent(mom, 10_000L))
    }

    @Test fun aClockEarlierThanTheMatchIsNotRecent() {
        NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 5_000L) { from("annem") }
        assertFalse(NotificationMatches.isRecent(mom, 4_000L))
    }

    @Test fun aConditionNoLongerInAnyScriptIsForgotten() {
        NotificationMatches.record(listOf(mom), "com.whatsapp", "key", 0L) { from("annem") }
        NotificationMatches.record(listOf(anyWhatsApp), "com.other", "key", 1L) { from("") }
        assertFalse(NotificationMatches.isRecent(mom, 1L))
    }

    /** The notification a blank-chat reply answers: the newest match inside the window, else none. */
    @Test fun theTriggeringKeyIsTheNewestRecentMatch() {
        NotificationMatches.record(listOf(mom, anyWhatsApp), "com.whatsapp", "k-1", 0L) { from("annem") }
        NotificationMatches.record(listOf(mom, anyWhatsApp), "com.whatsapp", "k-2", 2_000L) { from("Ali") }
        assertEquals("k-2", NotificationMatches.triggeringKey(listOf(mom, anyWhatsApp), 3_000L))
        assertEquals("k-1", NotificationMatches.triggeringKey(listOf(mom), 3_000L))
        assertNull(NotificationMatches.triggeringKey(listOf(mom), 10_000L))
        assertNull(NotificationMatches.triggeringKey(listOf(ScriptConstraint("charging")), 3_000L))
    }
}
