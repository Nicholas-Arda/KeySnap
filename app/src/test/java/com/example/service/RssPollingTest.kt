package com.example.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class RssPollingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun script(url: String, enabled: Boolean = true, automation: Boolean = true) = ShortcutScript(
        id = "$url-$enabled-$automation",
        name = "feed",
        enabled = enabled,
        triggers = emptyList(),
        actions = listOf(ScriptAction("toggle_flashlight")),
        constraints = listOf(ScriptConstraint("rss_new_item", mapOf("url" to url))),
        automation = automation,
    )

    @Test fun followsEachEnabledAutomationFeedOnceExactlyAsTyped() {
        val typed = "https://Example.com/feed?token=a%20b"
        val scripts = listOf(script(typed), script(typed), script("https://off", enabled = false), script("https://key", automation = false))
        assertEquals(listOf(typed), RssPolling.feedUrls(scripts))
    }

    @Test fun theNewItemFactHoldsForExactlyOneReevaluation() {
        val evaluator = AndroidConstraintEvaluator(context)
        val mine = ScriptConstraint("rss_new_item", mapOf("url" to "https://a"))
        val other = ScriptConstraint("rss_new_item", mapOf("url" to "https://b"))
        val seen = mutableListOf<Pair<Boolean, Boolean>>()
        val look = { seen += evaluator.isSatisfied(mine) to evaluator.isSatisfied(other) }
        RssPolling.pulse("https://a", seed = look, reevaluate = look)
        // The seed sees it false, the pulse true for one evaluation, then false again.
        assertEquals(listOf(false to false, true to false, false to false), seen)
        assertFalse(evaluator.isSatisfied(mine))
    }

    /** As in a process the feed job just started: the scripts collector has not seeded this automation yet. */
    @Test fun aNewItemFiresAnAutomationWhoseEdgeAFreshProcessDoesNotKnowYet() {
        val url = "https://example.org/cold"
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        ScriptRepository.getInstance(context).upsert(script(url).copy(id = "qa-rss-cold", vibrateOnTrigger = false))
        val before = HardwareKeyTriggerCoordinator.triggerCount.value
        RssPolling.pulse(url, reevaluate = HardwareKeyTriggerCoordinator::reevaluateAutomations)
        idle()
        assertEquals(before + 1, HardwareKeyTriggerCoordinator.triggerCount.value)
        ScriptRepository.getInstance(context).delete("qa-rss-cold")
    }

    /** Pins why the seed exists: without it, a first sighting only records and the new item is lost. */
    @Test fun withoutTheSeedAFreshProcessOnlyRecords() {
        val url = "https://example.org/cold-unseeded"
        HardwareKeyTriggerCoordinator.initialize(context, KeyMappingRepository.getInstance(context))
        ScriptRepository.getInstance(context).upsert(script(url).copy(id = "qa-rss-unseeded", vibrateOnTrigger = false))
        val before = HardwareKeyTriggerCoordinator.triggerCount.value
        RssPolling.pulse(url, seed = {}, reevaluate = HardwareKeyTriggerCoordinator::reevaluateAutomations)
        idle()
        assertEquals(before, HardwareKeyTriggerCoordinator.triggerCount.value)
        ScriptRepository.getInstance(context).delete("qa-rss-unseeded")
    }

    /** A paused global mapping disables every script: no request, and nothing to replay afterwards. */
    @Test fun nothingIsFetchedWhilePausedAndTheSeenItemsAreForgotten() = runBlocking<Unit> {
        val url = "https://127.0.0.1:9/paused"
        val repository = ScriptRepository.getInstance(context)
        repository.upsert(script(url).copy(id = "qa-rss-paused"))
        val prefs = context.getSharedPreferences("rss_feed_state", Context.MODE_PRIVATE)
        prefs.edit().putStringSet(url, setOf("seen-before-the-pause")).commit()
        repository.setGloballyEnabled(false)
        try {
            ShadowLog.clear()
            RssPolling.pollOnce(context)
            assertTrue("no fetch was attempted", ShadowLog.getLogsForTag("RssPolling").isEmpty())
            assertTrue("the first check after the pause only records", prefs.all.isEmpty())
        } finally {
            repository.setGloballyEnabled(true)
            repository.delete("qa-rss-paused")
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun schedulesAFifteenMinuteOnlineJobWhileAFeedIsFollowedAndCancelsItAfter() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        RssPolling.sync(context, listOf(script("https://a")))
        val job = scheduler.allPendingJobs.single()
        assertEquals(15 * 60_000L, job.intervalMillis)
        @Suppress("DEPRECATION")
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
        RssPolling.sync(context, emptyList())
        assertTrue(scheduler.allPendingJobs.isEmpty())
    }

    @Test fun aFailedFetchNeverLogsTheAddress() {
        ShadowLog.clear()
        assertNull(RssPolling.fetchItemIds("https://127.0.0.1:9/feed?token=s3cret"))
        assertTrue("the failure is logged", ShadowLog.getLogsForTag("RssPolling").isNotEmpty())
        ShadowLog.getLogs().forEach { log ->
            val text = log.msg + " " + (log.throwable?.message ?: "")
            assertFalse(text, "s3cret" in text || "127.0.0.1" in text)
        }
    }
}
