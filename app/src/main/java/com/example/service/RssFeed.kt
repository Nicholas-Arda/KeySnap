package com.example.service

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

// ponytail: a cap instead of a byte limit; the parser stops reading here. A feed longer than this
// that appends new items at the end (oldest first) would be missed; raise it if one turns up.
internal const val MAX_FEED_ITEMS = 100

/** JobScheduler runs a periodic job at most once per period, which still allows two runs close together. */
internal const val MIN_POLL_GAP_MS = 15 * 60_000L

/** A real feed is tens of kilobytes; this bounds memory and data use for anything else at the address. */
internal const val MAX_FEED_BYTES = 2L * 1024 * 1024

/** Overall time for one feed, on top of the connection's own per-read timeout. */
internal const val MAX_FEED_FETCH_MS = 30_000L

/**
 * Throws [IOException] once more than [maxBytes] have been read or [deadlineNanos] (`System.nanoTime`)
 * has passed, so a huge or trickling response cannot hold the job. Counts what the caller reads,
 * i.e. after the connection's transparent gzip decoding.
 */
internal class CappedInputStream(
    input: InputStream,
    private val maxBytes: Long = MAX_FEED_BYTES,
    private val deadlineNanos: Long = System.nanoTime() + MAX_FEED_FETCH_MS * 1_000_000,
) : FilterInputStream(input) {
    private var total = 0L

    override fun read(): Int = super.read().also { counted(if (it >= 0) 1 else 0) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { counted(maxOf(it, 0)) }

    private fun counted(n: Int) {
        total += n
        if (total > maxBytes) throw IOException("Feed larger than the cap")
        if (System.nanoTime() - deadlineNanos > 0) throw IOException("Feed took longer than the deadline")
    }
}

/**
 * The identity of each `<item>` (RSS 0.9x, 1.0, 2.0) or `<entry>` (Atom), in feed order: its guid
 * or id, else its link, else its title; the first occurrence of each wins. Namespace processing is
 * off, so `media:title` and friends never match. A document with a DOCTYPE is refused before
 * anything after it is read: the platform parser can still define entities from an internal
 * subset, which is how entity-expansion bombs work, and no real feed needs one (the old RSS 0.91
 * DOCTYPE is the rare exception, and is refused too).
 */
internal fun parseFeedItemIds(input: InputStream, limit: Int = MAX_FEED_ITEMS): List<String> {
    val parser = Xml.newPullParser()
    parser.setInput(input, null)
    val ids = mutableListOf<String>()
    var inItem = false
    var id: String? = null
    var link: String? = null
    var title: String? = null
    // nextToken, not next: only it reports the DOCTYPE, before any entity it declares can be used.
    while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.DOCDECL) throw XmlPullParserException("DOCTYPE not allowed in a feed")
        val name = parser.name
        if (parser.eventType == XmlPullParser.START_TAG) {
            when (name) {
                "item", "entry" -> {
                    inItem = true
                    id = null
                    link = null
                    title = null
                }
                "guid", "id" -> if (inItem && id == null) id = parser.textOrNull()
                "link" -> if (inItem && link == null) link = parser.getAttributeValue(null, "href") ?: parser.textOrNull()
                "title" -> if (inItem && title == null) title = parser.textOrNull()
            }
        } else if (parser.eventType == XmlPullParser.END_TAG && (name == "item" || name == "entry")) {
            inItem = false
            listOf(id, link, title).firstOrNull { !it.isNullOrBlank() }?.let { ids += it }
            if (ids.size >= limit) break
        }
    }
    return ids
}

/** Null for an element holding markup rather than text (an Atom xhtml title), so one odd field cannot sink the feed. */
private fun XmlPullParser.textOrNull(): String? = runCatching { nextText().trim() }.getOrNull()

/** The first check of a feed only records what is there: saving a rule never fires it. */
internal fun hasNewItems(previous: Set<String>?, current: List<String>): Boolean =
    previous != null && current.any { it !in previous }

internal fun isPollDue(lastPollMs: Long?, nowMs: Long): Boolean =
    lastPollMs == null || nowMs - lastPollMs >= MIN_POLL_GAP_MS
