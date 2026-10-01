package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarConditionTest {

    private fun event(
        begin: Long,
        end: Long,
        title: String = "Standup",
        calendar: String = "Work",
        allDay: Boolean = false,
        canceled: Boolean = false,
    ) = EventInstance(begin, end, title, calendar, allDay, canceled)

    @Test fun activeFromBeginUntilJustBeforeEnd() {
        val events = listOf(event(100, 200))
        assertFalse(isEventActive(events, 99, null, null))
        assertTrue(isEventActive(events, 100, null, null))
        assertTrue(isEventActive(events, 199, null, null))
        assertFalse(isEventActive(events, 200, null, null))
    }

    @Test fun allDayAndCancelledEventsNeverMatch() {
        assertFalse(isEventActive(listOf(event(0, 1_000, allDay = true)), 500, null, null))
        assertFalse(isEventActive(listOf(event(0, 1_000, canceled = true)), 500, null, null))
        assertNull(nextEventBoundary(listOf(event(100, 200, allDay = true)), 0, null, null))
    }

    @Test fun filtersAreCaseInsensitiveContainsAndBlankMeansAny() {
        val events = listOf(event(100, 200, title = "Weekly Standup", calendar = "Team work"))
        assertTrue(isEventActive(events, 150, "WORK", "standup"))
        assertTrue(isEventActive(events, 150, " ", ""))
        assertFalse(isEventActive(events, 150, "Personal", null))
        assertFalse(isEventActive(events, 150, null, "Lunch"))
    }

    /** Documents the known limit: two adjoining meetings are one stretch, so the automation fires once. */
    @Test fun backToBackEventsReadAsOneStretch() {
        val events = listOf(event(100, 200), event(200, 300))
        assertTrue(isEventActive(events, 199, null, null))
        assertTrue(isEventActive(events, 200, null, null))
    }

    @Test fun nextBoundaryIsTheEarliestFutureBeginOrEnd() {
        val events = listOf(event(500, 600), event(100, 300), event(50, 80, title = "Lunch"))
        assertEquals(100L, nextEventBoundary(events, 90, null, "standup"))
        assertEquals(300L, nextEventBoundary(events, 100, null, null))
        assertNull(nextEventBoundary(events, 600, null, null))
    }
}
