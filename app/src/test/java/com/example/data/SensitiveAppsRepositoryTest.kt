package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SensitiveAppsRepositoryTest {
    private lateinit var prefs: SharedPreferences
    private fun repository() = SensitiveAppsRepository(prefs, Moshi.Builder().build())

    @Before fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("sensitive_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test fun addUpdateRemoveSurviveReload() {
        val repo = repository()
        repo.add("com.bank", "Bank")
        repo.add("com.game", "Game")
        assertEquals(listOf(SensitiveApp("com.bank", "Bank", true, true), SensitiveApp("com.game", "Game", true, true)), repo.store.value.apps)

        repo.update(SensitiveApp("com.bank", "Bank", hideDeveloperOptions = true, hideAccessibility = false))
        // Adding an app already listed keeps its choices.
        repo.add("com.bank", "Renamed")
        assertEquals(SensitiveApp("com.bank", "Bank", true, false), repository().store.value.apps.first())

        repo.remove("com.game")
        assertEquals(listOf("com.bank"), repository().store.value.apps.map { it.packageName })
    }

    @Test fun settingsAndCardDismissalSurviveReload() {
        val repo = repository()
        assertEquals(SensitiveAppsSettings(masterEnabled = true, showNotification = false, vibrate = false, vibrateMs = 60), repo.store.value.settings)
        assertFalse(repo.store.value.homeCardDismissed)

        repo.updateSettings { it.copy(masterEnabled = false, showNotification = true, vibrate = true, vibrateMs = 80) }
        repo.dismissHomeCard()

        val reloaded = repository().store.value
        assertEquals(SensitiveAppsSettings(masterEnabled = false, showNotification = true, vibrate = true, vibrateMs = 80), reloaded.settings)
        assertTrue(reloaded.homeCardDismissed)
    }

    @Test fun missingOrUnreadableDataStartsEmptyAndRecovers() {
        assertEquals(SensitiveAppsStore(), repository().store.value)
        prefs.edit().putString("sensitive_apps_v1", "{not json").commit()
        assertEquals(SensitiveAppsStore(), repository().store.value)
        // The next write replaces the garbage.
        repository().add("com.bank", "Bank")
        assertEquals(1, repository().store.value.apps.size)
    }

    @Test fun fieldsMissingFromOlderDataTakeTheSafeDefaults() {
        prefs.edit().putString("sensitive_apps_v1", """{"apps":[{"packageName":"com.bank","label":"Bank"}]}""").commit()
        val store = repository().store.value
        assertEquals(listOf(SensitiveApp("com.bank", "Bank", true, true)), store.apps)
        assertEquals(SensitiveAppsSettings(), store.settings)
    }

    @Test fun listsSavedBeforeWirelessDebuggingLoadUnchangedWithItOff() {
        prefs.edit().putString(
            "sensitive_apps_v1",
            """{"apps":[{"packageName":"com.bank","label":"Bank","hideDeveloperOptions":false,"hideAccessibility":true}],"settings":{"showNotification":true}}""",
        ).commit()
        val store = repository().store.value
        assertEquals(listOf(SensitiveApp("com.bank", "Bank", hideDeveloperOptions = false, hideAccessibility = true, hideWirelessDebugging = false)), store.apps)
        assertTrue(store.settings.showNotification)

        repository().update(store.apps.single().copy(hideWirelessDebugging = true))
        assertTrue(repository().store.value.apps.single().hideWirelessDebugging)
    }

    @Test fun advancedModeEndedFlagSurvivesReloadAndOlderDataLoadsWithItOff() {
        prefs.edit().putString("sensitive_apps_v1", """{"apps":[{"packageName":"com.bank","label":"Bank"}],"homeCardDismissed":true}""").commit()
        val old = repository().store.value
        assertFalse(old.advancedModeEnded)
        assertTrue(old.homeCardDismissed)

        repository().setAdvancedModeEnded(true)
        assertTrue(repository().store.value.advancedModeEnded)
        assertEquals(listOf("com.bank"), repository().store.value.apps.map { it.packageName })
        repository().setAdvancedModeEnded(false)
        assertFalse(repository().store.value.advancedModeEnded)
    }

    @Test fun activeAppsHonourMasterSwitchAndSkipAppsWithNothingToHide() {
        val bank = SensitiveApp("com.bank", "Bank")
        val idle = SensitiveApp("com.idle", "Idle", hideDeveloperOptions = false, hideAccessibility = false)
        val wifiOnly = SensitiveApp("com.bankid", "BankID", hideDeveloperOptions = false, hideAccessibility = false, hideWirelessDebugging = true)
        assertEquals(listOf(wifiOnly), SensitiveAppsStore(apps = listOf(idle, wifiOnly)).activeApps)
        val store = SensitiveAppsStore(apps = listOf(bank, idle))
        assertEquals(listOf(bank), store.activeApps)
        assertEquals(emptyList<SensitiveApp>(), store.copy(settings = SensitiveAppsSettings(masterEnabled = false)).activeApps)
    }
}
