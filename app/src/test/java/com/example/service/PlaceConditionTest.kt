package com.example.service

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.BridgeConfig
import com.example.data.ConstraintEditorForms
import com.example.data.KeyMappingConfig
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.service.bridge.BridgePayload
import com.example.data.Place
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlaceConditionTest {

    @Test fun onlyAWholeInRangePlaceParses() {
        assertEquals(Place(41.0151, 28.9795, 300), Place.parse(mapOf("lat" to "41.0151", "lng" to "28.9795", "radius" to "300")))
        assertNull(Place.parse(mapOf("lat" to "41", "lng" to "28")))
        assertNull(Place.parse(mapOf("lat" to "91", "lng" to "28", "radius" to "300")))
        assertEquals(100, Place.parse(mapOf("lat" to "41", "lng" to "28", "radius" to "100"))?.radius) // Fast mode's floor
        assertNull(Place.parse(mapOf("lat" to "41", "lng" to "28", "radius" to "99")))
        assertNull(Place.parse(mapOf("lat" to "41", "lng" to "28", "radius" to "5000")))
    }

    /** Inside needs a fix in the circle and no vaguer than it; outside needs the exit margin cleared. */
    @Test fun aVagueOrEdgeFixKeepsTheOldSide() {
        assertEquals(true, PlaceCondition.side(distance = 100f, accuracy = 50f, radius = 300))
        assertEquals(true, PlaceCondition.side(distance = 290f, accuracy = 20f, radius = 300)) // just inside counts
        assertEquals(false, PlaceCondition.side(distance = 500f, accuracy = 50f, radius = 300))
        assertNull(PlaceCondition.side(distance = 340f, accuracy = 10f, radius = 300)) // in the 75 m exit band
        assertNull(PlaceCondition.side(distance = 100f, accuracy = 2000f, radius = 300)) // a cell estimate is not an arrival
        assertEquals(false, PlaceCondition.side(distance = 5000f, accuracy = 2000f, radius = 300)) // but far enough is out
    }

    @Test fun theExitMarginIsAtLeast50m() {
        assertEquals(50f, PlaceCondition.exitMargin(100))
        assertEquals(500f, PlaceCondition.exitMargin(2000))
    }

    @Test fun fastModeSlowsDownFarFromEveryCircle() {
        assertEquals(30_000L, PlaceCondition.fastIntervalMs(0f))
        assertEquals(30_000L, PlaceCondition.fastIntervalMs(900f))
        assertEquals(120_000L, PlaceCondition.fastIntervalMs(3_000f))
        assertEquals(300_000L, PlaceCondition.fastIntervalMs(20_000f))
    }

    /** Under 150 m only Fast mode can tell the sides apart. */
    @Test fun aSmallCircleNeedsFastMode() {
        val small = mapOf("lat" to "41", "lng" to "28", "radius" to "100")
        assertFalse(ConstraintEditorForms.isComplete(ScriptConstraintCatalog.AT_PLACE, small))
        assertTrue(ConstraintEditorForms.isComplete(ScriptConstraintCatalog.AT_PLACE, small + ("mode" to Place.MODE_FAST)))
    }

    @Test fun theMapAnswerIsTakenOnlyWhenAPickIsOpenAndInRange() {
        val answer = Uri.parse("keysnap://place?lat=41.01510&lng=28.97950&r=300")
        PlacePick.result.value = null
        assertTrue(PlacePick.deliver(answer)) // ours, but nobody asked: ignored
        assertNull(PlacePick.result.value)
        assertFalse(PlacePick.deliver(Uri.parse("https://ardasenn.com/keysnap/place/")))

        PlacePick.open(ApplicationProvider.getApplicationContext(), null)
        assertTrue(PlacePick.deliver(Uri.parse("keysnap://place?lat=41.01510&lng=28.97950&r=50"))) // under 100 m: refused
        assertNull(PlacePick.result.value)
        assertTrue(PlacePick.deliver(answer))
        assertEquals(mapOf("lat" to "41.01510", "lng" to "28.97950", "radius" to "300"), PlacePick.result.value)
        assertTrue(PlacePick.deliver(answer)) // a second answer to the same pick is ignored
        PlacePick.result.value = null
    }

    /** Where someone lives must not travel with a shared shortcut; the name and the checking mode may. */
    @Test fun sharingDropsTheCoordinates() {
        val params = mapOf("lat" to "41.01510", "lng" to "28.97950", "radius" to "300", "name" to "Home",
            Place.NOTICE_TITLE to "At 12 Elm St", Place.NOTICE_TEXT to "hidden")
        assertEquals(mapOf("name" to "Home"), ShortcutShare.portableConstraintParams(ScriptConstraintCatalog.AT_PLACE, params))
        assertEquals(setOf("name", "mode", "negate"), ShortcutShare.constraintParamKeys(ScriptConstraintCatalog.AT_PLACE))
    }

    /** No fix is no evidence: never a commit, never Android's word. */
    @Test fun anAlertCommitsOnlyWhatAFixDecided() {
        val d = PlaceCondition::decide
        assertEquals(PlaceCondition.AlertDecision(PlaceCondition.Step.COMMIT, rearm = false), d(true, true, 0))
        // Android said "entered", the fix says outside: Android's side is now wrong, re-arm it.
        assertEquals(PlaceCondition.AlertDecision(PlaceCondition.Step.COMMIT, rearm = true), d(true, false, 0))
        assertEquals(PlaceCondition.AlertDecision(PlaceCondition.Step.RECHECK, rearm = false), d(true, null, 0))
        assertEquals(PlaceCondition.AlertDecision(PlaceCondition.Step.RECHECK, rearm = false), d(true, null, PlaceCondition.MAX_RECHECKS - 1))
        assertEquals(PlaceCondition.AlertDecision(PlaceCondition.Step.GIVE_UP, rearm = true), d(true, null, PlaceCondition.MAX_RECHECKS))
        // KeySnap's own check after re-adding an alert has no Android word to correct.
        assertEquals(PlaceCondition.AlertDecision(PlaceCondition.Step.GIVE_UP, rearm = false), d(null, null, PlaceCondition.MAX_RECHECKS))
        assertEquals(PlaceCondition.AlertDecision(PlaceCondition.Step.COMMIT, rearm = false), d(null, false, 1))
    }

    /**
     * Fast mode: a vague fix between two agreeing ones neither confirms nor cancels the change. A
     * side kept through a pause may be out of date, so the first change after resuming is silent.
     */
    @Test fun aVagueFixWaitsAndResumingIsNeverACrossing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        val repo = ScriptRepository.getInstance(context)
        repo.scripts.value.forEach { repo.delete(it.id) }
        val params = mapOf("lat" to "41.01510", "lng" to "28.97950", "radius" to "300", "mode" to Place.MODE_FAST)
        val key = Place.parse(params)!!.key
        val prefs = context.getSharedPreferences("place_condition", Context.MODE_PRIVATE)
        prefs.edit().putString(key, "out").commit()
        repo.upsert(
            ShortcutScript("qa-place", "qa-place", triggers = emptyList(), actions = listOf(ScriptAction("toggle_flashlight")),
                constraints = listOf(ScriptConstraint(ScriptConstraintCatalog.AT_PLACE, params)), automation = true, vibrateOnTrigger = false),
        )
        idle()
        fun fix(lat: Double, accuracy: Float) = Location("gps").apply {
            latitude = lat
            longitude = 28.97950
            this.accuracy = accuracy
        }
        val inside = fix(41.01510, 10f)
        val vague = fix(41.01510, 5_000f)
        val far = fix(41.10000, 10f)
        val count = { HardwareKeyTriggerCoordinator.triggerCount.value }
        try {
            val before = count()
            listOf(inside, vague, inside).forEach { PlaceCondition.onFix(context, it) }
            idle()
            assertEquals("in", prefs.getString(key, null))
            assertEquals(before + 1, count())
            listOf(far, far).forEach { PlaceCondition.onFix(context, it) }
            idle()
            assertEquals("out", prefs.getString(key, null))

            repo.setGloballyEnabled(false)
            idle()
            repo.setGloballyEnabled(true)
            idle()
            listOf(inside, inside).forEach { PlaceCondition.onFix(context, it) }
            idle()
            assertEquals("in", prefs.getString(key, null))
            assertEquals("the first side after resuming is not an arrival", before + 1, count())

            listOf(far, far, inside, inside).forEach { PlaceCondition.onFix(context, it) }
            idle()
            assertEquals("the next real arrival runs again", before + 2, count())
        } finally {
            repo.setGloballyEnabled(true)
            repo.delete("qa-place")
            idle()
            prefs.edit().clear().commit()
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The bridge config is readable by the shell: a place goes in as its side alone. */
    @Test fun theBridgeConfigCarriesTheSideNeverThePlace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val place = mapOf("lat" to "41.01510", "lng" to "28.97950", "radius" to "300", "name" to "Home", "mode" to Place.MODE_FAST,
            Place.NOTICE_TITLE to "Near home", "negate" to "true")
        context.getSharedPreferences("place_condition", Context.MODE_PRIVATE).edit().putString("41.01510,28.97950,300", "in").commit()
        val script = ShortcutScript("k", "k", triggers = listOf(ScriptTrigger(listOf(25), TriggerPressType.SINGLE_PRESS)),
            actions = listOf(ScriptAction("toggle_flashlight")), constraints = listOf(ScriptConstraint(ScriptConstraintCatalog.AT_PLACE, place)))
        val payload = BridgePayload(context)
        assertTrue(payload.writeConfig(listOf(script), true, KeyMappingConfig()))
        val text = payload.configFile()!!.readText()
        listOf("41.0151", "28.9795", "Home", "Near home").forEach { assertFalse(it, text.contains(it)) }
        val params = BridgeConfig.read(payload.configFile()).scripts.single().constraints.single().params
        assertEquals(mapOf("negate" to "true", BridgeConfig.PARAM_INSIDE to "true"), params)
    }
}
