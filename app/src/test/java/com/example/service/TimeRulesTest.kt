package com.example.service

import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeRulesTest {
    private fun at(time: String, days: String = "") = ScriptConstraint(ScriptConstraintCatalog.AT_TIME, mapOf("time" to time, "days" to days))
    private fun every(n: String, unit: String) = ScriptConstraint(ScriptConstraintCatalog.INTERVAL, mapOf("every" to n, "unit" to unit))
    private fun window(start: String, end: String) = ScriptConstraint("time", mapOf("start" to start, "end" to end))
    private fun t(s: String) = LocalDateTime.parse(s)

    @Test fun atTimeLaterToday() = assertEquals(t("2026-10-01T07:00"), TimeRules.nextDue(at("07:00"), t("2026-10-01T06:59:59")))

    @Test fun atTimeExactlyNowIsTomorrow() = assertEquals(t("2026-10-02T07:00"), TimeRules.nextDue(at("07:00"), t("2026-10-01T07:00")))

    @Test fun atTimeSkipsUnselectedDays() =
        // 2026-10-02 is a Friday; next Monday is 2026-10-05.
        assertEquals(t("2026-10-05T07:00"), TimeRules.nextDue(at("07:00", "mon,tue"), t("2026-10-02T08:00")))

    @Test fun atTimeLastMinuteOfDay() = assertEquals(t("2026-10-01T23:59"), TimeRules.nextDue(at("23:59"), t("2026-10-01T23:58:30")))

    @Test fun atTimeMalformedIsNull() = assertNull(TimeRules.nextDue(at("7am"), t("2026-10-01T06:00")))

    @Test fun intervalAlignsToTheClock() = assertEquals(t("2026-10-01T10:15"), TimeRules.nextDue(every("15", "m"), t("2026-10-01T10:07:12")))

    @Test fun intervalOnTheBoundaryMovesToTheNext() = assertEquals(t("2026-10-01T10:30"), TimeRules.nextDue(every("15", "m"), t("2026-10-01T10:15")))

    @Test fun intervalSeconds() = assertEquals(t("2026-10-01T10:00:10"), TimeRules.nextDue(every("5", "s"), t("2026-10-01T10:00:07")))

    @Test fun intervalNonDividingPeriodRestartsAtMidnight() =
        assertEquals(t("2026-10-02T00:00"), TimeRules.nextDue(every("5", "h"), t("2026-10-01T21:00")))

    @Test fun dueAtMidnightIncludesANonDividingInterval() {
        val fiveHours = every("5", "h")
        assertEquals(listOf(fiveHours), TimeRules.dueBetween(listOf(fiveHours), t("2026-10-02T00:00"), t("2026-10-02T00:00")))
    }

    @Test fun intervalClampsIntoUnitRange() {
        assertEquals(5L, TimeRules.intervalSeconds("1", "s"))
        assertEquals(59L * 60, TimeRules.intervalSeconds("120", "m"))
        assertEquals(24L * 3600, TimeRules.intervalSeconds("99", "h"))
        assertNull(TimeRules.intervalSeconds("x", "m"))
        assertNull(TimeRules.intervalSeconds("10", "d"))
    }

    @Test fun windowNextBoundaryIsStartOrEnd() {
        assertEquals(t("2026-10-01T22:00"), TimeRules.nextDue(window("22:00", "07:00"), t("2026-10-01T12:00")))
        assertEquals(t("2026-10-02T07:00"), TimeRules.nextDue(window("22:00", "07:00"), t("2026-10-01T23:00")))
    }

    @Test fun nextBoundaryIsTheEarliest() =
        assertEquals(t("2026-10-01T10:15"), TimeRules.nextBoundary(listOf(at("11:00"), every("15", "m")), t("2026-10-01T10:01")))

    @Test fun dueOnTimeListsOnlyPulsesDueThen() {
        val a = at("10:15")
        val b = every("15", "m")
        val c = every("20", "m")
        val w = window("10:15", "11:00")
        assertEquals(listOf(a, b), TimeRules.dueBetween(listOf(a, b, c, w), t("2026-10-01T10:15"), t("2026-10-01T10:15")))
    }

    @Test fun dueLateAlsoListsPulsesItSleptPast() {
        val minute = every("1", "m")
        val seven = at("07:00")
        val later = at("07:10")
        assertEquals(listOf(minute, seven), TimeRules.dueBetween(listOf(minute, seven, later), t("2026-10-01T06:59"), t("2026-10-01T07:05")))
    }
}
