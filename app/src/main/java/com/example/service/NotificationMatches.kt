package com.example.service

import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog

/**
 * All the "Notification received" condition remembers: for each such condition, when a
 * notification last matched it and that notification's system key, so a reply can go back to the
 * chat that triggered the run. Never the notification's content, never on disk.
 */
internal object NotificationMatches {
    /**
     * How long one match keeps its condition true. Short on purpose: the condition stands for an
     * event, and a long window would let an unrelated condition turning true later ride on an old
     * match. Bursts inside it collapse into one run.
     */
    const val WINDOW_MS = 10_000L

    private data class Match(val atMs: Long, val key: String)

    private val lastMatch = mutableMapOf<ScriptConstraint, Match>()

    /**
     * [conditions] is every notification condition in every script. Content is read only when one
     * of them names [packageName], so other apps' notifications are never looked at. True when at
     * least one condition matched. [key] is the notification's StatusBarNotification key.
     */
    @Synchronized
    fun record(
        conditions: List<ScriptConstraint>,
        packageName: String,
        key: String,
        nowMs: Long,
        readContent: () -> NotificationContent,
    ): Boolean {
        lastMatch.keys.retainAll(conditions.toSet())
        val forApp = conditions.filter { it.params["packageName"]?.trim() == packageName }
        if (forApp.isEmpty()) return false
        val content = readContent()
        val matched = forApp.filter { notificationMatches(it.params, content) }
        matched.forEach { lastMatch[it] = Match(nowMs, key) }
        return matched.isNotEmpty()
    }

    @Synchronized
    fun isRecent(condition: ScriptConstraint, nowMs: Long): Boolean = recent(condition, nowMs) != null

    /** The newest notification that matched one of [constraints] inside the window, or null. */
    @Synchronized
    fun triggeringKey(constraints: List<ScriptConstraint>, nowMs: Long): String? =
        constraints.filter { it.type == ScriptConstraintCatalog.NOTIFICATION_RECEIVED }
            .mapNotNull { recent(it, nowMs) }
            .maxByOrNull { it.atMs }
            ?.key

    private fun recent(condition: ScriptConstraint, nowMs: Long): Match? =
        lastMatch[condition]?.takeIf { nowMs - it.atMs in 0 until WINDOW_MS }

    @Synchronized
    fun clearForTest() = lastMatch.clear()
}

/** What a notification condition reads: its [titles] (title, conversation title) and its message [text]. */
internal data class NotificationContent(val titles: List<String>, val text: String)

/**
 * A condition's "contact" must name the chat exactly, as a reply's chat does ([isChatNamed]); its
 * "contains" is a case-insensitive substring of the message text only. Blank means any; both set,
 * both must match.
 */
internal fun notificationMatches(params: Map<String, String>, content: NotificationContent): Boolean {
    val contact = params["contact"]
    val needle = params["contains"]
    return (contact.isNullOrBlank() || isChatNamed(content.titles, contact)) &&
        (needle.isNullOrBlank() || content.text.contains(needle.trim(), ignoreCase = true))
}

/** Trimmed and ignoring case, but whole: "Ali" never matches "Alice". */
internal fun isChatNamed(titles: List<String>, chat: String): Boolean =
    titles.any { it.trim().equals(chat.trim(), ignoreCase = true) }

/**
 * One active notification that has a free-text Reply button: its system [key] and its [titles]
 * (title and conversation title). Generic so the choice is testable without Android.
 */
internal data class ReplyCandidate<T>(val key: String, val titles: List<String>, val postTime: Long, val action: T)

/**
 * A named [chat] is the newest candidate with a title equal to it, trimmed and ignoring case:
 * "Ali" never answers "Alice". A blank one is only the notification that started this run,
 * [triggerKey]; with none, there is no target, never "some other chat".
 */
internal fun <T> pickReplyTarget(candidates: List<ReplyCandidate<T>>, chat: String?, triggerKey: String?): ReplyCandidate<T>? {
    if (chat.isNullOrBlank()) return triggerKey?.let { key -> candidates.firstOrNull { it.key == key } }
    return candidates
        .filter { isChatNamed(it.titles, chat) }
        .maxByOrNull { it.postTime }
}
