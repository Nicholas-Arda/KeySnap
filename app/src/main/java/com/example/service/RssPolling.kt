package com.example.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wake-up source for `rss_new_item`. A JobScheduler job, only while online and never more than
 * every 15 minutes, fetches each followed feed and holds "new item" true for exactly one automation
 * re-evaluation. No server: the phone fetches the address the user typed, unchanged.
 */
object RssPolling {
    private const val TAG = "RssPolling"

    // High, so it cannot meet an id another library's scheduler hands out counting up from 0.
    private const val JOB_ID = 0x4B535201
    private const val PREFS = "rss_feed_state"
    private const val LAST_POLL = "__last_poll"

    @Volatile private var pulsing: String? = null

    fun isPulsing(url: String?): Boolean = url != null && url == pulsing

    /** Feeds on enabled automations, each once, exactly as typed. */
    fun feedUrls(scripts: List<ShortcutScript>): List<String> = scripts
        .filter { it.automation && it.enabled }
        .flatMap { it.constraints }
        .filter { it.type == ScriptConstraintCatalog.RSS_NEW_ITEM }
        .mapNotNull { it.params["url"]?.takeIf(String::isNotBlank) }
        .distinct()

    /** Schedules the job while any feed is followed and cancels it otherwise; forgets feeds no longer followed. */
    fun sync(context: Context, scripts: List<ShortcutScript>) {
        val urls = feedUrls(scripts).toSet()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { prefs.all.keys.filter { it != LAST_POLL && it !in urls }.forEach { remove(it) } }
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (urls.isEmpty()) {
            scheduler.cancel(JOB_ID)
            return
        }
        // Re-scheduling restarts the period, so an unrelated edit must not push the next check back.
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, RssPollJobService::class.java))
                .setPeriodic(JobInfo.getMinPeriodMillis())
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build(),
        )
    }

    /**
     * Holds [url]'s "new item" true for exactly one re-evaluation, then records it false again.
     * [seed] runs first, while it still reads false, so an automation whose edge a freshly started
     * process does not know yet sees the new item as a false -> true edge. Main thread only.
     */
    internal fun pulse(
        url: String,
        seed: () -> Unit = HardwareKeyTriggerCoordinator::seedUnknownAutomations,
        reevaluate: () -> Unit,
    ) {
        seed()
        pulsing = url
        try {
            reevaluate()
        } finally {
            pulsing = null
        }
        reevaluate()
    }

    suspend fun pollOnce(context: Context) {
        val urls = feedUrls(ScriptRepository.getInstance(context).scripts.value)
        if (urls.isEmpty()) {
            // Nothing follows a feed any more: leave nothing scheduled.
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // A paused global mapping disables every script, so nothing is fetched. The items seen so
        // far are forgotten, so the first check after the pause only records: what was published
        // during it is not replayed, as for every other automation.
        if (!ScriptRepository.getInstance(context).globallyEnabled.value) {
            prefs.edit { clear() }
            return
        }
        // The job can start this process cold, before the Accessibility service or the app has.
        withContext(Dispatchers.Main) { HardwareKeyTriggerCoordinator.ensureInitialized(context) }
        val now = System.currentTimeMillis()
        if (!isPollDue(prefs.getLong(LAST_POLL, -1L).takeIf { it >= 0 }, now)) return
        prefs.edit { putLong(LAST_POLL, now) }
        urls.forEach { url ->
            val ids = fetchItemIds(url) ?: return@forEach // a failed check keeps the last known items
            val previous = prefs.getStringSet(url, null)
            prefs.edit { putStringSet(url, ids.toSet()) }
            if (hasNewItems(previous, ids)) {
                withContext(Dispatchers.Main) { pulse(url, reevaluate = HardwareKeyTriggerCoordinator::reevaluateAutomations) }
            }
        }
    }

    /** Null on any failure. Logs the exception class only: the URL, and messages that echo its host, can carry tokens. */
    internal fun fetchItemIds(url: String): List<String>? = try {
        // The deadline starts before connecting, so a slow handshake counts against it too.
        val deadline = System.nanoTime() + MAX_FEED_FETCH_MS * 1_000_000
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode in 200..299) {
                CappedInputStream(connection.inputStream, deadlineNanos = deadline).use { parseFeedItemIds(it) }
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Feed check failed: ${e.javaClass.simpleName}")
        null
    }
}

class RssPollJobService : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(Dispatchers.IO).launch {
            RssPolling.pollOnce(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }
}
