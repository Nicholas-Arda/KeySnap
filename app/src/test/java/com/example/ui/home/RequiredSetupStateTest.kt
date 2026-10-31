package com.example.ui.home

import org.junit.Assert.*
import org.junit.Test

class RequiredSetupStateTest {
    @Test fun accessibilityIsFirstEvenWhenBackgroundAlreadyGranted() {
        for (background in listOf(false, true)) {
            val state = RequiredSetupState(false, background, false)
            assertEquals(SetupStep.Accessibility, state.nextStep)
            assertEquals(SetupBanner.NotRunning, state.banner)
            assertEquals(SetupPill.Setup, state.pill(true))
            assertEquals(SetupPill.Setup, state.pill(false))
        }
    }

    @Test fun backgroundIsTheSecondRequiredStep() {
        val state = RequiredSetupState(true, false, true)
        assertEquals(1, state.completeCount)
        assertEquals(SetupStep.Background, state.nextStep)
        assertEquals(SetupBanner.BackgroundRisk, state.banner)
        assertFalse(state.showNotifications)
    }

    @Test fun notificationsNeverBlockLiveOrChangeRequiredProgress() {
        for (notifications in listOf(false, true)) {
            val state = RequiredSetupState(true, true, notifications)
            assertEquals(2, state.completeCount)
            assertEquals(SetupPill.Live, state.pill(true))
            assertEquals(SetupPill.Paused, state.pill(false))
            assertNull(state.banner)
            assertNull(state.nextStep)
            assertEquals(!notifications, state.showNotifications)
        }
    }

    @Test fun notificationChoiceIsOptionalAndOnlyShownAfterRequiredSteps() {
        assertFalse(RequiredSetupState(false, false, false).showNotifications)
        assertFalse(RequiredSetupState(true, false, false).showNotifications)
        assertTrue(RequiredSetupState(true, true, false).showNotifications)
        assertFalse(RequiredSetupState(true, true, false, notificationsDismissed = true).showNotifications)
        assertFalse(RequiredSetupState(true, true, true).showNotifications)
    }
}
