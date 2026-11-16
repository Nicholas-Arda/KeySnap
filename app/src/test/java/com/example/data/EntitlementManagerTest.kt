package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntitlementManagerTest {
    @Test fun acceptanceProRequiresExplicitOptInAndTheExactDevPackage() {
        assertTrue(capsuleAcceptanceProEnabled(true, "io.github.nicholasarda.keysnap.dev"))
        assertFalse(capsuleAcceptanceProEnabled(false, "io.github.nicholasarda.keysnap.dev"))
        assertFalse(capsuleAcceptanceProEnabled(true, "io.github.nicholasarda.keysnap"))
        assertFalse(capsuleAcceptanceProEnabled(true, "other.app.dev"))
    }

    @Test fun testAccessSurvivesANegativeBillingRefreshWithoutPersistingFakeOwnership() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("acceptance_entitlement_${System.nanoTime()}", Context.MODE_PRIVATE)
        val repository = ScriptRepository(prefs, Moshi.Builder().build())
        val acceptance = EntitlementManager(prefs, repository, acceptancePro = true)
        assertTrue(acceptance.isPro.value)
        assertFalse(prefs.getBoolean("is_pro", false))
        acceptance.setPro(false)
        assertTrue(acceptance.isPro.value)
        assertFalse(prefs.getBoolean("is_pro", true))
        assertFalse(EntitlementManager(prefs, repository).isPro.value)
    }

    @Test fun freeKeepsThreeActiveScriptsAndPausesTheFourth() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("entitlement_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        val repository = ScriptRepository(prefs, Moshi.Builder().build())
        val manager = EntitlementManager(prefs, repository)
        (1..4).forEach { index ->
            repository.upsert(ShortcutScript(
                id = "script-$index",
                name = "Shortcut $index",
                enabled = true,
                triggers = emptyList(),
                actions = listOf(ScriptAction.ToggleFlashlight),
            ))
        }
        assertEquals(3, manager.activeScriptLimit)
        manager.enforceLimit()
        assertEquals(listOf("script-1", "script-2", "script-3"), repository.scripts.value.filter { it.enabled }.map { it.id })
        assertFalse(repository.scripts.value.last().enabled)
    }

    @Test fun proKeepsAllScriptsAndRefundKeepsTheFirstThree() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("entitlement_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        val repository = ScriptRepository(prefs, Moshi.Builder().build())
        val manager = EntitlementManager(prefs, repository)
        (1..5).forEach { index ->
            repository.upsert(ShortcutScript(
                id = "script-$index",
                name = "Shortcut $index",
                enabled = true,
                triggers = emptyList(),
                actions = listOf(ScriptAction.ToggleFlashlight),
            ))
        }
        manager.setPro(true)
        assertEquals(Int.MAX_VALUE, manager.activeScriptLimit)
        manager.enforceLimit()
        assertEquals(5, repository.scripts.value.count { it.enabled })
        manager.setPro(false)
        manager.enforceLimit()
        assertEquals(listOf("script-1", "script-2", "script-3"), repository.scripts.value.filter { it.enabled }.map { it.id })
    }

    @Test fun obsoleteExpiryPreferenceIsIgnoredIncludingAnUnexpectedType() {
        listOf(0L, Long.MAX_VALUE, "obsolete").forEach { oldValue ->
            val prefs = ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences("entitlement_test_${System.nanoTime()}", Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (oldValue is Long) putLong("reward_expiry_at", oldValue)
                else putString("reward_expiry_at", oldValue.toString())
            }.commit()
            val manager = EntitlementManager(prefs, ScriptRepository(prefs, Moshi.Builder().build()))
            assertEquals(3, manager.activeScriptLimit)
        }
    }
}
