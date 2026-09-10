package com.example.ui.home

/** Where a setup step sits in the tutorial rail. */
enum class SetupStepState { Done, Current, Upcoming }

/**
 * The rail's shape for the three real permission signals, in dependency order. The first unfinished
 * step is the current one — so exactly one step is ever open — and everything after it is dimmed
 * rather than offered, because a later step is pointless while an earlier one is missing.
 *
 * Pure on purpose: this is the part of the tutorial that has a right answer, and
 * `SetupStepStateTest` covers it without a Compose or Robolectric runtime.
 */
fun setupStepStates(done: List<Boolean>): List<SetupStepState> {
    val current = done.indexOf(false)
    return done.mapIndexed { index, isDone ->
        when {
            isDone -> SetupStepState.Done
            index == current -> SetupStepState.Current
            else -> SetupStepState.Upcoming
        }
    }
}
