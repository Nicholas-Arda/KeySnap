package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepAwakeTest {
    private val never = KeepAwake.NEVER

    @Test fun toggleRemembersTheUsersTimeoutAndPutsItBack() {
        assertEquals(KeepAwake.Step(never, 1_800_000), KeepAwake.step(null, 1_800_000, null))
        assertEquals(KeepAwake.Step(1_800_000, null), KeepAwake.step("toggle", never, 1_800_000))
    }

    @Test fun onAndOffDoNothingWhenAlreadyThere() {
        assertNull(KeepAwake.step("on", never, 60_000))
        assertNull(KeepAwake.step("off", 60_000, null))
    }

    @Test fun onWhenTheUserAlreadyHasNeverRemembersItAndOffKeepsIt() {
        assertEquals(KeepAwake.Step(never, never), KeepAwake.step("on", never, null))
        assertEquals(KeepAwake.Step(never, null), KeepAwake.step("off", never, never))
        assertEquals(KeepAwake.Step(never, null), KeepAwake.step(null, never, never))
    }

    @Test fun toggleOnThenOffKeepsTheUsersNever() {
        val on = KeepAwake.step("toggle", never, null)!!
        assertEquals(KeepAwake.Step(never, never), on)
        assertEquals(KeepAwake.Step(never, null), KeepAwake.step("toggle", on.write, on.remember))
    }

    @Test fun toggleOnThenOffPutsBackANormalTimeout() {
        val on = KeepAwake.step(null, 60_000, null)!!
        assertEquals(KeepAwake.Step(never, 60_000), on)
        assertEquals(KeepAwake.Step(60_000, null), KeepAwake.step(null, on.write, on.remember))
    }

    @Test fun toggleAfterTheUserChangedTheTimeoutTurnsOnAgain() {
        assertEquals(KeepAwake.Step(never, 120_000), KeepAwake.step(null, 120_000, 60_000))
    }

    @Test fun offWithNothingRememberedFallsBackToThirtySeconds() {
        assertEquals(KeepAwake.Step(30_000, null), KeepAwake.step("off", never, null))
    }
}
