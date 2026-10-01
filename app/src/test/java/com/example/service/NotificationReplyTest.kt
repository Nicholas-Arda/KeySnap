package com.example.service

import androidx.test.core.app.ApplicationProvider
import com.example.data.ScriptAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationReplyTest {
    private val mom = ReplyCandidate("k-mom", listOf("Annem"), 1_000L, "a")
    private val work = ReplyCandidate("k-work", listOf("Ayşe", "İş grubu"), 2_000L, "b")
    private val alice = ReplyCandidate("k-alice", listOf("Alice"), 3_000L, "c")

    /** Blank never means "the newest chat": only the notification that started the run. */
    @Test fun blankChatAnswersOnlyTheTriggeringNotification() {
        assertEquals("a", pickReplyTarget(listOf(mom, work, alice), "", "k-mom")?.action)
        assertEquals("b", pickReplyTarget(listOf(mom, work, alice), null, "k-work")?.action)
        assertNull(pickReplyTarget(listOf(mom, work, alice), "", null))
        assertNull(pickReplyTarget(listOf(mom, work, alice), "  ", null))
        // The triggering notification is gone (opened or swiped away): no fallback to another chat.
        assertNull(pickReplyTarget(listOf(mom, work), null, "k-alice"))
    }

    @Test fun aNamedChatMatchesExactlyIgnoringCaseAndSpaces() {
        assertEquals("a", pickReplyTarget(listOf(mom, work, alice), " ANNEM ", null)?.action)
        assertEquals("b", pickReplyTarget(listOf(mom, work, alice), "iş grubu", null)?.action)
        assertEquals("b", pickReplyTarget(listOf(mom, work, alice), "Ayşe", null)?.action)
        // A named chat wins over the triggering notification.
        assertEquals("a", pickReplyTarget(listOf(mom, work, alice), "Annem", "k-work")?.action)
    }

    @Test fun aNamedChatIsNeverASubstringMatch() {
        assertNull(pickReplyTarget(listOf(mom, work, alice), "Ali", null))
        assertNull(pickReplyTarget(listOf(mom, work, alice), "iş", null))
        assertNull(pickReplyTarget(emptyList<ReplyCandidate<String>>(), "Annem", null))
    }

    @Test fun theNewestOfTwoSameNamedChatsIsAnswered() {
        val newerMom = ReplyCandidate("k-mom-2", listOf("annem"), 4_000L, "d")
        assertEquals("d", pickReplyTarget(listOf(mom, newerMom), "Annem", null)?.action)
    }

    @Test fun withoutNotificationAccessTheActionFailsSafe() {
        val executor = ScriptActionExecutor.android(ApplicationProvider.getApplicationContext())
        val results = runBlocking {
            executor.execute(listOf(ScriptAction("reply_to_notification", mapOf("packageName" to "com.whatsapp", "chat" to "Annem", "text" to "hi"))))
        }
        assertEquals(listOf(false), results)
    }

    /** The run's triggering notification reaches the reply, and only for that run. */
    @Test fun theExecutorHandsTheTriggerKeyToRepliesOnly() {
        val seen = mutableListOf<ScriptAction>()
        val executor = ScriptActionExecutor { seen.add(it); true }
        val reply = ScriptAction("reply_to_notification", mapOf("packageName" to "p", "text" to "hi"))
        val torch = ScriptAction("toggle_flashlight")
        runBlocking { executor.executeEach(listOf(reply, torch, ScriptAction("repeat_previous", mapOf("count" to "1"))), "k-mom") }
        assertEquals("k-mom", seen[0].params[TRIGGER_NOTIFICATION_PARAM])
        assertEquals(torch, seen[1])
        assertEquals(emptyMap<String, String>(), seen[2].params)
    }

    /** A stored copy of the param (a crafted import, an old save) never aims a reply: it is replaced. */
    @Test fun aStoredTriggerKeyIsNeverTrusted() {
        val seen = mutableListOf<ScriptAction>()
        val executor = ScriptActionExecutor { seen.add(it); true }
        val planted = ScriptAction("reply_to_notification", mapOf("text" to "hi", TRIGGER_NOTIFICATION_PARAM to "k-planted"))
        runBlocking { executor.execute(listOf(planted)) }
        assertFalse(TRIGGER_NOTIFICATION_PARAM in seen.single().params)
        runBlocking { executor.execute(listOf(planted), "k-real") }
        assertEquals("k-real", seen.last().params[TRIGGER_NOTIFICATION_PARAM])
    }
}
