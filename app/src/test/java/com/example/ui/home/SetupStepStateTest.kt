package com.example.ui.home

import com.example.ui.home.SetupStepState.Current
import com.example.ui.home.SetupStepState.Done
import com.example.ui.home.SetupStepState.Upcoming
import org.junit.Assert.assertEquals
import org.junit.Test

/** The tutorial's one piece of logic: which step is open, and what every other row reports. */
class SetupStepStateTest {

    @Test fun firstLaunchOpensTheFirstStepAndDimsTheRest() {
        assertEquals(
            listOf(Current, Upcoming, Upcoming),
            setupStepStates(listOf(false, false, false)),
        )
    }

    @Test fun finishingAStepHandsTheOpenSlotToTheNextOne() {
        assertEquals(
            listOf(Done, Current, Upcoming),
            setupStepStates(listOf(true, false, false)),
        )
        assertEquals(
            listOf(Done, Done, Current),
            setupStepStates(listOf(true, true, false)),
        )
    }

    /** Android can grant a later permission first; that step reports Done without opening ahead. */
    @Test fun aStepGrantedOutOfOrderIsDoneWhileAnEarlierOneIsStillOpen() {
        assertEquals(
            listOf(Current, Done, Upcoming),
            setupStepStates(listOf(false, true, false)),
        )
        assertEquals(
            listOf(Done, Current, Done),
            setupStepStates(listOf(true, false, true)),
        )
    }

    @Test fun nothingIsOpenOnceEveryStepIsDone() {
        assertEquals(
            listOf(Done, Done, Done),
            setupStepStates(listOf(true, true, true)),
        )
    }
}
