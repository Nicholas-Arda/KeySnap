package com.example.service

import com.example.data.ScriptConstraint

/**
 * All the "Notification received" condition remembers: for each such condition, when a
 * notification last matched it. Never the notification itself, never on disk.
 */
internal object NotificationMatches {
    /**
     * How long one match keeps its condition true. Short on purpose: the condition stands for an
     * event, and a long window would let an unrelated condition turning true later ride on an old
     * match. Bursts inside it collapse into one run.
     */
    const val WINDOW_MS = 10_000L

    private val lastMatch = mutableMapOf<ScriptConstraint, Long>()

    /**
     * [conditions] is every notification condition in every script. Content is read only when one
     * of them names [packageName], so other apps' notifications are never looked at. True when at
     * least one condition matched.
     */
    @Synchronized
    fun record(conditions: List<ScriptConstraint>, packageName: String, nowMs: Long, readContent: () -> String): Boolean {
        lastMatch.keys.retainAll(conditions.toSet())
        val forApp = conditions.filter { it.params["packageName"]?.trim() == packageName }
        if (forApp.isEmpty()) return false
        val content = readContent()
        val matched = forApp.filter { contentMatches(it.params["contains"], content) }
        matched.forEach { lastMatch[it] = nowMs }
        return matched.isNotEmpty()
    }

    @Synchronized
    fun isRecent(condition: ScriptConstraint, nowMs: Long): Boolean =
        lastMatch[condition]?.let { nowMs - it in 0 until WINDOW_MS } ?: false

    @Synchronized
    fun clearForTest() = lastMatch.clear()
}

/** A blank [needle] matches anything; otherwise a case-insensitive substring of title and text. */
internal fun contentMatches(needle: String?, content: String): Boolean =
    needle.isNullOrBlank() || content.contains(needle.trim(), ignoreCase = true)

/** One active notification that has a free-text Reply button. Generic so the choice is testable without Android. */
internal data class ReplyCandidate<T>(val title: String, val postTime: Long, val action: T)

/** The newest candidate whose title contains [chat]; any candidate when [chat] is blank. */
internal fun <T> pickReplyTarget(candidates: List<ReplyCandidate<T>>, chat: String?): ReplyCandidate<T>? =
    candidates
        .filter { chat.isNullOrBlank() || it.title.contains(chat.trim(), ignoreCase = true) }
        .maxByOrNull { it.postTime }
