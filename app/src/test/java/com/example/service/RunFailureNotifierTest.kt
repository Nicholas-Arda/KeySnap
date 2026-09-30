package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActionResult
import com.example.data.RunRecord
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import io.github.nicholasarda.keysnap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RunFailureNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val script = ShortcutScript(
        id = "s1",
        name = "Wi-Fi key",
        triggers = listOf(ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)),
        actions = listOf(ScriptAction("toggle_wifi")),
    )

    private fun run(ok: Boolean) = RunRecord(
        "s1", 1L, RunRecord.SOURCE_KEY, 24, "SINGLE_PRESS",
        listOf(ActionResult("toggle_wifi", ok, if (ok) null else "ADVANCED_MODE_OFF")),
    )

    @Test fun repeatedFailuresKeepOneNotificationThatSaysWhyAndOpensTheShortcut() {
        RunFailureNotifier.onRun(context, script, run(ok = false))
        RunFailureNotifier.onRun(context, script, run(ok = false))

        val posted = shadowOf(manager).allNotifications
        assertEquals(1, posted.size)
        val text = posted.single().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(text, text.contains(context.getString(R.string.badge_expert_mode)))
        val tap = shadowOf(posted.single().contentIntent).savedIntent
        assertEquals("s1", tap.getStringExtra(RunFailureNotifier.EXTRA_SCRIPT_ID))
    }

    @Test fun aCleanRunClearsTheEarlierAlert() {
        RunFailureNotifier.onRun(context, script, run(ok = false))
        RunFailureNotifier.onRun(context, script, run(ok = true))
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test fun blockedNotificationsPostNothing() {
        shadowOf(manager).setNotificationsEnabled(false)
        RunFailureNotifier.onRun(context, script, run(ok = false))
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
