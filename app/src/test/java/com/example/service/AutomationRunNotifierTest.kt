package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.RunNotice
import com.example.data.ScriptAction
import com.example.data.ShortcutScript
import com.example.data.toDomain
import com.example.data.toPersisted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** An automation's notice words: blank follows the name, typed words stay, and both survive a save. */
@RunWith(RobolectricTestRunner::class)
class AutomationRunNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun blankWordsFollowTheName() {
        assertEquals("Home Wi-Fi ran" to "3 actions done", AutomationRunNotifier.runWords(context, "Home Wi-Fi", RunNotice(), 3))
        assertEquals("1 action done", AutomationRunNotifier.runWords(context, "X", RunNotice(), 1).second)
        assertEquals("Undo · Home Wi-Fi" to "Settings put back", AutomationRunNotifier.undoWords(context, "Home Wi-Fi", RunNotice()))
    }

    @Test fun typedWordsStay() {
        val notice = RunNotice(onRun = true, runTitle = "Welcome home", runText = "Lights on")
        assertEquals("Welcome home" to "Lights on", AutomationRunNotifier.runWords(context, "Home Wi-Fi", notice, 3))
    }

    @Test fun settingsDefaultFillsBlankWordsButNeverTypedOnes() {
        NotificationPrefs.setText(context, NotificationPrefs.RUN_TITLE, "{name} is on")
        NotificationPrefs.setText(context, NotificationPrefs.UNDO_TEXT, "All back")
        assertEquals("Morning is on" to "3 actions done", AutomationRunNotifier.runWords(context, "Morning", RunNotice(), 3))
        assertEquals("Undo · Morning" to "All back", AutomationRunNotifier.undoWords(context, "Morning", RunNotice()))
        assertEquals("Mine", AutomationRunNotifier.runWords(context, "Morning", RunNotice(runTitle = "Mine"), 3).first)
    }

    @Test fun noticeSurvivesASaveAndIsLeftOutWhenOff() {
        val script = ShortcutScript("a", "Home", triggers = emptyList(), actions = listOf(ScriptAction("vibrate")), automation = true)
        assertNull(script.toPersisted().notice)
        val on = script.copy(notice = RunNotice(onRun = true, onUndo = true, undoText = "Back"))
        assertEquals(on.notice, on.toPersisted().toDomain()!!.notice)
    }
}
