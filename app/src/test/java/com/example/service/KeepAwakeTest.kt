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

    @Test fun offWithNothingRememberedFallsBackToThirtySeconds() {
        assertEquals(KeepAwake.Step(30_000, null), KeepAwake.step("off", never, null))
    }
}
