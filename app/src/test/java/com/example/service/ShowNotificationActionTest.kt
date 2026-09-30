package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import com.example.data.ScriptAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Drives the real entry point, [ScriptActionExecutor.android], so the dispatch is covered too. */
@RunWith(RobolectricTestRunner::class)
class ShowNotificationActionTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val executor = ScriptActionExecutor.android(context)

    private fun run(vararg params: Pair<String, String>): Boolean =
        runBlocking { executor.execute(listOf(ScriptAction("show_notification", mapOf(*params)))) }.single()

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    @Test fun postsTheUsersTitleAndTextOnItsOwnChannel() {
        assertTrue(run("title" to "Drink water", "text" to "Glass #3", "onTap" to "none"))
        val notification = posted().single()
        assertEquals(SHORTCUT_NOTIFICATION_CHANNEL_ID, notification.channelId)
        assertNotNull(manager.getNotificationChannel(SHORTCUT_NOTIFICATION_CHANNEL_ID))
        assertEquals("Drink water", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Glass #3", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNull(notification.contentIntent)
    }

    @Test fun tappingOpensTheUrlExactlyAsWritten() {
        val url = "https://example.com/hook?token=abc&x=1"
        assertTrue(run("title" to "t", "onTap" to "url", "url" to url))
        val intent = shadowOf(posted().single().contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
    }

    @Test fun tappingOpensTheChosenApp() {
        // KeySnap's own package is the one app Robolectric's package manager reliably resolves.
        assertTrue(run("title" to "t", "onTap" to "app", "packageName" to context.packageName))
        val intent = shadowOf(posted().single().contentIntent).savedIntent
        assertEquals(context.packageName, intent.component?.packageName)
    }

    @Test fun aStaleUrlIsIgnoredOnceTheTapTargetIsNothing() {
        assertTrue(run("title" to "t", "onTap" to "none", "url" to "https://old.example"))
        assertNull(posted().single().contentIntent)
    }

    @Test fun anUninstalledAppStillShowsTheNotificationWithoutATapTarget() {
        assertTrue(run("title" to "t", "onTap" to "app", "packageName" to "com.example.not.installed"))
        assertNull(posted().single().contentIntent)
    }

    @Test fun twoNotificationsInARowKeepTheirOwnTapTargets() {
        run("title" to "a", "onTap" to "url", "url" to "https://a.example")
        run("title" to "b", "onTap" to "url", "url" to "https://b.example")
        val urls = posted().map { shadowOf(it.contentIntent).savedIntent.dataString }.toSet()
        assertEquals(setOf("https://a.example", "https://b.example"), urls)
    }

    @Test fun blockedNotificationsReportFailureInsteadOfPretending() {
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(run("title" to "t"))
        assertTrue(posted().isEmpty())
    }

    @Test fun aBlankTitleIsRejected() {
        assertFalse(run("title" to "   "))
        assertTrue(posted().isEmpty())
    }
}
