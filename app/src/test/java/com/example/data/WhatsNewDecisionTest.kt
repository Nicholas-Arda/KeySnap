package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.example.mode.ModeHooks
import io.github.nicholasarda.keysnap.BuildConfig
import io.github.nicholasarda.keysnap.R
import org.junit.Test

class WhatsNewDecisionTest {
    @Test fun currentReleaseHasNewsInBothEditions() {
        assertNotNull(whatsNewReleases[BuildConfig.VERSION_CODE.toLong()])
        val release = whatsNewReleases.getValue(16L)
        assertEquals(
            listOf(R.string.whats_new_assistant_gesture_title, R.string.whats_new_home_only_title),
            release.features.take(2).map { it.title },
        )
        assertEquals(ModeHooks.whatsNewFeatures(16L), release.features.drop(2))
        assertEquals(if (ModeHooks.AVAILABLE) 3 else 2, release.features.size)
        assertEquals(List(release.features.size) { WhatsNewDestination.SHORTCUTS }, release.features.map { it.destination })
        assertEquals(listOf(R.string.whats_new_fix_coloros_dnd, R.string.whats_new_fix_conditional_volume), release.fixes)
        assertEquals(emptyList<WhatsNewFeature>(), ModeHooks.whatsNewFeatures(15L))
    }

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
    fun announcementRunsForFortyEightHoursUnlessTheLogoDismissesIt() {
        val start = 1_700_000_000_000L
        assertFalse(shouldAnnounceWhatsNew(16L, startedAtMillis = null, nowMillis = start, dismissedVersionCode = null))
        assertFalse(shouldAnnounceWhatsNew(16L, start, start - 1L, null))
        assertTrue(shouldAnnounceWhatsNew(16L, start, start, null))
        assertTrue(shouldAnnounceWhatsNew(16L, start, start + WhatsNewAnnounceWindowMs - 1L, null))
        assertFalse(shouldAnnounceWhatsNew(16L, start, start + WhatsNewAnnounceWindowMs, null))
        assertFalse(shouldAnnounceWhatsNew(16L, start, start + 1_000L, dismissedVersionCode = 16L))
        assertTrue(shouldAnnounceWhatsNew(17L, start, start + 1_000L, dismissedVersionCode = 16L))
    }

    @Test
    fun announcementWindowOpensWhenTheDialogAutoShows() {
        val start = 1_700_000_000_000L
        assertEquals(null, whatsNewAnnouncementStartedAt(16L, null, null, start, autoShowing = false))
        assertEquals(start, whatsNewAnnouncementStartedAt(16L, null, null, start, autoShowing = true))
        assertEquals(start, whatsNewAnnouncementStartedAt(16L, 16L, start, start + 5_000L, autoShowing = false))
        assertEquals(start, whatsNewAnnouncementStartedAt(16L, 16L, start, start + 5_000L, autoShowing = true))
        assertEquals(null, whatsNewAnnouncementStartedAt(17L, 16L, start, start + 9_000L, autoShowing = false))
        assertEquals(start + 9_000L, whatsNewAnnouncementStartedAt(17L, 16L, start, start + 9_000L, autoShowing = true))
    }

    @Test
    fun manualOpenUsesThisVersionOrTheNewestReleaseNotAfterIt() {
        assertEquals(whatsNewReleases.getValue(16L), whatsNewReleaseFor(16L))
        assertEquals(whatsNewReleases.getValue(16L), whatsNewReleaseFor(99L))
        if (whatsNewReleases[15L] == null) {
            val below = whatsNewReleases.keys.filter { it <= 15L }.maxOrNull()
            assertNotNull(below)
            assertEquals(whatsNewReleases.getValue(below!!), whatsNewReleaseFor(15L))
        }
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
