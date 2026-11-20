package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Notification waiting": which notifications in the shade count. */
class NotificationWaitingTest {
    private val own = "io.github.nicholasarda.keysnap"
    private val chat = WaitingNotification("chat.app", ongoing = false, silent = false)
    private val music = WaitingNotification("music.app", ongoing = true, silent = false)
    private val promo = WaitingNotification("shop.app", ongoing = false, silent = true)

    private fun waiting(params: Map<String, String>, vararg shade: WaitingNotification) =
        NotificationWaiting.matches(params, shade.toList(), own)

    @Test fun anyAppCountsAnOrdinaryNotification() {
        assertTrue(waiting(mapOf("apps" to "any"), chat))
        assertFalse(waiting(mapOf("apps" to "any")))
    }

    @Test fun silentAndOngoingAreSkippedUnlessAsked() {
        assertFalse(waiting(mapOf("apps" to "any"), music, promo))
        assertFalse("absent means skip, as for a shared or older condition", waiting(emptyMap(), music))
        assertTrue(waiting(mapOf("apps" to "any", "skip_silent" to "false"), music))
    }

    @Test fun systemNoticesAreSkippedTooOrTurningDndOnWouldKeepItTrue() {
        val dndNotice = WaitingNotification("com.android.systemui", ongoing = false, silent = false)
        assertFalse(waiting(mapOf("apps" to "any"), dndNotice))
        assertTrue(waiting(mapOf("apps" to "any", "skip_silent" to "false"), dndNotice))
    }

    @Test fun exceptAndPickNarrowTheApps() {
        assertFalse(waiting(mapOf("apps" to "any", "except" to "chat.app, shop.app"), chat))
        assertTrue(waiting(mapOf("apps" to "pick", "packages" to "chat.app"), chat))
        assertFalse(waiting(mapOf("apps" to "pick", "packages" to "other.app"), chat))
        assertFalse("pick with nothing picked never matches", waiting(mapOf("apps" to "pick"), chat))
    }

    @Test fun keySnapsOwnNotificationsNeverCount() {
        assertFalse(waiting(mapOf("apps" to "any"), WaitingNotification(own, ongoing = false, silent = false)))
    }
}
