package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RatingPromptRepositoryTest {
    @Test fun choicesAndDisplaysSurviveRepositoryRecreation() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("rating_prompt_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val first = RatingPromptRepository(prefs)
        assertEquals(RatingPromptState(), first.read())
        first.write(first.read().afterShowing().afterChoice(RatingPromptChoice.LATER, 123L))
        val second = RatingPromptRepository(prefs)
        assertEquals(RatingPromptState(shownCount = 1, deferredAt = 123L), second.read())
        second.write(second.read().afterShowing())
        assertEquals(RatingPromptState(shownCount = 2), RatingPromptRepository(prefs).read())
        second.write(second.read().afterChoice(RatingPromptChoice.NO_THANKS, 456L))
        assertEquals(RatingPromptState(shownCount = 2, finished = true), RatingPromptRepository(prefs).read())
    }
}
