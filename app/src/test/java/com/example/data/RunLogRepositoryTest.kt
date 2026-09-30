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
}
