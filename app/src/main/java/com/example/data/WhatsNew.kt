package com.example.data

import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R

enum class WhatsNewDecision {
    NONE,
    MARK_SEEN,
    SHOW,
}

/** Fresh installs establish a baseline even while setup is open; updates wait for setup to finish. */
fun whatsNewDecision(
    lastSeenVersionCode: Long?,
    currentVersionCode: Long,
    freshInstall: Boolean,
    onboardingFinished: Boolean,
    setupWizardShowing: Boolean,
    hasContent: Boolean,
): WhatsNewDecision = when {
    lastSeenVersionCode == null && freshInstall -> WhatsNewDecision.MARK_SEEN
    !onboardingFinished || setupWizardShowing || !hasContent -> WhatsNewDecision.NONE
    lastSeenVersionCode == null || lastSeenVersionCode < currentVersionCode -> WhatsNewDecision.SHOW
    else -> WhatsNewDecision.NONE
}

enum class WhatsNewDestination {
    ADVANCED_MODE,
    APPEARANCE,
    ABOUT,
}

data class WhatsNewFeature(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val button: Int,
    val destination: WhatsNewDestination,
)

data class WhatsNewRelease(
    val features: List<WhatsNewFeature>,
    val fixes: List<Int>,
    /** Leads the dialog when a release has one headline that needs no button. */
    @StringRes val highlightTitle: Int? = null,
    @StringRes val highlightDescription: Int? = null,
)

private val release112Features = listOf(
    WhatsNewFeature(
        R.string.whats_new_restart_title,
        R.string.whats_new_restart_description,
        R.string.whats_new_turn_on,
        WhatsNewDestination.ADVANCED_MODE,
    ),
    WhatsNewFeature(
        R.string.whats_new_theme_title,
        R.string.whats_new_theme_description,
        R.string.whats_new_try,
        WhatsNewDestination.APPEARANCE,
    ),
    WhatsNewFeature(
        R.string.whats_new_recording_title,
        R.string.whats_new_recording_description,
        R.string.whats_new_open,
        WhatsNewDestination.ABOUT,
    ),
)

private val release112Fixes = listOf(
    R.string.whats_new_fix_assistant,
    R.string.whats_new_fix_vibration,
    R.string.whats_new_fix_uninstall,
    R.string.whats_new_fix_search,
)

/** Add the next release under its version code; a version without an entry has no dialog. */
val whatsNewReleases = mapOf(
    12L to WhatsNewRelease(release112Features, release112Fixes),
    // 1.1.3 repeats 1.1.2's news: most updates skip straight past 1.1.2.
    13L to WhatsNewRelease(
        features = release112Features,
        fixes = listOf(R.string.whats_new_fix_large_text, R.string.whats_new_fix_nav_bar) + release112Fixes,
        highlightTitle = R.string.whats_new_no_ads_title,
        highlightDescription = R.string.whats_new_no_ads_description,
    ),
)

fun whatsNewVersionLabel(versionName: String): String = versionName.substringBefore("-dev")
