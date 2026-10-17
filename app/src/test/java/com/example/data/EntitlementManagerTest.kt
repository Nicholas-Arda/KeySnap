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
    private fun manager(): EntitlementManager {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("entitlement_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        return EntitlementManager(prefs, ScriptRepository(prefs, Moshi.Builder().build()))
    }

    @Test fun freeKeepsTheFirstTwoWidgetsByPlacementAndRefusesAThird() {
        val manager = manager()
        assertTrue(manager.widgetAllowed(7, boundWidgetIds = emptyList()))
        assertTrue(manager.widgetAllowed(8, boundWidgetIds = listOf(7)))
        assertFalse(manager.widgetAllowed(9, boundWidgetIds = listOf(7, 8)))
        // Reconfiguring a widget that already has a slot keeps it.
        assertTrue(manager.widgetAllowed(8, boundWidgetIds = listOf(7, 8)))
        // Removing a widget frees its slot for the next one placed.
        assertTrue(manager.widgetAllowed(9, boundWidgetIds = listOf(8)))
    }

    @Test fun proBindsAnyNumberOfWidgets() {
        val manager = manager()
        manager.setPro(true)
        assertTrue(manager.widgetAllowed(9, boundWidgetIds = listOf(7, 8)))
    }

    @Test fun afterARefundOnlyWidgetsPastTheFirstTwoStopRunning() {
        val manager = manager()
        manager.setPro(true)
        val bound = listOf(3, 5, 9, 12)
        assertTrue(bound.all { manager.widgetAllowed(it, bound) })
        manager.setPro(false)
        assertEquals(listOf(3, 5), bound.filter { manager.widgetAllowed(it, bound) })
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
