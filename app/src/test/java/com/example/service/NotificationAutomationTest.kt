package com.example.service

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A notification is an event modelled as a condition: it fires on the edge, bursts collapse, and it re-arms. */
@RunWith(RobolectricTestRunner::class)
class NotificationAutomationTest {
    private lateinit var context: Context
    private val condition = ScriptConstraint("notification_received", mapOf("packageName" to "com.whatsapp", "contains" to "annem"))

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NotificationMatches.clearForTest()
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        ScriptRepository.getInstance(context).delete("qa-notify-2")
        ScriptRepository.getInstance(context).delete("qa-notify-self")
        ScriptRepository.getInstance(context).upsert(script("qa-notify"))
        idle()
    }

    @Test fun aMatchingNotificationFiresOnce() {
        val before = count()
        post("com.whatsapp", "Annem\nEve gel")
        assertEquals(before + 1, count())
    }

    @Test fun aBurstInsideTheWindowCollapsesIntoOneRun() {
        val before = count()
        post("com.whatsapp", "Annem\nEve gel")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        post("com.whatsapp", "Annem\nNeredesin")
        assertEquals(before + 1, count())
    }

    @Test fun aMatchAfterTheWindowFiresAgain() {
        val before = count()
        post("com.whatsapp", "Annem\nEve gel")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        post("com.whatsapp", "Annem\nNeredesin")
        assertEquals(before + 2, count())
    }

    @Test fun otherAppsAndNonMatchingTextDoNotFire() {
        val before = count()
        post("org.telegram.messenger", "Annem\nEve gel")
        post("com.whatsapp", "Ali\nselam")
        assertEquals(before, count())
    }

    @Test fun twoScriptsWithTheSameConditionBothFire() {
        ScriptRepository.getInstance(context).upsert(script("qa-notify-2"))
        idle()
        val before = count()
        post("com.whatsapp", "Annem\nEve gel")
        assertEquals(before + 2, count())
    }

    /** A shortcut that shows a notification (show_notification, run-failure alerts) must never trigger itself. */
    @Test fun keySnapsOwnNotificationsNeverMatch() {
        val own = ScriptConstraint("notification_received", mapOf("packageName" to context.packageName))
        ScriptRepository.getInstance(context).upsert(script("qa-notify-self", own))
        idle()
        val before = count()
        post(context.packageName, "Annem\nEve gel")
        assertEquals(before, count())
    }

    private fun script(id: String, constraint: ScriptConstraint = condition) = ShortcutScript(
        id = id,
        name = id,
        triggers = emptyList(),
        actions = listOf(ScriptAction("toggle_flashlight")),
        constraints = listOf(constraint),
        automation = true,
        vibrateOnTrigger = false,
    )

    private fun post(packageName: String, content: String) {
        NotificationEventListener.onPosted(context, packageName) { content }
        idle()
    }

    private fun count() = HardwareKeyTriggerCoordinator.triggerCount.value

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
