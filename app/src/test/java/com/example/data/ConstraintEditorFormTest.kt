package com.example.data

import io.github.nicholasarda.keysnap.R
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
        "battery_above", "battery_below", "time", "day_of_week", "cooldown",
        "notification_received", "rss_new_item", "calendar_event_active", "at_time", "interval",
    )

    @Test fun aCalendarConditionNeedsNothingAndSummarisesItsFilters() {
        assertTrue(ConstraintEditorForms.isComplete("calendar_event_active", emptyMap()))
        assertEquals(res.getString(R.string.constraint_summary_any_event), ConstraintEditorForms.summarize(ScriptConstraint("calendar_event_active"), res))
        assertEquals("Work · Standup", ConstraintEditorForms.summarize(ScriptConstraint("calendar_event_active", mapOf("calendar" to "Work", "title" to "Standup")), res))
    }

    @Test fun anRssFeedNeedsAnHttpsAddressAndIsNeverRewritten() {
        assertFalse(ConstraintEditorForms.isComplete("rss_new_item", emptyMap()))
        assertFalse(ConstraintEditorForms.isComplete("rss_new_item", mapOf("url" to "http://example.com/feed")))
        assertFalse(ConstraintEditorForms.isComplete("rss_new_item", mapOf("url" to "example.com/feed")))
        assertTrue(ConstraintEditorForms.isComplete("rss_new_item", mapOf("url" to "https://example.com/feed?token=a")))
        val typed = "HTTPS://Example.com/Feed?x=1"
        assertEquals(typed, ConstraintEditorForms.summarize(ScriptConstraint("rss_new_item", mapOf("url" to typed)), res))
    }

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
                assertFalse("$type uses an action-only field", field is EditorField.KeyCodePick || field is EditorField.Point || field is EditorField.TwoPoints || field is EditorField.ImagePick)
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
        assertFalse(ConstraintEditorForms.isComplete("notification_received", mapOf("contains" to "annem")))
        assertTrue(ConstraintEditorForms.isComplete("notification_received", mapOf("packageName" to "com.whatsapp")))
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
        assertEquals("10", ConstraintEditorForms.defaults(ConstraintEditorForms.form("cooldown")!!)["minutes"])
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
        assertEquals("Example · “annem”", ConstraintEditorForms.summarize(ScriptConstraint("notification_received", mapOf("packageName" to "com.example.app", "contains" to "annem")), res, apps))
        assertEquals("Example", ConstraintEditorForms.summarize(ScriptConstraint("notification_received", mapOf("packageName" to "com.example.app")), res, apps))
        assertEquals(
            "Example · Annem · “acil”",
            ConstraintEditorForms.summarize(ScriptConstraint("notification_received", mapOf("packageName" to "com.example.app", "contact" to "Annem", "contains" to "acil")), res, apps),
        )
        assertEquals("Home", ConstraintEditorForms.summarize(ScriptConstraint("connected_to_wifi_network", mapOf("ssid" to "Home")), res))
        assertNull(ConstraintEditorForms.summarize(ScriptConstraint("flashlight_on"), res))
        // Non-empty is all that is asserted for resource-driven summaries: their wording is translated.
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("connected_to_wifi_network"), res)!!.isNotBlank())
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("battery_below", mapOf("percent" to "15")), res)!!.contains("15"))
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("time", mapOf("start" to "22:00", "end" to "07:00")), res)!!.isNotBlank())
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("day_of_week", mapOf("days" to "mon,tue")), res)!!.contains(", "))
        assertTrue(ConstraintEditorForms.summarize(ScriptConstraint("cooldown", mapOf("minutes" to "15")), res)!!.contains("15"))
    }

    @Test
    fun atTimeDefaultsAndSummary() {
        val form = ConstraintEditorForms.form(ScriptConstraintCatalog.AT_TIME)!!
        assertEquals(mapOf("time" to "07:00"), ConstraintEditorForms.defaults(form))
        assertTrue(ConstraintEditorForms.isComplete(ScriptConstraintCatalog.AT_TIME, mapOf("time" to "07:00", "days" to "")))
        val time = ConstraintEditorForms.formatTime("07:00", res.configuration.locales[0])
        assertEquals("$time · every day", ConstraintEditorForms.summarize(ScriptConstraint("at_time", mapOf("time" to "07:00")), res))
        val days = ConstraintEditorForms.summarize(ScriptConstraint("day_of_week", mapOf("days" to "mon,fri")), res)
        assertEquals("$time · $days", ConstraintEditorForms.summarize(ScriptConstraint("at_time", mapOf("time" to "07:00", "days" to "mon,fri")), res))
    }

    @Test
    fun intervalDefaultsToThirtyMinutes() {
        val form = ConstraintEditorForms.form(ScriptConstraintCatalog.INTERVAL)!!
        assertEquals(mapOf("every" to "30", "unit" to "m"), ConstraintEditorForms.defaults(form))
        assertTrue(ConstraintEditorForms.isComplete(ScriptConstraintCatalog.INTERVAL, ConstraintEditorForms.defaults(form)))
    }

    @Test
    fun unitChangeClampsValue() {
        assertEquals("24", ConstraintEditorForms.clampInterval("59", "h"))
        assertEquals("5", ConstraintEditorForms.clampInterval("2", "s"))
        assertEquals("30", ConstraintEditorForms.clampInterval("30", "m"))
        assertEquals("1", ConstraintEditorForms.clampInterval("", "m"))
    }

    @Test
    fun intervalIncompleteWhenMalformed() {
        assertFalse(ConstraintEditorForms.isComplete(ScriptConstraintCatalog.INTERVAL, mapOf("every" to "x", "unit" to "m")))
        assertFalse(ConstraintEditorForms.isComplete(ScriptConstraintCatalog.INTERVAL, mapOf("every" to "30", "unit" to "d")))
        // Out of range is still complete: Save stores the clamped value instead.
        assertTrue(ConstraintEditorForms.isComplete(ScriptConstraintCatalog.INTERVAL, mapOf("every" to "2", "unit" to "s")))
    }

    @Test
    fun saveStoresTheClampedInterval() {
        assertEquals("59", ConstraintEditorForms.clampInterval("99", "m"))
        assertEquals("5", ConstraintEditorForms.clampInterval(" 2 ", "s"))
        assertEquals("1", ConstraintEditorForms.clampInterval("0", "h"))
    }

    @Test
    fun intervalSummaries() {
        fun summary(every: String, unit: String) = ConstraintEditorForms.summarize(ScriptConstraint("interval", mapOf("every" to every, "unit" to unit)), res)
        assertEquals("Every 30 min", summary("30", "m"))
        assertEquals("Every 10 s", summary("10", "s"))
        assertEquals("Every 2 h", summary("2", "h"))
    }

    @Test
    fun clockExampleShowsTheFirstTicks() {
        // Dividing periods: ticks within the minute, hour or day.
        assertEquals(":00, :15, :30, :45", ConstraintEditorForms.clockExample("15", "m"))
        assertEquals(":00, :10, :20, :30, …", ConstraintEditorForms.clockExample("10", "s"))
        assertEquals("00:00, 06:00, 12:00, 18:00", ConstraintEditorForms.clockExample("6", "h"))
        assertEquals("00:00, 12:00", ConstraintEditorForms.clockExample("12", "h"))
        assertTrue(ConstraintEditorForms.intervalDividesClock("15", "m"))
        // Non-dividing periods: clock times counted from midnight.
        assertEquals("00:00, 00:07, 00:14, 00:21, …", ConstraintEditorForms.clockExample("7", "m"))
        assertEquals("00:00, 07:00, 14:00, 21:00", ConstraintEditorForms.clockExample("7", "h"))
        assertEquals("00:00:00, 00:00:07, 00:00:14, 00:00:21, …", ConstraintEditorForms.clockExample("7", "s"))
        assertFalse(ConstraintEditorForms.intervalDividesClock("7", "m"))
        // Malformed.
        assertNull(ConstraintEditorForms.clockExample("x", "m"))
        assertNull(ConstraintEditorForms.clockExample("15", "d"))
    }

    @Test
    fun theTimeWindowExplainsItsAutomationEdge() {
        assertEquals(R.string.helper_time_window_start, ConstraintEditorForms.form("time")!!.helperRes)
    }
}
