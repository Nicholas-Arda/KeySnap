package com.example.service

import androidx.test.core.app.ApplicationProvider
import com.example.data.ScriptAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationReplyTest {
    private val older = ReplyCandidate("Annem", 1_000L, "a")
    private val newer = ReplyCandidate("İş grubu", 2_000L, "b")

    @Test fun blankChatPicksTheNewest() {
        assertEquals("b", pickReplyTarget(listOf(older, newer), "")?.action)
        assertEquals("b", pickReplyTarget(listOf(older, newer), null)?.action)
    }

    @Test fun chatFilterIsCaseInsensitiveAndPicksTheNewestMatch() {
        val newerMom = ReplyCandidate("ANNEM", 3_000L, "c")
        assertEquals("c", pickReplyTarget(listOf(older, newer, newerMom), "annem")?.action)
        assertEquals("b", pickReplyTarget(listOf(older, newer), "iş")?.action)
    }

    @Test fun noMatchingChatMeansNoReply() {
        assertNull(pickReplyTarget(listOf(older, newer), "Ali"))
        assertNull(pickReplyTarget(emptyList<ReplyCandidate<String>>(), null))
    }

    @Test fun withoutNotificationAccessTheActionFailsSafe() {
        val executor = ScriptActionExecutor.android(ApplicationProvider.getApplicationContext())
        val results = runBlocking {
            executor.execute(listOf(ScriptAction("reply_to_notification", mapOf("packageName" to "com.whatsapp", "text" to "hi"))))
        }
        assertEquals(listOf(false), results)
    }
}
