package com.example.ui.scripts

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.ScriptDraft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Leaving the editor throws the draft away, so a touched draft has to ask first. An untouched one
 * must still close in one Back — the dialog is a guard, not a toll.
 */
@RunWith(RobolectricTestRunner::class)
class ScriptEditorCloseTest {

    @get:Rule val rule = createComposeRule()

    private var closes = 0
    private var draft by mutableStateOf(
        ScriptDraft(
            id = "s1",
            name = "Torch",
            enabled = true,
            trigger = null,
            actions = emptyList(),
            isNew = false,
        ),
    )

    private fun showEditor(): () -> Unit {
        var back: (() -> Unit)? = null
        rule.setContent {
            val owner = LocalOnBackPressedDispatcherOwner.current!!
            back = { owner.onBackPressedDispatcher.onBackPressed() }
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                ScriptEditorScreen(
                    draft = draft,
                    message = null,
                    onConsumeMessage = {},
                    onSetName = { draft = draft.copy(name = it) },
                    onAddAction = { _, _ -> },
                    onConfigureNewAction = {},
                    onConfigureAction = {},
                    onRemoveAction = {},
                    onMoveAction = { _, _ -> },
                    onRemoveConstraint = {},
                    onOpenTriggerPicker = {},
                    onToggleAutomation = {},
                    onPickConstraint = {},
                    onOpenScriptSettings = {},
                    onRunOnce = {},
                    onDeleteScript = {},
                    onDone = {},
                    onClose = { closes++ },
                )
            }
        }
        rule.waitForIdle()
        return back!!
    }

    @Test fun editedDraftAsksBeforeDiscarding() {
        val back = showEditor()
        rule.runOnIdle { draft = draft.copy(name = "Torch on") }
        rule.runOnIdle { back() }
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertEquals("nothing closed until the user confirms", 0, closes)
    }

    @Test fun untouchedDraftClosesSilently() {
        val back = showEditor()
        rule.runOnIdle { back() }
        rule.waitForIdle()
        assertEquals("closed on the first Back", 1, closes)
        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }
}
