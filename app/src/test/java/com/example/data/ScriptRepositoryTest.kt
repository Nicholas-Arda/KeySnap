package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScriptRepositoryTest {
    private lateinit var context: Context
    private fun repository() = ScriptRepository(
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE), Moshi.Builder().build()
    )

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun migratesLegacyMappingExactlyOnceAndPreservesSettings() {
        val prefs = context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("target_key_code", 24).putString("press_type", "LONG_PRESS")
            .putBoolean("is_enabled", false).putBoolean("consume_original", false).commit()

        val repository = repository()
        assertFalse(repository.globallyEnabled.value)
        assertEquals(24, repository.scripts.value.single().triggers.single().keyCodes.single())
        assertEquals(TriggerPressType.LONG_PRESS, repository.scripts.value.single().triggers.single().pressType)
        assertFalse(repository.shouldConsume(24))

        prefs.edit().putInt("target_key_code", 25).commit()
        val reloaded = repository()
        assertEquals(24, reloaded.scripts.value.single().triggers.single().keyCodes.single())
    }

    @Test fun upsertPersistsTypedMultipleScriptsAndGlobalPauseControlsConsumption() {
        val repository = repository()
        repository.setGloballyEnabled(true)
        repository.upsert(ShortcutScript("one", "One", true, listOf(ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)), listOf(ScriptAction.ToggleFlashlight)))
        repository.upsert(ShortcutScript("two", "Two", true, listOf(ScriptTrigger(listOf(25), TriggerPressType.DOUBLE_PRESS)), listOf(ScriptAction.ToggleFlashlight)))
        assertEquals(listOf("one", "two"), repository.scripts.value.map { it.id })
        assertTrue(repository.shouldConsume(25))
        repository.setScriptEnabled("two", false)
        assertFalse(repository.shouldConsume(25))
        assertEquals(listOf("one", "two"), repository().scripts.value.map { it.id })
    }

    @Test fun aV1StoreWrittenBeforeParameterizedActionsExistedRoundTripsUnchanged() {
        // Simulates a document persisted by the pre-v2 app: no "params" on actions, no per-trigger
        // timing overrides. Moshi must fill the new fields with their defaults rather than fail.
        val v1Json = """
            {"version":1,"globallyEnabled":true,"scripts":[
              {"id":"legacy","name":"Legacy","enabled":true,
               "triggers":[{"keyCodes":[24],"pressType":"DOUBLE_PRESS"}],
               "actions":[{"type":"toggle_flashlight"}]}
            ]}
        """.trimIndent()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit()
            .putString("shortcut_scripts_v1", v1Json).commit()

        val script = repository().scripts.value.single()
        assertEquals("legacy", script.id)
        assertEquals(ScriptAction.ToggleFlashlight, script.actions.single())
        assertEquals(24, script.triggers.single().keyCodes.single())
        assertNull(script.triggers.single().doublePressWindowMs)
    }

    @Test fun aScriptWithActionsButNoTriggerSurvivesAsADraft() {
        val repository = repository()
        repository.upsert(ShortcutScript("draft", "Draft", true, emptyList(), listOf(ScriptAction.ToggleFlashlight)))
        assertTrue(repository.scripts.value.single().triggers.isEmpty())
        assertTrue(repository().scripts.value.single().triggers.isEmpty())
        // A keyless script can never match, and must not affect other keys' consumption.
        assertFalse(repository.shouldConsume(24))
    }

    @Test fun parameterizedActionsAndTimingOverridesRoundTrip() {
        val repository = repository()
        val script = ShortcutScript(
            "params", "Params", true,
            listOf(ScriptTrigger(listOf(24), TriggerPressType.LONG_PRESS, doublePressWindowMs = 450L, longPressThresholdMs = 900L)),
            listOf(ScriptAction("change_flashlight_brightness", mapOf("strength" to "3"))),
        )
        repository.upsert(script)

        val reloaded = repository().scripts.value.single()
        assertEquals(mapOf("strength" to "3"), reloaded.actions.single().params)
        assertEquals(450L, reloaded.triggers.single().doublePressWindowMs)
        assertEquals(900L, reloaded.triggers.single().longPressThresholdMs)
    }

    @Test fun aStoreWrittenBeforeConstraintsExistedRoundTripsUnchanged() {
        // Simulates a document persisted before the constraints field was introduced: no
        // "constraints" key on the script at all. Moshi must default to an empty list.
        val preConstraintsJson = """
            {"version":2,"globallyEnabled":true,"scripts":[
              {"id":"legacy","name":"Legacy","enabled":true,
               "triggers":[{"keyCodes":[24],"pressType":"DOUBLE_PRESS"}],
               "actions":[{"type":"toggle_flashlight"}]}
            ]}
        """.trimIndent()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit()
            .putString("shortcut_scripts_v1", preConstraintsJson).commit()

        val script = repository().scripts.value.single()
        assertTrue(script.constraints.isEmpty())
    }

    @Test fun availableConstraintsRoundTripAndUnavailableOnesAreDropped() {
        val repository = repository()
        val script = ShortcutScript(
            "constrained", "Constrained", true,
            listOf(ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)),
            listOf(ScriptAction.ToggleFlashlight),
            listOf(ScriptConstraint("wifi_on"), ScriptConstraint("charging")),
        )
        repository.upsert(script)

        val reloaded = repository().scripts.value.single()
        assertEquals(setOf("wifi_on", "charging"), reloaded.constraints.map { it.type }.toSet())

        // A constraint type that isn't AVAILABLE (or doesn't exist) must never survive a reload,
        // the same way an unrecognized action type is dropped.
        val prefs = context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("shortcut_scripts_v1", "")!!
            .replace("\"charging\"", "\"hinge_open\"")
        prefs.edit().putString("shortcut_scripts_v1", raw).commit()
        val afterUnavailableInjected = repository().scripts.value.single()
        assertEquals(listOf("wifi_on"), afterUnavailableInjected.constraints.map { it.type })
    }

    @Test fun theAutomationFlagRoundTrips() {
        val repository = repository()
        repository.upsert(
            ShortcutScript(
                "automation", "Automation", true, emptyList(), listOf(ScriptAction.ToggleFlashlight),
                constraints = listOf(ScriptConstraint("charging")),
                automation = true,
            ),
        )
        val reloaded = repository().scripts.value.single()
        assertTrue(reloaded.automation)
        assertEquals(listOf(ScriptConstraint("charging")), reloaded.constraints)
    }

    @Test fun v1EventTriggersMigrateToAnAutomationWithTheMatchingConstraint() {
        val json = """
            {"version":2,"globallyEnabled":true,"scripts":[
              {"id":"bt","name":"BT","enabled":true,"triggers":[],"actions":[{"type":"toggle_flashlight"}],
               "eventTriggers":[{"type":"bluetooth_disconnected","params":{"deviceAddress":"AA:BB"}}]},
              {"id":"any","name":"Any","enabled":true,"triggers":[],"actions":[{"type":"toggle_flashlight"}],
               "eventTriggers":[{"type":"bluetooth_connected"}]},
              {"id":"chg","name":"Charger","enabled":true,"triggers":[],"actions":[{"type":"toggle_flashlight"}],
               "eventTriggers":[{"type":"charger_connected"}]}
            ]}
        """.trimIndent()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit()
            .putString("shortcut_scripts_v1", json).commit()

        val byId = repository().scripts.value.associateBy { it.id }
        assertTrue(byId.values.all { it.automation })
        assertEquals(
            listOf(ScriptConstraint("bluetooth_device_disconnected", mapOf("address" to "AA:BB"))),
            byId.getValue("bt").constraints,
        )
        // "Any device" has no constraint equivalent; the script survives flagged but conditionless.
        assertTrue(byId.getValue("any").constraints.isEmpty())
        assertEquals(listOf(ScriptConstraint("charging")), byId.getValue("chg").constraints)
    }

    @Test fun aStoreWrittenBeforeEventTriggersExistedRoundTripsUnchanged() {
        // Simulates a document persisted before the eventTriggers field was introduced: no
        // "eventTriggers" key on the script at all. Moshi must default to an empty list.
        val preEventTriggersJson = """
            {"version":2,"globallyEnabled":true,"scripts":[
              {"id":"legacy","name":"Legacy","enabled":true,
               "triggers":[{"keyCodes":[24],"pressType":"DOUBLE_PRESS"}],
               "actions":[{"type":"toggle_flashlight"}]}
            ]}
        """.trimIndent()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit()
            .putString("shortcut_scripts_v1", preEventTriggersJson).commit()

        val script = repository().scripts.value.single()
        assertFalse(script.automation)
    }

    @Test fun deleteRemovesAScript() {
        val repository = repository()
        repository.upsert(ShortcutScript("one", "One", true, listOf(ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)), listOf(ScriptAction.ToggleFlashlight)))
        repository.delete("one")
        assertTrue(repository.scripts.value.isEmpty())
        assertTrue(repository().scripts.value.isEmpty())
    }

    @Test fun aLegacyCenterAndDistancePinchMigratesToTwoPoints() {
        // Pre-two-point pinch: a center, a span, and an implied horizontal axis. The migrated
        // points must reproduce the exact gesture the old executor performed.
        val legacyJson = """
            {"version":2,"globallyEnabled":true,"scripts":[
              {"id":"pinch","name":"Pinch","enabled":true,
               "triggers":[{"keyCodes":[24],"pressType":"SINGLE_PRESS"}],
               "actions":[{"type":"pinch_screen","params":{"x":"500","y":"1000","distance":"400","pinchType":"in","duration":"250"}}]}
            ]}
        """.trimIndent()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit()
            .putString("shortcut_scripts_v1", legacyJson).commit()

        val action = repository().scripts.value.single().actions.single()
        assertEquals("pinch_screen", action.type)
        assertEquals(
            mapOf("x1" to "300", "y1" to "1000", "x2" to "700", "y2" to "1000", "pinchType" to "in", "duration" to "250"),
            action.params,
        )
    }

    @Test fun anAlreadyTwoPointPinchIsLeftAlone() {
        val json = """
            {"version":2,"globallyEnabled":true,"scripts":[
              {"id":"pinch","name":"Pinch","enabled":true,
               "triggers":[{"keyCodes":[24],"pressType":"SINGLE_PRESS"}],
               "actions":[{"type":"pinch_screen","params":{"x1":"10","y1":"20","x2":"30","y2":"40","pinchType":"out"}}]}
            ]}
        """.trimIndent()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit()
            .putString("shortcut_scripts_v1", json).commit()

        assertEquals(
            mapOf("x1" to "10", "y1" to "20", "x2" to "30", "y2" to "40", "pinchType" to "out"),
            repository().scripts.value.single().actions.single().params,
        )
    }

    @Test fun aSavedInputKeyEventActionBecomesInputKeyCodeWithItsParamsIntact() {
        // input_key_event was a byte-for-byte duplicate of input_key_code in the bridge. Dropping
        // the catalog entry without this alias would make toDomain() discard the whole action.
        val json = """
            {"version":2,"globallyEnabled":true,"scripts":[
              {"id":"keyev","name":"Key","enabled":true,
               "triggers":[{"keyCodes":[24],"pressType":"SINGLE_PRESS"}],
               "actions":[{"type":"input_key_event","params":{"keyCode":"66"}}]}
            ]}
        """.trimIndent()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit()
            .putString("shortcut_scripts_v1", json).commit()

        val action = repository().scripts.value.single().actions.single()
        assertEquals("input_key_code", action.type)
        assertEquals(mapOf("keyCode" to "66"), action.params)
    }

    @Test fun aPinchMissingItsLegacyDistanceIsKeptStaleRatherThanHalfMigrated() {
        val json = """
            {"version":2,"globallyEnabled":true,"scripts":[
              {"id":"pinch","name":"Pinch","enabled":true,
               "triggers":[{"keyCodes":[24],"pressType":"SINGLE_PRESS"}],
               "actions":[{"type":"pinch_screen","params":{"x":"500","y":"1000"}},
                          {"type":"toggle_flashlight"}]}
            ]}
        """.trimIndent()
        context.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE).edit()
            .putString("shortcut_scripts_v1", json).commit()

        // The unmigratable pinch keeps its stale params; it renders as "Tap to configure" in the
        // editor (Task 8) rather than silently vanishing from the user's script.
        val actions = repository().scripts.value.single().actions
        assertEquals(listOf("pinch_screen", "toggle_flashlight"), actions.map { it.type })
        assertEquals(mapOf("x" to "500", "y" to "1000"), actions.first().params)
    }
}
