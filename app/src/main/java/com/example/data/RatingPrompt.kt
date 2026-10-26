package com.example.data

private const val DAY_MS = 24L * 60L * 60L * 1_000L

data class RatingPromptState(
    val shownCount: Int = 0,
    val deferredAt: Long? = null,
    val finished: Boolean = false,
)

enum class RatingPromptChoice {
    RATE,
    LATER,
    NO_THANKS,
}

/** Only an explicit first "Later" permits a second request. No answer never causes nagging. */
fun ratingPromptEligible(
    firstInstallTime: Long,
    now: Long,
    successfulRuns: Long,
    state: RatingPromptState,
): Boolean {
    if (firstInstallTime <= 0L || now < firstInstallTime || now - firstInstallTime < 3L * DAY_MS) return false
    if (successfulRuns < 10L || state.finished) return false
    return when (state.shownCount) {
        0 -> true
        1 -> state.deferredAt?.let { it <= now && now - it >= 14L * DAY_MS } == true
        else -> false
    }
}

fun RatingPromptState.afterShowing(): RatingPromptState = copy(shownCount = shownCount + 1, deferredAt = null)

fun RatingPromptState.afterChoice(choice: RatingPromptChoice, now: Long): RatingPromptState =
    if (choice == RatingPromptChoice.LATER && shownCount == 1 && !finished) {
        copy(deferredAt = now)
    } else {
        copy(deferredAt = null, finished = true)
    }

/**
 * Owned by the Activity's ViewModel. Rotation keeps the launch. A cover (settings, a page
 * swipe, What's New) hides the card; the same launch shows it again until the user answers.
 * What's New, including one that arrives after the card, ends the launch's chance.
 */
class RatingPromptSession {
    private var claimed = false
    private var answered = false
    private var hadWhatsNew = false

    fun suppressForWhatsNew() {
        hadWhatsNew = true
    }

    fun markAnswered() {
        answered = true
    }

    /** True while this launch still owes the user the card they have not answered. */
    fun pending(homeReady: Boolean): Boolean = claimed && !answered && !hadWhatsNew && homeReady

    fun claim(eligible: Boolean, homeReady: Boolean): Boolean {
        if (claimed || hadWhatsNew || !eligible || !homeReady) return false
        claimed = true
        return true
    }
}
