package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for the real org.json; the rules themselves are pure. */
@RunWith(RobolectricTestRunner::class)
class TeachRulesTest {
    @Test fun parsesEveryBridgeAnswer() {
        val ok = TeachRules.parseBack("""{"status":"ok","changes":[{"ns":"secure","key":"aod","changed":"1","back":"0"}]}""")
        assertEquals(TeachOutcome.Learned(listOf(TaughtKey("secure", "aod", "1", "0"))), ok)
        assertEquals(TeachOutcome.NoChange, TeachRules.parseBack("""{"status":"none","changes":[]}"""))
        assertEquals(TeachOutcome.TooMany, TeachRules.parseBack("""{"status":"too-many","changes":[]}"""))
        assertEquals(TeachOutcome.Blocked, TeachRules.parseBack("""{"status":"blocked","changes":[]}"""))
        assertEquals(TeachOutcome.Lost, TeachRules.parseBack("expired"))
        assertEquals(TeachOutcome.Lost, TeachRules.parseBack(null))
    }

    @Test fun aOneZeroKeyGoesFirstAndSaysWhichSideIsOn() {
        // The user turned it OFF first: the 0 the change produced is still Off.
        val keys = listOf(TaughtKey("system", "mode_name", "dark", "light"), TaughtKey("secure", "aod", "0", "1"))
        assertTrue(TeachRules.knowsOn(keys))
        assertEquals("secure/aod=1|0;system/mode_name=light|dark", TeachRules.writes(keys))
    }

    @Test fun withoutAOneZeroKeyTheChangedSideIsOnUntilSwapped() {
        val keys = listOf(TaughtKey("system", "mode_name", "dark", "light"))
        assertFalse(TeachRules.knowsOn(keys))
        assertEquals("system/mode_name=dark|light", TeachRules.writes(keys))
        assertEquals("system/mode_name=light|dark", TeachRules.writes(keys, swapped = true))
    }

    @Test fun commandsAreTheLiteralSettingsPuts() {
        assertEquals(listOf("settings put secure aod 1"), TeachRules.commands("secure/aod=1|0", on = true))
        assertEquals(listOf("settings put secure aod 0"), TeachRules.commands("secure/aod=1|0", on = false))
        assertNull("a shared copy has no writes", TeachRules.commands(null, on = true))
    }
}
