package com.example.data

import android.content.Context
import androidx.annotation.StringRes
import com.example.ai.AiHooks
import com.example.capsule.CapsuleHooks
import com.example.mode.ModeHooks
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
    KEY_CAPSULE,
    SHORTCUTS,
    NEW_SHORTCUT,
    SENSITIVE_APPS,
    /** A new shortcut with the action picker open on its New tile. */
    ACTION_PICKER_NEW,
    /** A new shortcut with the action picker open on the phone-brand tile. */
    ACTION_PICKER_PHONE,
}

data class WhatsNewFeature(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val button: Int,
    val destination: WhatsNewDestination,
    /** False on a phone that cannot use the feature: the card says so instead of offering its button. */
    val supportedOn: ((Context) -> Boolean)? = null,
)

data class WhatsNewRelease(
    val features: List<WhatsNewFeature>,
    val fixes: List<Int>,
    /** Leads the dialog when a release has one headline that needs no button. */
    @StringRes val highlightTitle: Int? = null,
    @StringRes val highlightDescription: Int? = null,
    /** Heading over [fixes]; a release whose short items are new features says so instead of "Fixed". */
    @StringRes val fixesTitle: Int = R.string.whats_new_fixed,
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
private val release120 = WhatsNewRelease(
    features = listOf(
        WhatsNewFeature(
            R.string.whats_new_test_editors_title_120,
            R.string.whats_new_test_editors_description_120,
            R.string.whats_new_try,
            WhatsNewDestination.SHORTCUTS,
        ),
        WhatsNewFeature(
            R.string.whats_new_reorder_shortcuts_title_120,
            R.string.whats_new_reorder_shortcuts_description_120,
            R.string.whats_new_try,
            WhatsNewDestination.SHORTCUTS,
        ),
        WhatsNewFeature(
            R.string.whats_new_input_devices_title_120,
            R.string.whats_new_input_devices_description_120,
            R.string.whats_new_try,
            WhatsNewDestination.SHORTCUTS,
        ),
    ),
    fixes = listOfNotNull(
        R.string.whats_new_fix_setup_120,
        R.string.whats_new_fix_conditions_120,
        R.string.whats_new_fix_app_names_120,
        R.string.whats_new_fix_side_power_keys_120,
        R.string.whats_new_fix_idle_battery_120,
        R.string.whats_new_fix_advanced_setup_120,
        R.string.whats_new_fix_saved_keys_120,
        R.string.whats_new_fix_shortcuts_page_120,
        R.string.whats_new_fix_modes_120.takeIf { ModeHooks.AVAILABLE },
    ),
)

val whatsNewReleases = mapOf(
    12L to WhatsNewRelease(release112Features, release112Fixes),
    // 1.1.3 repeats 1.1.2's news: most updates skip straight past 1.1.2.
    13L to WhatsNewRelease(
        features = release112Features,
        fixes = listOf(R.string.whats_new_fix_large_text, R.string.whats_new_fix_nav_bar) + release112Fixes,
        highlightTitle = R.string.whats_new_no_ads_title,
        highlightDescription = R.string.whats_new_no_ads_description,
    ),
    16L to WhatsNewRelease(
        features = listOf(
            WhatsNewFeature(
                R.string.whats_new_assistant_gesture_title,
                R.string.whats_new_assistant_gesture_description,
                R.string.whats_new_try,
                WhatsNewDestination.SHORTCUTS,
            ),
            WhatsNewFeature(
                R.string.whats_new_home_only_title,
                R.string.whats_new_home_only_description,
                R.string.whats_new_try,
                WhatsNewDestination.SHORTCUTS,
            ),
        ) + ModeHooks.whatsNewFeatures(16L),
        fixes = listOf(R.string.whats_new_fix_coloros_dnd, R.string.whats_new_fix_conditional_volume),
    ),
    17L to WhatsNewRelease(
        features = CapsuleHooks.whatsNewRelease(17L)?.features.orEmpty() + listOf(
            WhatsNewFeature(
                R.string.whats_new_editor_title,
                R.string.whats_new_editor_description,
                R.string.whats_new_try,
                WhatsNewDestination.SHORTCUTS,
            ),
        ),
        fixes = listOf(
            R.string.whats_new_fix_assistant,
            R.string.whats_new_fix_update_startup,
            R.string.whats_new_fix_large_text,
        ),
        highlightTitle = R.string.whats_new_dutch_title,
        highlightDescription = R.string.whats_new_dutch_description,
    ),
    // 1.1.8 is a polish update: no headline, only what changed.
    19L to WhatsNewRelease(
        features = emptyList(),
        fixes = listOfNotNull(
            R.string.whats_new_fix_capsule_preview_118.takeIf { CapsuleHooks.AVAILABLE },
            R.string.whats_new_fix_settings_motion_118,
            R.string.whats_new_fix_pro_card_118,
            R.string.whats_new_fix_key_code_118,
        ),
    ),
    20L to release120,
    21L to release120.copy(fixes = listOf(R.string.whats_new_fix_filipino_121) + release120.fixes),
    // 1.2.1 never reached Play (1.2.0 shipped 2026-10-31), so 1.3.0 carries its new language. The AI builder is Pro-only.
    22L to WhatsNewRelease(features = AiHooks.whatsNewFeatures(22L), fixes = listOf(R.string.whats_new_fix_filipino_121)),
    // 1.3.0's rollout was halted early, so most phones come from 1.2.0 and 1.3.1 carries its news too.
    23L to WhatsNewRelease(
        features = AiHooks.whatsNewFeatures(22L),
        fixes = listOf(
            R.string.whats_new_fix_colors_131,
            // One item lists every language added since 1.2.0, Filipino included.
            R.string.whats_new_fix_languages_131,
        ),
        highlightTitle = R.string.whats_new_widgets_title_131,
        highlightDescription = R.string.whats_new_widgets_description_131,
    ),
    // 1.3.2 ships as code 25: code 24 reached Play's bundle library but never a user.
    25L to WhatsNewRelease(
        features = listOf(
            WhatsNewFeature(
                R.string.whats_new_teach_title_132,
                R.string.whats_new_teach_description_132,
                R.string.whats_new_try,
                WhatsNewDestination.SHORTCUTS,
            ),
            WhatsNewFeature(
                R.string.whats_new_place_title_132,
                R.string.whats_new_place_description_132,
                R.string.whats_new_try,
                WhatsNewDestination.SHORTCUTS,
            ),
            WhatsNewFeature(
                R.string.whats_new_actions_title_132,
                R.string.whats_new_actions_description_132,
                R.string.whats_new_try,
                WhatsNewDestination.SHORTCUTS,
            ),
        ),
        fixes = listOf(
            R.string.whats_new_fix_conditions_132,
            R.string.whats_new_fix_undo_132,
            R.string.whats_new_fix_vibration_132,
            R.string.whats_new_fix_foldable_132,
        ),
        fixesTitle = R.string.whats_new_also_new_132,
    ),
    26L to WhatsNewRelease(
        features = listOf(
            WhatsNewFeature(
                R.string.whats_new_sensitive_title_133,
                R.string.whats_new_sensitive_description_133,
                R.string.whats_new_open,
                WhatsNewDestination.SENSITIVE_APPS,
            ),
            WhatsNewFeature(
                R.string.whats_new_picker_title_133,
                R.string.whats_new_picker_description_133,
                R.string.whats_new_try,
                WhatsNewDestination.ACTION_PICKER_NEW,
            ),
            // Shown everywhere; usable only where one of those actions exists (OemActions).
            WhatsNewFeature(
                R.string.whats_new_oem_title_133,
                R.string.whats_new_oem_description_133,
                R.string.whats_new_try,
                WhatsNewDestination.ACTION_PICKER_PHONE,
                supportedOn = { context -> OemActions.TYPES.any { OemActions.offered(context, it) } },
            ),
        ),
        fixes = listOf(
            R.string.whats_new_fix_actions_133,
            R.string.whats_new_fix_single_press_133,
            R.string.whats_new_fix_pairing_133,
            R.string.whats_new_fix_app_automation_133,
            R.string.whats_new_fix_large_text_133,
        ),
        fixesTitle = R.string.whats_new_more_133,
    ),
).filterValues { it.features.isNotEmpty() || it.fixes.isNotEmpty() || it.highlightTitle != null } +
    listOf(14L, 15L).mapNotNull { code -> CapsuleHooks.whatsNewRelease(code)?.let { code to it } }.toMap()

fun whatsNewVersionLabel(versionName: String): String = versionName.substringBefore("-dev")

/** Wall-clock length of the header announcement that follows an update. */
const val WhatsNewAnnounceWindowMs = 48L * 60L * 60L * 1000L

/**
 * Whether the header should still announce [versionCode].
 *
 * [startedAtMillis] is the first launch that auto-showed this version's dialog, or null when that
 * launch has not happened. [dismissedVersionCode] is the version whose logo tap ended the announcement.
 * A missing start, a dismissal of this version, or a closed 48 h window all stay quiet.
 */
fun shouldAnnounceWhatsNew(
    versionCode: Long,
    startedAtMillis: Long?,
    nowMillis: Long,
    dismissedVersionCode: Long?,
): Boolean {
    if (dismissedVersionCode == versionCode) return false
    val started = startedAtMillis ?: return false
    if (nowMillis < started) return false
    return nowMillis - started < WhatsNewAnnounceWindowMs
}

/**
 * Start time to keep for [versionCode]. The window opens on the launch that auto-shows the dialog
 * and stays put after that; a newer version does not inherit the previous window.
 */
fun whatsNewAnnouncementStartedAt(
    versionCode: Long,
    storedVersionCode: Long?,
    storedStartedAtMillis: Long?,
    nowMillis: Long,
    autoShowing: Boolean,
): Long? = when {
    storedVersionCode == versionCode && storedStartedAtMillis != null -> storedStartedAtMillis
    autoShowing -> nowMillis
    else -> null
}

/** This version's notes, or the newest release that is not newer than it. */
fun whatsNewReleaseFor(versionCode: Long): WhatsNewRelease? =
    whatsNewReleases[versionCode]
        ?: whatsNewReleases.filterKeys { it <= versionCode }.maxByOrNull { it.key }?.value
        ?: whatsNewReleases.maxByOrNull { it.key }?.value
