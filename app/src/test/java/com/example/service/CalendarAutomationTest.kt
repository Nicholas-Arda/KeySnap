package com.example.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The calendar alarm can start the process cold, where the coordinator knows no edge yet and a
 * first sighting only records. The alarm carries its boundary so the event that started at it is
 * still a false -> true edge, without firing twice for a start another broadcast already reported.
 */
@RunWith(RobolectricTestRunner::class)
class CalendarAutomationTest {
    private lateinit var context: Context
    private val ids = mutableListOf<String>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Robolectric.setupContentProvider(FakeCalendar::class.java, CalendarContract.AUTHORITY)
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
    }

    @After fun tearDown() {
        FakeCalendar.events = emptyList()
        ids.forEach { ScriptRepository.getInstance(context).delete(it) }
    }

    @Test fun theAlarmInAFreshProcessFiresForTheEventThatStartedAtItsBoundary() {
        val start = System.currentTimeMillis() - 1_000
        FakeCalendar.events = listOf(start to start + HOUR)
        // Not idled: the scripts collector has not seeded it, as in a fresh process where onReceive
        // runs before anything the coordinator's initialize queued.
        add("qa-cal-cold")
        val before = count()
        CalendarCondition.onWakeUp(context, boundary = start)
        idle()
        assertEquals(before + 1, count())
    }

    /** The manifest receiver reads the boundary extra and runs the wake-up (goAsync included). */
    @Test fun theAlarmReceiverRunsTheWakeUp() {
        add("qa-cal-receiver")
        idle()
        val start = System.currentTimeMillis() - 1_000
        FakeCalendar.events = listOf(start to start + HOUR)
        val before = count()
        context.sendBroadcast(Intent(context, CalendarAlarmReceiver::class.java).putExtra(CalendarCondition.EXTRA_BOUNDARY, start))
        idle()
        assertEquals(before + 1, count())
    }

    /** Pins why the boundary rides along: without it, a fresh process only records the running event. */
    @Test fun withoutTheBoundaryAFreshProcessOnlyRecords() {
        val start = System.currentTimeMillis() - 1_000
        FakeCalendar.events = listOf(start to start + HOUR)
        add("qa-cal-cold-unseeded")
        val before = count()
        CalendarCondition.onWakeUp(context, boundary = null)
        idle()
        assertEquals(before, count())
    }

    /** A late alarm after a screen-on broadcast already reported the start must not fire it again. */
    @Test fun aStartAlreadyReportedIsNotFiredAgainByTheLateAlarm() {
        add("qa-cal-warm")
        idle() // the collector seeds it: no event yet
        val start = System.currentTimeMillis() - 1_000
        FakeCalendar.events = listOf(start to start + HOUR)
        val before = count()
        HardwareKeyTriggerCoordinator.reevaluateAutomations()
        CalendarCondition.onWakeUp(context, boundary = start)
        idle()
        assertEquals(before + 1, count())
    }

    private fun add(id: String) {
        ids += id
        ScriptRepository.getInstance(context).upsert(
            ShortcutScript(
                id = id,
                name = id,
                triggers = emptyList(),
                actions = listOf(ScriptAction("toggle_flashlight")),
                constraints = listOf(ScriptConstraint("calendar_event_active")),
                automation = true,
                vibrateOnTrigger = false,
            ),
        )
    }

    private fun count() = HardwareKeyTriggerCoordinator.triggerCount.value

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Answers `instances/when/{from}/{to}` with the events overlapping that range. */
    class FakeCalendar : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            val columns = projection ?: arrayOf(Instances.BEGIN, Instances.END)
            val cursor = MatrixCursor(columns)
            val (from, to) = uri.pathSegments.takeLast(2).map { it.toLong() }
            events.filter { (begin, end) -> begin <= to && end > from }.forEach { (begin, end) ->
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            Instances.BEGIN -> begin
                            Instances.END -> end
                            Instances.TITLE -> "Standup"
                            Instances.CALENDAR_DISPLAY_NAME -> "Work"
                            Instances.ALL_DAY -> 0
                            Instances.STATUS -> CalendarContract.Events.STATUS_CONFIRMED
                            else -> null
                        }
                    }.toTypedArray(),
                )
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

        companion object {
            @Volatile var events: List<Pair<Long, Long>> = emptyList()
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
