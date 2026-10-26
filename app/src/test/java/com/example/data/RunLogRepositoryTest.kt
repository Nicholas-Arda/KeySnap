package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunLogRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private fun repository() = RunLogRepository(prefs, Moshi.Builder().build())
    private fun run(id: String, at: Long) = RunRecord(
        id, at, RunRecord.SOURCE_KEY, 24, "SINGLE_PRESS",
        listOf(ActionResult("toggle_wifi", ok = false, reason = RunFailure.ADVANCED_MODE_OFF.name)),
    )

    @Before fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(RunLogRepository.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test fun runsSurviveARestartNewestFirst() {
        val first = repository()
        first.append(run("a", 1L))
        first.append(run("a", 2L))
        assertEquals(listOf(2L, 1L), repository().runs.value.map { it.at })
    }

    @Test fun aCorruptStoreLoadsEmptyInsteadOfCrashing() {
        prefs.edit().putString(RunLogRepository.STORE_KEY, "{not json").commit()
        assertTrue(repository().runs.value.isEmpty())
        prefs.edit().putString(RunLogRepository.STORE_KEY, """{"runs":[{"at":1}]}""").commit()
        assertTrue(repository().runs.value.isEmpty())
    }

    @Test fun forgettingAShortcutDropsOnlyItsRuns() {
        val repo = repository()
        repo.append(run("a", 1L))
        repo.append(run("b", 2L))
        repo.forget("a")
        assertEquals(listOf("b"), repository().runs.value.map { it.scriptId })
    }

    @Test fun lifetimeSuccessesSurviveHistoryTrimmingDeletionAndRestart() {
        val repo = repository()
        repeat(120) { index ->
            repo.append(run("a", index.toLong()).copy(actions = listOf(ActionResult("vibrate", ok = true))))
        }
        repo.append(run("a", 121L))
        repo.append(run("a", 122L).copy(actions = emptyList()))
        repo.append(run("a", 123L).copy(actions = listOf(ActionResult("vibrate", true), ActionResult("toggle_wifi", false))))
        assertEquals(120L, repo.successfulRuns.value)
        assertEquals(120L, repository().successfulRuns.value)
        repo.forget("a")
        assertTrue(repository().runs.value.isEmpty())
        assertEquals(120L, repository().successfulRuns.value)
    }

    @Test fun legacyHistorySeedsOnlyKnownSuccessesOnce() {
        val repo = repository()
        repo.append(run("a", 1L).copy(actions = listOf(ActionResult("vibrate", ok = true))))
        repo.append(run("b", 2L))
        prefs.edit().remove(RunLogRepository.SUCCESS_COUNT_KEY).commit()
        val migrated = repository()
        assertEquals(1L, migrated.successfulRuns.value)
        migrated.forget("a")
        assertEquals(1L, repository().successfulRuns.value)
        migrated.append(run("b", 3L).copy(actions = listOf(ActionResult("vibrate", ok = true))))
        assertEquals(2L, repository().successfulRuns.value)
    }
}
