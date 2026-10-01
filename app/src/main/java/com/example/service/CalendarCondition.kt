package com.example.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript

/** One CalendarContract.Instances row, reduced to what the calendar condition matches on. */
internal data class EventInstance(
    val begin: Long,
    val end: Long,
    val title: String,
    val calendarName: String,
    val allDay: Boolean,
    val canceled: Boolean,
)

/** Blank filters match anything; both are case-insensitive "contains". All-day and cancelled events never match. */
private fun EventInstance.matches(calendarFilter: String?, titleFilter: String?): Boolean =
    !allDay && !canceled &&
        (calendarFilter.isNullOrBlank() || calendarName.contains(calendarFilter.trim(), ignoreCase = true)) &&
        (titleFilter.isNullOrBlank() || title.contains(titleFilter.trim(), ignoreCase = true))

/** `[begin, end)`. Back-to-back matching events read as one unbroken stretch, so they fire once. */
internal fun isEventActive(events: List<EventInstance>, now: Long, calendarFilter: String?, titleFilter: String?): Boolean =
    events.any { it.matches(calendarFilter, titleFilter) && now >= it.begin && now < it.end }

/** The next instant after [now] at which [isEventActive] can change, or null when none is ahead. */
internal fun nextEventBoundary(events: List<EventInstance>, now: Long, calendarFilter: String?, titleFilter: String?): Long? =
    events.asSequence()
        .filter { it.matches(calendarFilter, titleFilter) }
        .flatMap { sequenceOf(it.begin, it.end) }
        .filter { it > now }
        .minOrNull()

/**
 * Wake-up source for `calendar_event_active`: one inexact alarm at the earliest start or end among
 * every calendar condition in use, re-armed when it rings and whenever the calendar changes.
 * Events are read on demand and kept nowhere. Nothing is logged but an exception class: titles and
 * calendar names are the user's.
 */
object CalendarCondition {
    private const val TAG = "CalendarCondition"

    // ponytail: a week ahead; every alarm and every calendar change re-arm it, so a quiet week is fine.
    private const val LOOKAHEAD_MS = 7L * 24 * 60 * 60_000

    // Android 12+ stretches shorter windows to 10 minutes anyway; exact alarms would need
    // SCHEDULE_EXACT_ALARM. A matching event shorter than the window can be missed.
    private const val WINDOW_MS = 10 * 60_000L

    private val PROJECTION = arrayOf(
        Instances.BEGIN, Instances.END, Instances.TITLE, Instances.CALENDAR_DISPLAY_NAME, Instances.ALL_DAY, Instances.STATUS,
    )

    /** The alarm's boundary (epoch ms) it was set for. */
    internal const val EXTRA_BOUNDARY = "com.example.service.CALENDAR_BOUNDARY"

    private var observer: ContentObserver? = null

    /** While set, [isActive] reads the calendar as of this instant instead of now. */
    @Volatile private var asOfMs: Long? = null

    /** Runs [block] with the calendar condition read as of [timeMs]. Main thread only. */
    internal fun asOf(timeMs: Long, block: () -> Unit) {
        asOfMs = timeMs
        try {
            block()
        } finally {
            asOfMs = null
        }
    }

    /** False without READ_CALENDAR or a readable provider: this condition only drives an automation edge. */
    fun isActive(context: Context, calendarFilter: String?, titleFilter: String?): Boolean {
        val now = asOfMs ?: System.currentTimeMillis()
        // ponytail: one provider query per evaluation; cache if a large calendar ever shows up in a profile.
        return instances(context, now, now + 1)?.let { isEventActive(it, now, calendarFilter, titleFilter) } ?: false
    }

    /**
     * Arms the alarm for the next start or end that any enabled automation's calendar condition
     * cares about, or for the end of the look-ahead when none is that close, so an event added far
     * ahead is still found once the observer is gone (a new process). Cancelled when no calendar
     * condition is in use or the calendar cannot be read.
     */
    fun sync(context: Context, scripts: List<ShortcutScript>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val conditions = scripts.filter { it.automation && it.enabled }.flatMap { it.constraints }
            .filter { it.type == ScriptConstraintCatalog.CALENDAR_EVENT_ACTIVE }
        val now = System.currentTimeMillis()
        val events = if (conditions.isEmpty()) null else instances(context, now, now + LOOKAHEAD_MS)
        val next = events?.let { list ->
            conditions.mapNotNull { nextEventBoundary(list, now, it.params["calendar"], it.params["title"]) }.minOrNull()
                ?: (now + LOOKAHEAD_MS)
        }
        if (next == null) {
            alarms.cancel(alarmIntent(context, 0L))
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, next, WINDOW_MS, alarmIntent(context, next))
            ensureObserver(context.applicationContext)
        }
    }

    /**
     * The alarm, or the calendar changing, in a process that may have just started: when this
     * wake-up is for a [boundary], automations whose edge is not known yet are first recorded with
     * the calendar as of just before it, so an event that started at it is a false -> true edge.
     */
    internal fun onWakeUp(context: Context, boundary: Long?) {
        HardwareKeyTriggerCoordinator.ensureInitialized(context)
        if (boundary != null) asOf(boundary - 1) { HardwareKeyTriggerCoordinator.seedUnknownAutomations() }
        HardwareKeyTriggerCoordinator.reevaluateAutomations()
        sync(context, ScriptRepository.getInstance(context).scripts.value)
    }

    @SuppressLint("MissingPermission") // a missing READ_CALENDAR surfaces as the SecurityException below
    private fun instances(context: Context, from: Long, to: Long): List<EventInstance>? = try {
        Instances.query(context.contentResolver, PROJECTION, from, to)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        EventInstance(
                            begin = cursor.getLong(0),
                            end = cursor.getLong(1),
                            title = cursor.getString(2).orEmpty(),
                            calendarName = cursor.getString(3).orEmpty(),
                            allDay = cursor.getInt(4) == 1,
                            canceled = cursor.getInt(5) == Events.STATUS_CANCELED,
                        ),
                    )
                }
            }
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        Log.w(TAG, "Calendar read failed: ${e.javaClass.simpleName}")
        null
    }

    /** One alarm slot; the extra only rides along, so cancelling with any [boundary] cancels it. */
    private fun alarmIntent(context: Context, boundary: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CalendarAlarmReceiver::class.java).putExtra(EXTRA_BOUNDARY, boundary),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Process lifetime: an event added or moved re-arms the alarm without waiting for the next one. */
    private fun ensureObserver(context: Context) {
        if (observer != null) return
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onWakeUp(context, boundary = null)
        }
        runCatching { context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, watcher) }
            .onSuccess { observer = watcher }
    }
}

/** Rings at a calendar condition's next start or end: re-evaluates automations, then arms the next one. */
class CalendarAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The alarm can start this process cold; keep it protected while a fired shortcut runs.
        val pending: PendingResult? = goAsync()
        val boundary = intent.getLongExtra(CalendarCondition.EXTRA_BOUNDARY, 0L).takeIf { it > 0 }
        try {
            CalendarCondition.onWakeUp(context.applicationContext, boundary)
        } finally {
            // ponytail: a fixed budget, as for a widget tap; a long chain keeps running unprotected after it.
            Handler(Looper.getMainLooper()).postDelayed({ pending?.finish() }, BROADCAST_BUDGET_MS)
        }
    }

    private companion object {
        const val BROADCAST_BUDGET_MS = 9_000L
    }
}
