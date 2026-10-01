package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class RssFeedTest {

    private fun ids(xml: String, limit: Int = MAX_FEED_ITEMS) = parseFeedItemIds(xml.byteInputStream(), limit)

    @Test fun rssItemsAreIdentifiedByGuidThenLinkThenTitle() {
        val xml = """<?xml version="1.0"?><rss version="2.0"><channel><title>Feed</title><link>https://site</link>
            <item><title>A</title><link>https://site/a</link><guid isPermaLink="false">id-a</guid></item>
            <item><title>B</title><link>https://site/b</link></item>
            <item><title>C</title></item>
            <item><description>nothing to name it by</description></item>
            </channel></rss>"""
        assertEquals(listOf("id-a", "https://site/b", "C"), ids(xml))
    }

    @Test fun atomEntriesAreIdentifiedByIdThenLinkHref() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom"><id>urn:feed</id><title>F</title>
            <entry><id>urn:1</id><title type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml">X</div></title></entry>
            <entry><link rel="alternate" href="https://site/2"/><title>Two</title></entry>
            </feed>"""
        assertEquals(listOf("urn:1", "https://site/2"), ids(xml))
    }

    @Test fun stopsAtTheLimit() {
        val items = (1..5).joinToString("") { "<item><guid>$it</guid></item>" }
        assertEquals(listOf("1", "2"), ids("<rss><channel>$items</channel></rss>", limit = 2))
    }

    @Test fun aWellFormedPageThatIsNotAFeedHasNoItems() {
        assertEquals(emptyList<String>(), ids("<html><head><title>Blog</title></head><body><p>Hi</p></body></html>"))
    }

    /** No feed needs a DTD, and an internal subset is where entity-expansion bombs live. */
    @Test fun aDoctypeAbortsTheParse() {
        val bomb = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;">]>
            <rss><channel><item><title>&b;</title></item></channel></rss>"""
        assertThrows(XmlPullParserException::class.java) { ids(bomb) }
    }

    @Test fun anOversizedBodyAborts() {
        val items = (1..200).joinToString("") { "<item><guid>$it</guid><description>${"x".repeat(100)}</description></item>" }
        val body = "<rss><channel>$items</channel></rss>"
        assertThrows(IOException::class.java) {
            parseFeedItemIds(CappedInputStream(body.byteInputStream(), maxBytes = 1_000), limit = 1_000)
        }
        // Under the cap it reads normally.
        assertEquals(200, parseFeedItemIds(CappedInputStream(body.byteInputStream(), maxBytes = body.length + 1L), limit = 1_000).size)
    }

    @Test fun aBodyPastTheDeadlineAborts() {
        val late = CappedInputStream("<rss/>".byteInputStream(), deadlineNanos = System.nanoTime() - 1)
        assertThrows(IOException::class.java) { late.read(ByteArray(16), 0, 16) }
    }

    @Test fun theFirstCheckOfAFeedOnlyRecords() {
        assertFalse(hasNewItems(previous = null, current = listOf("a", "b")))
    }

    @Test fun onlyAnUnseenIdCountsAsNew() {
        assertFalse(hasNewItems(setOf("a", "b"), listOf("b", "a")))
        assertTrue(hasNewItems(setOf("a", "b"), listOf("c", "a")))
        assertFalse("an empty read is not news", hasNewItems(setOf("a"), emptyList()))
    }

    @Test fun checksAreAtLeastFifteenMinutesApart() {
        assertTrue(isPollDue(lastPollMs = null, nowMs = 0))
        assertFalse(isPollDue(lastPollMs = 0, nowMs = MIN_POLL_GAP_MS - 1))
        assertTrue(isPollDue(lastPollMs = 0, nowMs = MIN_POLL_GAP_MS))
    }
}
