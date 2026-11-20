package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Any" survives saving and sharing, and the cooldown never counts as the one condition that holds. */
class MatchAnyTest {
    private val script = ShortcutScript(
        id = "any", name = "Any", triggers = emptyList(), automation = true, matchAny = true,
        actions = listOf(ScriptAction("enable_dnd")),
        constraints = listOf(ScriptConstraint("charging"), ScriptConstraint("wifi_on"), ScriptConstraint(ScriptConstraintCatalog.COOLDOWN)),
    )

    @Test fun anyNeedsOneRealCondition() {
        assertTrue(script.constraintsMatch({ it.type == "charging" || it.type == ScriptConstraintCatalog.COOLDOWN }))
        assertFalse(script.constraintsMatch({ it.type == ScriptConstraintCatalog.COOLDOWN }))
        assertFalse(script.copy(matchAny = false).constraintsMatch({ it.type == "charging" || it.type == ScriptConstraintCatalog.COOLDOWN }))
    }

    @Test fun anIfThatCannotBeReadNeverFiresAnyOnItsOwn() {
        // "Unknown never blocks" reads true; under Any that alone would fire the shortcut.
        val unknown: (ScriptConstraint) -> Boolean = { it.type == "wifi_on" || it.type == ScriptConstraintCatalog.COOLDOWN }
        val known: (ScriptConstraint) -> Boolean = { it.type == ScriptConstraintCatalog.COOLDOWN }
        assertFalse(script.constraintsMatch(unknown, known))
        assertTrue(script.copy(matchAny = false).constraintsMatch({ true }, known))
    }

    @Test fun anySurvivesSavingAndSharing() {
        assertTrue(script.toPersisted().toDomain()!!.matchAny)
        val encoded = ShortcutShare.encode("Any", true, TriggerPressType.SINGLE_PRESS, script.actions, script.constraints, matchAny = true)
        assertTrue((ShortcutShare.decode(encoded) as ImportResult.Ok).shortcut.matchAny)
        val old = ShortcutShare.encode("All", true, TriggerPressType.SINGLE_PRESS, script.actions, script.constraints)
        assertFalse((ShortcutShare.decode(old) as ImportResult.Ok).shortcut.matchAny)
    }
}
