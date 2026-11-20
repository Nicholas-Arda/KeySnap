package com.example.service

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutScript
import com.example.data.toDomain
import com.example.data.toPersisted
import com.example.service.AutomationUndo.Kind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Undo restores the value from before the run, once, and never over a change the user made since. */
@RunWith(RobolectricTestRunner::class)
class AutomationUndoTest {
    private lateinit var context: Context
    private lateinit var audio: AudioManager

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audio = context.getSystemService(AudioManager::class.java)
        AutomationUndo.forget(context, ID)
    }

    @Test fun onlyAnIfThatLastsCanBeUndone() {
        val charging = ScriptConstraint("charging")
        assertTrue(AutomationUndo.offered(true, listOf(charging)))
        assertFalse(AutomationUndo.offered(false, listOf(charging)))
        assertFalse(AutomationUndo.offered(true, emptyList()))
        assertFalse(AutomationUndo.offered(true, listOf(charging, ScriptConstraint(ScriptConstraintCatalog.AT_TIME))))
        assertFalse(AutomationUndo.offered(true, listOf(ScriptConstraint(ScriptConstraintCatalog.NOTIFICATION_RECEIVED))))
    }

    @Test fun kindsAreTheFlaggedUndoableActionsOnce() {
        val actions = listOf(
            ScriptAction("enable_dnd", undoOnEnd = true),
            ScriptAction("disable_dnd", undoOnEnd = true),
            ScriptAction("change_ringer_mode", undoOnEnd = false),
            ScriptAction("launch_app", undoOnEnd = true),
        )
        assertEquals(listOf(Kind.DND.name), AutomationUndo.kinds(actions))
        assertFalse(AutomationUndo.canUndo(ScriptAction("launch_app")))
    }

    @Test fun aLearnedSwitchIsUndoneByItsOwnWrites() {
        val writes = "secure/aod=1|0"
        val action = ScriptAction("learned_setting", mapOf("writes" to writes, "state" to "on"), undoOnEnd = true)
        assertEquals(listOf("learned:$writes"), AutomationUndo.kinds(listOf(action)))
        assertEquals(
            listOf(ScriptAction("learned_setting", mapOf("writes" to writes, "state" to "off"))),
            AutomationUndo.restore(context, "learned:$writes", "false"),
        )
        AutomationUndo.remember(context, ID, mapOf("learned:$writes" to "false"), mapOf("learned:$writes" to "true"))
        assertEquals(mapOf("learned:$writes" to ("false" to "true")), AutomationUndo.load(context, ID))
        assertFalse("an import without writes can't be undone", AutomationUndo.canUndo(ScriptAction("learned_setting", mapOf("state" to "on"))))
    }

    @Test fun theRingerComesBackWhenTheIfEnds() = runBlocking {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        AutomationUndo.remember(context, ID, mapOf(Kind.RINGER.name to "2"), mapOf(Kind.RINGER.name to "0"))
        assertTrue(AutomationUndo.pending(context, ID))

        val restore = AutomationUndo.takeRestore(context, ID, listOf(Kind.RINGER.name))

        assertEquals(listOf(ScriptAction("change_ringer_mode", mapOf("mode" to "normal"))), restore)
        assertFalse("the entry is used once", AutomationUndo.pending(context, ID))
    }

    @Test fun aSettingTheUserChangedSinceIsLeftAlone() = runBlocking {
        AutomationUndo.remember(context, ID, mapOf(Kind.RINGER.name to "2"), mapOf(Kind.RINGER.name to "0"))
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE

        assertEquals(emptyList<ScriptAction>(), AutomationUndo.takeRestore(context, ID, listOf(Kind.RINGER.name)))
        assertFalse(AutomationUndo.pending(context, ID))
    }

    @Test fun anUnflaggedKindIsNotUndone() = runBlocking {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        AutomationUndo.remember(context, ID, mapOf(Kind.RINGER.name to "2"), mapOf(Kind.RINGER.name to "0"))

        assertEquals(emptyList<ScriptAction>(), AutomationUndo.takeRestore(context, ID, emptyList()))
    }

    @Test fun aRunThatChangedNothingLeavesNothingPending() {
        AutomationUndo.remember(context, ID, mapOf(Kind.RINGER.name to "2"), mapOf(Kind.RINGER.name to "2"))
        assertFalse(AutomationUndo.pending(context, ID))
    }

    @Test fun aSecondRunKeepsTheOriginalBefore() {
        AutomationUndo.remember(context, ID, mapOf(Kind.RINGER.name to "2"), mapOf(Kind.RINGER.name to "1"))
        AutomationUndo.remember(context, ID, mapOf(Kind.RINGER.name to "1"), mapOf(Kind.RINGER.name to "0"))
        assertEquals(mapOf(Kind.RINGER.name to ("2" to "0")), AutomationUndo.load(context, ID))
    }

    @Test fun theFlagSurvivesSaving() {
        val script = ShortcutScript(
            id = ID, name = "Undo", triggers = emptyList(), automation = true,
            actions = listOf(ScriptAction("enable_dnd", undoOnEnd = true)),
            constraints = listOf(ScriptConstraint("charging")),
        )
        assertEquals(script.actions, script.toPersisted().toDomain()?.actions)
    }

    private companion object {
        const val ID = "undo-test"
    }
}
