package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ConstraintEditorFormTest {

    private val res = RuntimeEnvironment.getApplication().resources

    private val formTypes = listOf(
        "app_in_foreground", "app_not_in_foreground", "screen_orientation",
        "connected_to_wifi_network", "disconnected_from_wifi_network",
        "bluetooth_device_connected", "bluetooth_device_disconnected",
        "battery_above", "battery_below", "time", "day_of_week",
    )

    @Test fun everyFormTypeExistsInTheCatalogAndIsAvailable() {
        formTypes.forEach { type ->
            assertTrue("$type has a form but is not an AVAILABLE catalog entry", ScriptConstraintCatalog.isAvailable(type))
            assertTrue("$type should have a form", ConstraintEditorForms.form(type) != null)
        }
        assertNull(ConstraintEditorForms.form("flashlight_on"))
        assertNull(ConstraintEditorForms.form("not_a_real_constraint"))
    }

    @Test fun constraintFormsUseOnlyFieldsTheConstraintSheetRenders() {
        formTypes.forEach { type ->
            ConstraintEditorForms.form(type)!!.fields.forEach { field ->
                assertFalse("$type uses an action-only field", field is EditorField.KeyCodePick || field is EditorField.Point || field is EditorField.TwoPoints)
            }
        }
    }

    @Test fun isCompleteIsFalseUntilRequiredParamsArePresent() {
        assertFalse(ConstraintEditorForms.isComplete("app_in_foreground", emptyMap()))
        assertFalse(ConstraintEditorForms.isComplete("app_in_foreground", mapOf("packageName" to "  ")))
        assertTrue(ConstraintEditorForms.isComplete("app_in_foreground", mapOf("packageName" to "com.example.app")))
        assertFalse(ConstraintEditorForms.isComplete("bluetooth_device_connected", mapOf("name" to "Buds")))
        assertTrue(ConstraintEditorForms.isComplete("bluetooth_device_connected", mapOf("address" to "AA:BB")))
        assertFalse(ConstraintEditorForms.isComplete("day_of_week", emptyMap()))
        assertTrue(ConstraintEditorForms.isComplete("day_of_week", mapOf("days" to "mon")))
        assertFalse(ConstraintEditorForms.isComplete("time", mapOf("start" to "22:00")))
        assertTrue(ConstraintEditorForms.isComplete("time", ConstraintEditorForms.defaults(ConstraintEditorForms.form("time")!!)))
        // No form means nothing to fill in.
        assertTrue(ConstraintEditorForms.isComplete("flashlight_on", emptyMap()))
    }

    @Test fun aBlankSsidMeansAnyNetworkAndIsComplete() {
        assertTrue(ConstraintEditorForms.isComplete("connected_to_wifi_network", emptyMap()))
        assertTrue(ConstraintEditorForms.isComplete("disconnected_from_wifi_network", mapOf("ssid" to "")))
    }

    @Test fun defaultsSeedPercentOrientationAndTheTimeRange() {
        assertEquals("20", ConstraintEditorForms.defaults(ConstraintEditorForms.form("battery_below")!!)["percent"])
        assertEquals("portrait", ConstraintEditorForms.defaults(ConstraintEditorForms.form("screen_orientation")!!)["orientation"])
        val time = ConstraintEditorForms.defaults(ConstraintEditorForms.form("time")!!)
        assertEquals("22:00", time["start"])
        assertEquals("07:00", time["end"])
        assertTrue(ConstraintEditorForms.defaults(ConstraintEditorForms.form("day_of_week")!!).isEmpty())
    }

    @Test fun daySetRoundTripsInCanonicalOrder() {
        val picked = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        val text = ConstraintEditorForms.formatDays(picked)
        assertEquals("mon,fri,sun", text)
        assertEquals(picked, ConstraintEditorForms.parseDays(text))
        assertEquals(emptySet<DayOfWeek>(), ConstraintEditorForms.parseDays(""))
        assertEquals(setOf(DayOfWeek.TUESDAY), ConstraintEditorForms.parseDays(" TUE , nope"))
    }

    @Test fun timeRangeRoundTrips() {
        assertEquals("07:05", ConstraintEditorForms.formatTime(7, 5))
        assertEquals(22 to 30, ConstraintEditorForms.parseTime("22:30"))
        assertNull(ConstraintEditorForms.parseTime("later"))
        assertEquals("later", ConstraintEditorForms.formatTime("later", Locale.US))
        assertEquals("10:30 PM", ConstraintEditorForms.formatTime("22:30", Locale.US).replace(' ', ' '))
    }

    @Test fun weekOrderFollowsTheLocale() {
        assertEquals(DayOfWeek.MONDAY, ConstraintEditorForms.weekOrder(Locale.GERMANY).first())
        assertEquals(DayOfWeek.SUNDAY, ConstraintEditorForms.weekOrder(Locale.US).first())
        assertEquals(7, ConstraintEditorForms.weekOrder(Locale.US).distinct().size)
    }

    @Test fun summariesReadAsTheRowsSecondLine() {
        val apps = listOf(InstalledApp("com.example.app", "Example"))
        assertEquals("Example", ConstraintEditorForms.summarize(ScriptConstraint("app_in_foreground", mapOf("packageName" to "com.example.app")), res, apps))
        assertEquals("com.other", ConstraintEditorForms.summarize(ScriptConstraint("app_in_foreground", mapOf("packageName" to "com.other")), res, apps))
        assertEquals("Buds", ConstraintEditorForms.summarize(ScriptConstraint("bluetooth_device_connected", mapOf("address" to "AA:BB", "name" to "Buds")), res))
        assertEquals("AA:BB", ConstraintEditorForms.summarize(ScriptConstraint("bluetooth_device_connected", mapOf("address" to "AA:BB")), res))
        assertEquals("Home", ConstraintEditorForms.summarize(ScriptConstraint("connected_to_wifi_network", mapOf("ssid" to "Home")), res))
        assertNull(ConstraintEditorForms.summarize(ScriptConstraint("flashlight_on"), res))
        // Non-empty is all that is asserted for resource-driven summaries: their wording is translated.
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("connected_to_wifi_network"), res)!!.isNotBlank())
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("battery_below", mapOf("percent" to "15")), res)!!.contains("15"))
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("time", mapOf("start" to "22:00", "end" to "07:00")), res)!!.isNotBlank())
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("day_of_week", mapOf("days" to "mon,tue")), res)!!.contains(", "))
    }
}
