package com.example.service

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The time alarm carries its boundary: only the seeding of unknown edges is pinned to just before it;
 * the `at_time` / `interval` constraints due between it and delivery pulse true for one re-evaluation,
 * which (like the clean pass after it) reads the real clock.
 */
@RunWith(RobolectricTestRunner::class)
class TimeAutomationTest {
    private lateinit var context: Context
    private val ids = mutableListOf<String>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RuntimeEnvironment.setQualifiers("+notnight")
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        // Another class's automation would fire on these re-evaluations too: start from no scripts.
        val repo = ScriptRepository.getInstance(context)
        repo.scripts.value.forEach { repo.delete(it.id) }
        idle()
    }

    @After fun tearDown() {
        ids.forEach { ScriptRepository.getInstance(context).delete(it) }
        idle()
    }

    @Test fun fireSeedsBeforeTheBoundaryThenPulsesOnce() {
        val boundary = ms(LocalDateTime.parse("2026-10-01T07:00"))
        val due = listOf(at("07:00"))
        val seen = mutableListOf<String>()
        TimeSchedule.fire(
            boundary, due,
            seed = { seen += "seed@" + TimeSchedule.now().toLocalTime() + " pulsing=" + TimeSchedule.isPulsing(due[0]) },
            reevaluate = {
                seen += "eval pulsing=" + TimeSchedule.isPulsing(due[0])
                false
            },
        )
        assertEquals(listOf("seed@06:59:59.999 pulsing=false", "eval pulsing=true", "eval pulsing=false"), seen)
    }

    /** "Every 15 min" and "At 07:00" both land on 07:00: each runs once, and only at the boundary. */
    @Test fun everyConstraintDueAtTheBoundaryPulses() {
        add("qa-time-at", at("07:00"))
        add("qa-time-every", every("15", "m"))
        idle()
        val boundary = today("07:00")
        val due = TimeRules.dueBetween(listOf(at("07:00"), every("15", "m")), boundary, boundary)
        assertEquals(2, due.size)
        val before = count()
        fireCoordinator(boundary, due)
        assertEquals(before + 2, count())
        HardwareKeyTriggerCoordinator.reevaluateAutomations()
        idle()
        assertEquals(before + 2, count())
    }

    /** Every 15 min + dark mode: runs on a tick while dark mode holds, never on the state change itself. */
    @Test fun intervalWithStateFiresOnlyOnTicks() {
        add("qa-time-every-dark", every("15", "m"), ScriptConstraint("dark_mode_on"))
        idle()
        val tick = every("15", "m")
        val before = count()

        setNight(true)
        assertEquals("state true, no tick", before, count())

        setNight(false)
        fireCoordinator(today("10:15"), listOf(tick))
        assertEquals("tick, state false", before, count())

        setNight(true)
        fireCoordinator(today("10:30"), listOf(tick))
        assertEquals("tick, state true", before + 1, count())
    }

    /** The process was cold when the window opened: the seeded edge still makes its start fire, once. */
    @Test fun windowStartFiresInAFreshProcess() {
        // Not idled: the scripts collector has not seeded it, as in a process the alarm just started.
        add("qa-time-window", ScriptConstraint("time", mapOf("start" to "07:00", "end" to "08:00")))
        val before = count()
        fireCoordinator(today("07:00"), emptyList())
        assertEquals("window start", before + 1, count())
        fireCoordinator(today("08:00"), emptyList())
        assertEquals("window end", before + 1, count())
        fireCoordinator(today("07:00").plusDays(1), emptyList())
        assertEquals("next day's start", before + 2, count())
    }

    /**
     * The 07:00 alarm deferred to 07:40 in a cold process: the seed pinned before 07:00 sees a 07:30–08:00
     * window closed, so the passes at the real time catch the window it slept through.
     */
    @Test fun aLateAlarmCatchesAWindowThatOpenedWhileItSlept() {
        add("qa-time-late-window", ScriptConstraint("time", mapOf("start" to "07:30", "end" to "08:00")))
        val before = count()
        wakeUp(boundary = today("07:00"), deliveredAt = today("07:40"))
        assertEquals(before + 1, count())
    }

    /** Warm: the window already ran at 07:35 via a broadcast; the 07:00 alarm delivered at 07:40 must not rewind and re-run it. */
    @Test fun aLateAlarmDoesNotRerunAWindowThatAlreadyRan() {
        add("qa-time-ran-window", ScriptConstraint("time", mapOf("start" to "07:30", "end" to "08:00")))
        idle()
        TimeSchedule.asOf(ms(today("07:00"))) { HardwareKeyTriggerCoordinator.reevaluateAutomations() }
        val before = count()
        TimeSchedule.asOf(ms(today("07:35"))) {
            HardwareKeyTriggerCoordinator.reevaluateAutomations()
            idle()
        }
        assertEquals("ran at 07:35", before + 1, count())
        wakeUp(boundary = today("07:00"), deliveredAt = today("07:40"))
        assertEquals("not again at 07:40", before + 1, count())
    }

    /** The 06:59 alarm (armed by some "every 1 min") delivered at 07:05: the 07:00 "At a time" it slept past still runs, once. */
    @Test fun aLateAlarmStillRunsAnAtTimeDueBeforeDelivery() {
        add("qa-time-late-at", at("07:00"))
        idle()
        val before = count()
        wakeUp(boundary = today("06:59"), deliveredAt = today("07:05"))
        assertEquals(before + 1, count())
    }

    /** The alarm for [boundary] arriving at [deliveredAt]; the pin also covers the runs it queues. */
    private fun wakeUp(boundary: LocalDateTime, deliveredAt: LocalDateTime) = TimeSchedule.asOf(ms(deliveredAt)) {
        TimeSchedule.onWakeUp(context, ms(boundary))
        idle()
    }

    /**
     * The manifest receiver reads the boundary extra, fires what is due, and re-arms the next boundary.
     * 00:00 today is always past, as a delivered alarm's boundary is; the real clock is not pinned here.
     */
    @Test fun theAlarmReceiverFiresTheDueAutomationAndReArms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        add("qa-time-receiver", at("00:00"))
        idle()
        val before = count()
        context.sendBroadcast(Intent(context, TimeAlarmReceiver::class.java).putExtra(TimeSchedule.EXTRA_BOUNDARY, ms(today("00:00"))))
        idle()
        assertEquals(before + 1, count())
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(ms(TimeRules.nextDue(at("00:00"), LocalDateTime.now())!!), alarms[0].triggerAtMs)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarms[0].windowLengthMs)
        // "Run exactly on time" is on by default and tomorrow's 00:00 is over a minute away.
        assertNotNull(alarms[0].alarmClockInfo)
    }

    /** Pausing everything removes the alarm, and with it the alarm-clock icon; resuming re-arms it. */
    @Test fun aGlobalPauseCancelsTheAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        add("qa-time-paused", at("00:00"))
        idle()
        val manager = shadowOf(context.getSystemService(AlarmManager::class.java))
        val repo = ScriptRepository.getInstance(context)
        assertEquals(1, manager.scheduledAlarms.size)
        try {
            repo.setGloballyEnabled(false)
            idle()
            assertEquals(0, manager.scheduledAlarms.size)
        } finally {
            repo.setGloballyEnabled(true)
            idle()
        }
        assertEquals(1, manager.scheduledAlarms.size)
    }

    /** Delivered on time: the real clock is pinned to [boundary], including the runs the fire queues. */
    private fun fireCoordinator(boundary: LocalDateTime, due: List<ScriptConstraint>) = TimeSchedule.asOf(ms(boundary)) {
        TimeSchedule.fire(
            ms(boundary), due,
            seed = HardwareKeyTriggerCoordinator::seedUnknownAutomations,
            reevaluate = HardwareKeyTriggerCoordinator::reevaluateAutomationsFired,
        )
        idle()
    }

    private fun add(id: String, vararg constraints: ScriptConstraint) {
        ids += id
        ScriptRepository.getInstance(context).upsert(
            ShortcutScript(
                id = id,
                name = id,
                triggers = emptyList(),
                actions = listOf(ScriptAction("toggle_flashlight")),
                constraints = constraints.toList(),
                automation = true,
                vibrateOnTrigger = false,
            ),
        )
    }

    private fun setNight(on: Boolean) {
        RuntimeEnvironment.setQualifiers(if (on) "+night" else "+notnight")
        (context as Application).onConfigurationChanged(context.resources.configuration)
        // What DeviceEventReceiver does on the configuration change.
        HardwareKeyTriggerCoordinator.reevaluateAutomations()
        idle()
    }

    private fun at(time: String) = ScriptConstraint(ScriptConstraintCatalog.AT_TIME, mapOf("time" to time, "days" to ""))

    private fun every(n: String, unit: String) = ScriptConstraint(ScriptConstraintCatalog.INTERVAL, mapOf("every" to n, "unit" to unit))

    private fun today(time: String) = LocalDate.now().atTime(java.time.LocalTime.parse(time))

    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun count() = HardwareKeyTriggerCoordinator.triggerCount.value

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
