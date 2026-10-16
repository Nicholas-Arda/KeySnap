package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewDecisionTest {
    @Test
    fun freshInstallStoresBaselineWithoutShowing() {
        assertEquals(WhatsNewDecision.MARK_SEEN, decide(freshInstall = true))
    }

    @Test
    fun updateWithoutKeyShows() {
        assertEquals(WhatsNewDecision.SHOW, decide())
    }

    @Test
    fun alreadySeenDoesNotShow() {
        assertEquals(WhatsNewDecision.NONE, decide(lastSeen = 12L))
    }

    @Test
    fun onboardingNotFinishedDoesNotShow() {
        assertEquals(WhatsNewDecision.NONE, decide(onboardingFinished = false))
    }

    @Test
    fun setupWizardDefersUntilClosed() {
        assertEquals(WhatsNewDecision.NONE, decide(setupWizardShowing = true))
        assertEquals(WhatsNewDecision.SHOW, decide(setupWizardShowing = false))
    }

    @Test
    fun newerVersionShowsOnlyWithContent() {
        assertEquals(WhatsNewDecision.SHOW, decide(lastSeen = 11L))
        assertEquals(WhatsNewDecision.NONE, decide(lastSeen = 11L, hasContent = false))
    }

    @Test
    fun downgradeDoesNotShow() {
        assertEquals(WhatsNewDecision.NONE, decide(lastSeen = 13L))
    }

    @Test
    fun freshInstallDuringSetupStillEstablishesBaseline() {
        assertEquals(WhatsNewDecision.MARK_SEEN, decide(freshInstall = true, onboardingFinished = false, setupWizardShowing = true))
        assertEquals(WhatsNewDecision.NONE, decide(lastSeen = 12L, freshInstall = true))
    }

    @Test
    fun versionLabelStripsOnlyDevSuffix() {
        assertEquals("1.1.2", whatsNewVersionLabel("1.1.2-dev-release"))
        assertEquals("1.1.2", whatsNewVersionLabel("1.1.2-dev"))
        assertEquals("1.1.2", whatsNewVersionLabel("1.1.2"))
        assertEquals("1.1.2-beta", whatsNewVersionLabel("1.1.2-beta"))
    }

    private fun decide(
        lastSeen: Long? = null,
        freshInstall: Boolean = false,
        onboardingFinished: Boolean = true,
        setupWizardShowing: Boolean = false,
        hasContent: Boolean = true,
    ) = whatsNewDecision(lastSeen, 12L, freshInstall, onboardingFinished, setupWizardShowing, hasContent)
}
