package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingPromptTest {
    private val day = 86_400_000L
    private val installed = 100L * day
    private val now = installed + 3L * day

    private fun eligible(
        state: RatingPromptState = RatingPromptState(),
        time: Long = now,
        count: Long = 10L,
        install: Long = installed,
    ) = ratingPromptEligible(install, time, count, state)

    @Test fun needsBothThreeFullDaysAndTenSuccessfulRuns() {
        assertTrue(eligible())
        assertFalse(eligible(time = now - 1L))
        assertFalse(eligible(count = 9L))
        assertFalse(eligible(count = 0L))
        assertTrue(eligible(count = 100L))
    }

    @Test fun missingOrFutureInstallTimeNeverQualifies() {
        assertFalse(eligible(install = 0L))
        assertFalse(eligible(install = -1L))
        assertFalse(eligible(install = now + 1L))
    }

    @Test fun laterAllowsOneReminderAfterFourteenFullDays() {
        val deferred = RatingPromptState().afterShowing().afterChoice(RatingPromptChoice.LATER, now)
        assertFalse(eligible(deferred, time = now + 14L * day - 1L))
        assertFalse(eligible(deferred, time = now - 1L))
        assertTrue(eligible(deferred, time = now + 14L * day))
        assertFalse(eligible(deferred, time = now + 14L * day, count = 9L))
        val reminder = deferred.afterShowing()
        for (choice in RatingPromptChoice.entries) {
            assertFalse(eligible(reminder.afterChoice(choice, now + 14L * day), time = now + 100L * day))
        }
    }

    @Test fun rateAndNoThanksArePermanent() {
        for (choice in listOf(RatingPromptChoice.RATE, RatingPromptChoice.NO_THANKS)) {
            val state = RatingPromptState().afterShowing().afterChoice(choice, now)
            assertFalse(eligible(state, time = now + 100L * day))
        }
    }

    @Test fun leavingWithoutAnAnswerDoesNotKeepAsking() {
        assertFalse(eligible(RatingPromptState().afterShowing(), time = now + 100L * day))
        assertFalse(eligible(RatingPromptState(shownCount = 2, deferredAt = now), time = now + 100L * day))
    }

    @Test fun setupOrOtherScreensDoNotConsumeTheLaunchOpportunity() {
        val session = RatingPromptSession()
        assertFalse(session.claim(eligible = true, homeReady = false))
        assertFalse(session.claim(eligible = false, homeReady = true))
        assertTrue(session.claim(eligible = true, homeReady = true))
        assertFalse(session.claim(eligible = true, homeReady = true))
    }

    @Test fun whatsNewSuppressesTheEntireLaunchEvenAfterDismissal() {
        val session = RatingPromptSession()
        session.suppressForWhatsNew()
        repeat(3) { assertFalse(session.claim(eligible = true, homeReady = true)) }
        assertTrue(RatingPromptSession().claim(eligible = true, homeReady = true))
    }

    @Test fun aCoverHidesTheCardAndTheSameLaunchShowsItAgainUntilAnswered() {
        val session = RatingPromptSession()
        assertTrue(session.claim(eligible = true, homeReady = true))
        assertFalse(session.pending(homeReady = false))
        assertTrue(session.pending(homeReady = true))
        session.suppressForWhatsNew()
        assertFalse(session.pending(homeReady = true))
        assertFalse(session.claim(eligible = true, homeReady = true))
    }

    @Test fun anAnswerEndsTheLaunchEvenIfHomeBecomesReadyAgain() {
        val session = RatingPromptSession()
        assertTrue(session.claim(eligible = true, homeReady = true))
        session.markAnswered()
        assertFalse(session.pending(homeReady = true))
        assertFalse(session.claim(eligible = true, homeReady = true))
    }
}
