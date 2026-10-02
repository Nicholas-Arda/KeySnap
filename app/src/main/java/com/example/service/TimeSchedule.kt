package com.example.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.MainActivity
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Wake-up source for the time conditions: "At a time" and "Repeat every" pulse true for one
 * re-evaluation at their moment; the "Time" window re-evaluates at its start and end.
 */
object TimeSchedule {
    @Volatile private var asOfMs: Long? = null

    @Volatile internal var pulsing: Set<ScriptConstraint> = emptySet()

    /** The clock the time conditions read: now, or the instant an [asOf] block is pinned to. */
    fun now(): LocalDateTime = asOfMs?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        ?: LocalDateTime.now()

    /** Main thread only, like CalendarCondition.asOf. Nests: the outer pin is restored after [block]. */
    internal fun <T> asOf(epochMs: Long, block: () -> T): T {
        val outer = asOfMs
        asOfMs = epochMs
        try {
            return block()
        } finally {
            asOfMs = outer
        }
    }

    fun isPulsing(constraint: ScriptConstraint): Boolean = constraint in pulsing

    /** The alarm's boundary (epoch ms) it was set for. */
    internal const val EXTRA_BOUNDARY = "com.example.service.TIME_BOUNDARY"

    private fun isTime(constraint: ScriptConstraint): Boolean = constraint.type == "time" ||
        constraint.type == ScriptConstraintCatalog.AT_TIME || constraint.type == ScriptConstraintCatalog.INTERVAL

    private fun timeConstraints(script: ShortcutScript): List<ScriptConstraint> = script.constraints.filter(::isTime)

    private fun constraintsOf(scripts: List<ShortcutScript>): List<ScriptConstraint> =
        scripts.filter { it.automation && it.enabled }.flatMap(::timeConstraints)

    /** An automation this schedule wakes, so its "Run exactly on time" option applies. */
    fun hasTimeCondition(automation: Boolean, constraints: List<ScriptConstraint>): Boolean =
        automation && constraints.any(::isTime)

    enum class AlarmKind { ALARM_CLOCK, EXACT, INEXACT }

    /**
     * An alarm clock for a boundary nearer than this would be one every few seconds for a short interval:
     * the status-bar icon flickering and an alarm-clock wake-up per tick. Those keep the plain exact alarm.
     * 30 s rather than 60 s: a 1-minute interval is re-armed just under a minute ahead and must still qualify.
     */
    private val ALARM_CLOCK_MIN_LEAD: Duration = Duration.ofSeconds(30)

    /**
     * The earliest boundary among enabled automations and how to arm it. Pure: the clock and the exact-alarm
     * grant are parameters. An alarm clock only when a script whose own next boundary it is asked for exact
     * timing: ColorOS aligns even exact alarms with its own wake-ups and holds them while the screen is off,
     * but not alarm clocks.
     */
    internal fun plan(scripts: List<ShortcutScript>, now: LocalDateTime, canExact: Boolean): Pair<LocalDateTime, AlarmKind>? {
        val nextByScript = scripts.filter { it.automation && it.enabled }
            .mapNotNull { script -> TimeRules.nextBoundary(timeConstraints(script), now)?.let { script to it } }
        val next = nextByScript.minOfOrNull { it.second } ?: return null
        val kind = when {
            !canExact -> AlarmKind.INEXACT
            nextByScript.any { (script, at) -> at == next && script.exactTiming } &&
                Duration.between(now, next) >= ALARM_CLOCK_MIN_LEAD -> AlarmKind.ALARM_CLOCK
            else -> AlarmKind.EXACT
        }
        return next to kind
    }

    /**
     * Arms one alarm for the earliest boundary among enabled automations, or cancels it when none or
     * while every shortcut is paused, so no alarm-clock icon is left in the status bar.
     */
    fun sync(context: Context, scripts: List<ShortcutScript>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val active = if (ScriptRepository.getInstance(context).globallyEnabled.value) scripts else emptyList()
        // From the wall clock, never the last boundary: a DST gap's phantom ticks are then skipped, not queued.
        val (next, kind) = plan(active, LocalDateTime.now(), CalendarCondition.canScheduleExact(alarms)) ?: run {
            alarms.cancel(alarmIntent(context, 0L))
            return
        }
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // All three replace the one pending alarm: same PendingIntent.
        when (kind) {
            AlarmKind.ALARM_CLOCK -> alarms.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    at,
                    PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                ),
                alarmIntent(context, at),
            )
            AlarmKind.EXACT -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context, at))
            AlarmKind.INEXACT -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context, at))
        }
    }

    /**
     * Seeding (unknown edges only) is pinned just before [boundaryMs], so a window that opens at it is a
     * real false -> true edge in a fresh process. The pulse of [due] and the clean pass after it run at
     * the real time: a late at_time / interval still runs once (late beats lost), a late window-start
     * alarm catches a window still open, and a window that already closed is not run. Main thread only.
     */
    internal fun fire(boundaryMs: Long, due: List<ScriptConstraint>, seed: () -> Unit, reevaluate: () -> Boolean): Boolean {
        asOf(boundaryMs - 1) { seed() }
        pulsing = due.toSet()
        val pulsed = try {
            reevaluate()
        } finally {
            pulsing = emptySet()
        }
        return reevaluate() or pulsed
    }

    /**
     * The alarm, in a process it may have just started; null (the exact-alarm grant) only re-arms.
     * True when an automation fired.
     */
    internal fun onWakeUp(context: Context, boundaryMs: Long?): Boolean {
        HardwareKeyTriggerCoordinator.ensureInitialized(context)
        val scripts = ScriptRepository.getInstance(context).scripts.value
        val fired = boundaryMs != null && fire(
            boundaryMs,
            TimeRules.dueBetween(
                constraintsOf(scripts),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(boundaryMs), ZoneId.systemDefault()),
                now(),
            ),
            seed = HardwareKeyTriggerCoordinator::seedUnknownAutomations,
            reevaluate = HardwareKeyTriggerCoordinator::reevaluateAutomationsFired,
        )
        sync(context, scripts)
        return fired
    }

    /** One alarm slot; the extra only rides along, so cancelling with any boundary cancels it. */
    private fun alarmIntent(context: Context, boundaryMs: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimeAlarmReceiver::class.java).putExtra(EXTRA_BOUNDARY, boundaryMs),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Rings at the next time-condition boundary: fires what is due, then arms the next one. Also
 * receives the exact-alarm grant (Android 12+), which re-arms the pending inexact alarm as an exact one.
 */
class TimeAlarmReceiver : BroadcastReceiver() {
    // Unexported, and every intent only re-evaluates and re-arms; there is no action to tell apart.
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        // The alarm can start this process cold; keep it protected while a fired shortcut runs.
        val pending: PendingResult? = goAsync()
        val boundary = intent.getLongExtra(TimeSchedule.EXTRA_BOUNDARY, 0L).takeIf { it > 0 }
        var fired = false
        try {
            fired = TimeSchedule.onWakeUp(context.applicationContext, boundary)
        } finally {
            if (fired) {
                // ponytail: a fixed budget, as for a widget tap; a long chain keeps running unprotected after it.
                Handler(Looper.getMainLooper()).postDelayed({ pending?.finish() }, BROADCAST_BUDGET_MS)
            } else {
                pending?.finish()
            }
        }
    }

    private companion object {
        const val BROADCAST_BUDGET_MS = 9_000L
    }
}
