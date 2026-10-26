package com.example.data

import android.content.SharedPreferences

/** Counts and choices only; no rating, account, shortcut, or device details. */
class RatingPromptRepository(private val prefs: SharedPreferences) {
    fun read(): RatingPromptState = RatingPromptState(
        shownCount = prefs.getInt("rating_prompt_shown_count", 0),
        deferredAt = if (prefs.contains("rating_prompt_deferred_at")) prefs.getLong("rating_prompt_deferred_at", 0L) else null,
        finished = prefs.getBoolean("rating_prompt_finished", false),
    )

    fun write(state: RatingPromptState) {
        prefs.edit()
            .putInt("rating_prompt_shown_count", state.shownCount)
            .putBoolean("rating_prompt_finished", state.finished)
            .apply {
                if (state.deferredAt == null) remove("rating_prompt_deferred_at")
                else putLong("rating_prompt_deferred_at", state.deferredAt)
            }
            .apply()
    }
}
