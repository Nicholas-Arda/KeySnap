package com.example.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SensitiveApp
import com.example.data.SensitiveAppsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SensitiveAppsGateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val bank = SensitiveApp("com.bank", "Bank")

    @Test fun noticeOnlyWhenAskedForQuietAndClearedOnRestore() {
        val repo = SensitiveAppsRepository.getInstance(context)
        repo.updateSettings { it.copy(showNotification = false) }
        SensitiveAppsGate.onSteppedAside(context, bank)
        assertEquals(0, shadowOf(manager).allNotifications.size)

        repo.updateSettings { it.copy(showNotification = true) }
        SensitiveAppsGate.onSteppedAside(context, bank)
        assertEquals(1, shadowOf(manager).allNotifications.size)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(SensitiveAppsGate.CHANNEL_ID).importance)

        SensitiveAppsGate.onRestored(context, bank)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test fun leavingNoticeOpensKeySnapAndOffersNoRestore() {
        SensitiveAppsRepository.getInstance(context).updateSettings { it.copy(showNotification = true) }
        SensitiveAppsGate.onSteppedAside(context, bank)
        SensitiveAppsGate.onSteppedAside(context, bank, leaving = true)
        val notification = shadowOf(manager).allNotifications.single() // replaces the "aside" one
        assertEquals(context.getString(io.github.nicholasarda.keysnap.R.string.sensitive_notif_text_off), shadowOf(notification).contentText.toString())
        assertEquals(0, notification.actions?.size ?: 0)
        assertNotNull(notification.contentIntent)
        assertTrue(notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
        SensitiveAppsGate.onRestored(context, bank)
    }

    @Test fun leavingNoticeGoesWhenAdvancedModeIsBack() {
        val repository = SensitiveAppsRepository.getInstance(context)
        repository.updateSettings { it.copy(showNotification = true) }
        val wireless = bank.copy(hideWirelessDebugging = true)
        repository.add(wireless.packageName, wireless.label)
        repository.update(wireless)
        SensitiveAppsGate.onSteppedAside(context, wireless, leaving = true)
        SensitiveAppsGate.onBridgeUp(context)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
