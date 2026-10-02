package com.example.service

import com.example.data.ConstraintEditorForms
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog.AT_TIME
import com.example.data.ScriptConstraintCatalog.INTERVAL
import java.time.Duration
import java.time.LocalDateTime

/** When the time conditions are due. Pure: the clock is a parameter. Used by [TimeSchedule]. */
internal object TimeRules {
    private const val WINDOW = "time"
    private val UNIT_SECONDS = mapOf("s" to 1L, "m" to 60L, "h" to 3600L)

    /** The interval period in seconds, clamped into the unit's range; null when malformed. */
    fun intervalSeconds(every: String?, unit: String?): Long? {
        val range = ConstraintEditorForms.intervalRange(unit) ?: return null
        val factor = UNIT_SECONDS[unit?.trim()] ?: return null
        val n = every?.trim()?.toLongOrNull() ?: return null
        return n.coerceIn(range.first.toLong(), range.last.toLong()) * factor
    }

    /**
     * Strictly after [after]: the due instant for `at_time` / `interval`, the next start or end for a `time`
     * window; null for other types or malformed params.
     */
    fun nextDue(constraint: ScriptConstraint, after: LocalDateTime): LocalDateTime? {
        val p = constraint.params
        return when (constraint.type) {
            AT_TIME -> {
                val time = parseHhMm(p["time"]) ?: return null
                val days = p["days"].orEmpty().takeIf { it.isNotBlank() }?.let(ConstraintEditorForms::parseDays)
                (0L..7L).asSequence()
                    .map { after.toLocalDate().plusDays(it).atTime(time) }
                    .firstOrNull { it.isAfter(after) && (days == null || it.dayOfWeek in days) }
            }
            INTERVAL -> {
                val period = intervalSeconds(p["every"], p["unit"]) ?: return null
                val midnight = after.toLocalDate().atStartOfDay()
                val elapsed = Duration.between(midnight, after).seconds
                // ponytail: counted from midnight, so a period that doesn't divide a day restarts there; store an anchor if users ask.
                val next = midnight.plusSeconds((elapsed / period + 1) * period)
                val nextMidnight = midnight.plusDays(1)
                if (next.isAfter(nextMidnight)) nextMidnight else next
            }
            WINDOW -> listOfNotNull(parseHhMm(p["start"]), parseHhMm(p["end"]))
                .takeIf { it.size == 2 }
                ?.flatMap { listOf(after.toLocalDate().atTime(it), after.toLocalDate().plusDays(1).atTime(it)) }
                ?.filter { it.isAfter(after) }
                ?.minOrNull()
            else -> null
        }
    }

    /** The earliest [nextDue] across [constraints]. */
    fun nextBoundary(constraints: List<ScriptConstraint>, after: LocalDateTime): LocalDateTime? =
        constraints.mapNotNull { nextDue(it, after) }.minOrNull()

    /**
     * The `at_time` / `interval` constraints whose first due instant from [boundary] on is not after [now],
     * each once: on time that is exactly those due at [boundary]; late, also those it slept past.
     */
    fun dueBetween(constraints: List<ScriptConstraint>, boundary: LocalDateTime, now: LocalDateTime): List<ScriptConstraint> =
        constraints.filter {
            (it.type == AT_TIME || it.type == INTERVAL) && nextDue(it, boundary.minusNanos(1))?.isAfter(now) == false
        }
}
