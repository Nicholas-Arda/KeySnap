package com.example.service

import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutScript
import com.example.service.TimeSchedule.AlarmKind
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which boundary the one time alarm is armed for, and whether as an alarm clock, an exact or an inexact alarm. */
class TimeSchedulePlanTest {
    private val now = LocalDateTime.parse("2026-10-01T10:00")

    private fun at(time: String) = ScriptConstraint(ScriptConstraintCatalog.AT_TIME, mapOf("time" to time, "days" to ""))
    private fun every(n: String, unit: String) = ScriptConstraint(ScriptConstraintCatalog.INTERVAL, mapOf("every" to n, "unit" to unit))
    private fun t(s: String) = LocalDateTime.parse(s)

    private fun automation(id: String, constraint: ScriptConstraint, exact: Boolean = true, enabled: Boolean = true) = ShortcutScript(
        id, id, enabled, emptyList(), listOf(ScriptAction.ToggleFlashlight), listOf(constraint), automation = true, exactTiming = exact,
    )

    private fun plan(vararg scripts: ShortcutScript, canExact: Boolean = true) = TimeSchedule.plan(scripts.toList(), now, canExact)

    @Test fun exactTimingUsesAnAlarmClock() =
        assertEquals(t("2026-10-01T10:02") to AlarmKind.ALARM_CLOCK, plan(automation("a", at("10:02"))))

    @Test fun optionOffKeepsTheExactAlarm() =
        assertEquals(t("2026-10-01T10:02") to AlarmKind.EXACT, plan(automation("a", at("10:02"), exact = false)))

    @Test fun withoutTheExactGrantItStaysInexact() =
        assertEquals(t("2026-10-01T10:02") to AlarmKind.INEXACT, plan(automation("a", at("10:02")), canExact = false))

    @Test fun aBoundaryUnderThirtySecondsAwayIsAPlainExactAlarm() {
        val near = TimeSchedule.plan(listOf(automation("a", at("10:01"))), t("2026-10-01T10:00:31"), canExact = true)
        assertEquals(AlarmKind.EXACT, near?.second)
        assertEquals(AlarmKind.ALARM_CLOCK, plan(automation("a", at("10:01")))?.second)
    }

    @Test fun aOneMinuteIntervalReArmedJustAfterFiringStillRingsAnAlarmClock() {
        val reArmed = TimeSchedule.plan(listOf(automation("a", every("1", "m"))), t("2026-10-01T10:00:01"), canExact = true)
        assertEquals(t("2026-10-01T10:01") to AlarmKind.ALARM_CLOCK, reArmed)
    }

    @Test fun aSecondsIntervalNeverRingsAnAlarmClock() =
        assertEquals(t("2026-10-01T10:00:05") to AlarmKind.EXACT, plan(automation("a", every("5", "s"))))

    @Test fun onlyTheScriptsOwningTheEarliestBoundaryDecide() {
        // The option-on script is due later, so the 10:05 boundary belongs to the option-off one alone.
        assertEquals(
            t("2026-10-01T10:05") to AlarmKind.EXACT,
            plan(automation("off", at("10:05"), exact = false), automation("on", at("11:00"))),
        )
        // Sharing the boundary, one option-on script is enough.
        assertEquals(
            t("2026-10-01T10:05") to AlarmKind.ALARM_CLOCK,
            plan(automation("off", at("10:05"), exact = false), automation("on", every("5", "m"))),
        )
    }

    @Test fun pausedAndKeyScriptsArmNothing() {
        val keyScript = automation("key", at("10:02")).copy(automation = false)
        assertNull(plan(automation("paused", at("10:02"), enabled = false), keyScript))
    }
}
