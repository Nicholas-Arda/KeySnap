package com.example.ui.scripts

import com.example.bridge.ChordTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunKeyLabelTest {
    private val name: (Int) -> String = { if (it == 24) "Vol+" else if (it == 25) "Vol−" else "K$it" }

    @Test
    fun realKeyUsesItsName() = assertEquals("Vol+", runKeyLabel(24, emptyList(), name))

    @Test
    fun chordIdShowsTheChordKeys() =
        assertEquals("Vol++Vol−", runKeyLabel(ChordTracker.chordId(listOf(24, 25)), listOf(24, 25), name))

    @Test
    fun chordIdOfAnOldChordFallsBack() = assertNull(runKeyLabel(ChordTracker.chordId(listOf(24, 26)), listOf(24, 25), name))
}
