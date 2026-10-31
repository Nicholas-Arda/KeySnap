package com.example.ui.scripts

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsSelected
import android.graphics.BitmapFactory
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptTrigger
import com.example.data.TriggerPressType
import com.example.data.PhysicalKeyOption
import com.example.bridge.KeyEventMapping
import com.github.takahirom.roborazzi.captureRoboImage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.ScriptDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier

/**
 * Leaving the editor throws the draft away, so a touched draft has to ask first. An untouched one
 * must still close in one Back — the dialog is a guard, not a toll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class ScriptEditorCloseTest {

    @get:Rule val rule = createComposeRule()

    private var closes = 0
    private var message by mutableStateOf<String?>(null)
    private var consumedMessages = 0
    private var theme by mutableStateOf(ThemeMode.DARK)
    private var choosingKey by mutableStateOf(false)
    private var recordingKey by mutableStateOf(false)
    private var heldKeys by mutableStateOf(emptyList<Int>())
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

    private fun showEditor(fontScale: Float = 1f): () -> Unit {
        var back: (() -> Unit)? = null
        rule.setContent {
            val owner = LocalOnBackPressedDispatcherOwner.current!!
            back = { owner.onBackPressedDispatcher.onBackPressed() }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            MyApplicationTheme(themeMode = theme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ScriptEditorScreen(
                    draft = draft,
                    message = message,
                    onConsumeMessage = { message = null; consumedMessages++ },
                    onSetName = { draft = draft.copy(name = it) },
                    onAddAction = { _, _ -> },
                    onConfigureNewAction = {},
                    onConfigureAction = {},
                    onRemoveAction = {},
                    onMoveAction = { _, _ -> },
                    onRemoveConstraint = {},
                    onOpenTriggerPicker = { choosingKey = true },
                    keyPickerOpen = choosingKey,
                    isRecordingKey = recordingKey,
                    recordingKeys = heldKeys,
                    savedKeys = listOf(PhysicalKeyOption(25, "Volume Down", "Down"), PhysicalKeyOption(26, "Power", "Power")),
                    onDismissKeyPicker = { choosingKey = false; recordingKey = false; heldKeys = emptyList() },
                    onSelectKey = {
                        draft = draft.copy(trigger = ScriptTrigger(listOf(it), if (it == KeyEventMapping.ASSISTANT_GESTURE) TriggerPressType.SINGLE_PRESS else draft.pressType))
                        choosingKey = false
                    },
                    onStartRecording = { recordingKey = true },
                    onCancelRecording = { recordingKey = false; heldKeys = emptyList() },
                    onSetPressType = { type -> draft = draft.copy(pressType = type, trigger = draft.trigger?.copy(pressType = type)) },
                    onSwitchToSurface = { draft = draft.copy(surfaceOnly = true, trigger = null); choosingKey = false },
                    onToggleAutomation = { draft = draft.copy(automation = !draft.automation, trigger = null, surfaceOnly = false) },
                    onPickConstraint = {},
                    onOpenScriptSettings = {},
                    onRunOnce = { message = "Test completed" },
                    onDeleteScript = {},
                    onDone = {},
                    onClose = { closes++ },
                )
                }
            }
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

    @Test fun inlineNameWaitsForApplyAndCancelRestoresDraft() {
        showEditor()
        rule.onRoot().captureRoboImage("build/outputs/shortcut-management/editor-title.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        rule.onNodeWithTag("editor_rename_title").performClick()
        rule.onNodeWithTag("editor_inline_name").performTextReplacement("Flashlight")
        rule.onRoot().captureRoboImage("build/outputs/shortcut-management/editor-inline.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        assertEquals("Torch", draft.name)
        rule.onNodeWithContentDescription("Cancel rename").performClick()
        assertEquals("Torch", draft.name)
        rule.onNodeWithTag("editor_rename_title").performClick()
        rule.onNodeWithTag("editor_inline_name").performTextReplacement("Flashlight")
        rule.onNodeWithTag("editor_apply_name").performClick()
        assertEquals("Flashlight", draft.name)
        assertEquals(0, closes)
    }

    @Test fun inlineNameKeyboardDoneAppliesAndBlankStaysInvalid() {
        showEditor()
        rule.onNodeWithTag("editor_rename_title").performClick()
        rule.onNodeWithTag("editor_inline_name").performTextReplacement("   ")
        rule.onNodeWithTag("editor_inline_name").performImeAction()
        rule.onNodeWithText("Enter a name").assertIsDisplayed()
        assertEquals("Torch", draft.name)
        rule.onNodeWithTag("editor_inline_name").performTextReplacement("  Camera  ")
        rule.onNodeWithTag("editor_inline_name").performImeAction()
        assertEquals("Camera", draft.name)
    }

    @Test fun backCancelsInlineNameBeforeClosingEditor() {
        val back = showEditor()
        rule.onNodeWithTag("editor_rename_title").performClick()
        rule.onNodeWithTag("editor_inline_name").performTextReplacement("Unsaved name")
        rule.runOnIdle { back() }
        assertEquals("Torch", draft.name)
        assertEquals(0, closes)
        rule.onNodeWithTag("editor_inline_name").assertDoesNotExist()
    }

    @Test fun testFeedbackUsesToastAndAllowsAnotherTestImmediately() {
        draft = draft.copy(actions = listOf(ScriptAction(type = "toggle_flashlight")))
        showEditor()

        repeat(2) { index ->
            rule.onNodeWithTag("run_once_button").assertIsDisplayed().performClick()
            rule.waitForIdle()
            assertEquals("Test completed", ShadowToast.getTextOfLatestToast())
            assertEquals(index + 1, consumedMessages)
            rule.onNodeWithText("Test completed").assertDoesNotExist()
            rule.onNodeWithTag("run_once_button").assertIsDisplayed()
        }
    }

    @Test fun sentenceChainShowsConditionAndVisibleAutomationSwitch() {
        draft = draft.copy(
            trigger = ScriptTrigger(listOf(25), TriggerPressType.SINGLE_PRESS),
            actions = listOf(ScriptAction.ToggleFlashlight),
            constraints = listOf(ScriptConstraint("device_unlocked")),
            isNew = true,
        )
        showEditor()
        rule.onNodeWithText("Only if").assertIsDisplayed()
        rule.onNodeWithTag("trigger_kind_automation").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/outputs/editor-a/keypress-dark.png")
        rule.runOnIdle { theme = ThemeMode.LIGHT }
        rule.onRoot().captureRoboImage("build/outputs/editor-a/keypress-light.png")
        rule.runOnIdle { theme = ThemeMode.SEPIA }
        rule.onRoot().captureRoboImage("build/outputs/editor-a/keypress-sepia.png")
        rule.runOnIdle { theme = ThemeMode.DARK }
        rule.onNodeWithTag("trigger_kind_automation").performClick()
        rule.onNodeWithText("If", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("When").assertDoesNotExist()
        assertEquals(listOf(ScriptConstraint("device_unlocked")), draft.constraints)
        assertEquals(listOf(ScriptAction.ToggleFlashlight), draft.actions)
        rule.onRoot().captureRoboImage("build/outputs/editor-a/automation-dark.png")
        rule.onNodeWithTag("editor_fine_tune").performScrollTo().performClick()
        rule.onNodeWithTag("script_settings_button").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("run_once_button").assertIsDisplayed()
        rule.onNodeWithTag("script_done_button").assertIsDisplayed()
    }

    @Test fun emptyAutomationCannotBeSaved() {
        draft = draft.copy(automation = true, actions = listOf(ScriptAction.ToggleFlashlight))
        showEditor()
        rule.onNodeWithTag("script_done_button").assertIsEnabled().performClick()
        rule.onNodeWithText("Add a condition first").assertIsDisplayed()
        rule.onNodeWithTag("add_constraint_button").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/outputs/editor-a/automation-empty-dark.png")
    }

    @Test fun typeIndicatorMovesThroughIntermediateFramesAndReverses() {
        draft = draft.copy(automation = false, actions = listOf(ScriptAction.ToggleFlashlight))
        showEditor()
        rule.mainClock.autoAdvance = false
        fun frame(name: String): android.graphics.Bitmap {
            rule.waitForIdle()
            val path = "build/outputs/editor-motion/selector-$name.png"
            rule.onNodeWithTag("shortcut_type_selector").captureRoboImage(
                path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            )
            return BitmapFactory.decodeFile(path)
        }
        val before = frame("before")
        rule.onNodeWithTag("trigger_kind_automation").performClick()
        rule.mainClock.advanceTimeBy(80)
        val middle = frame("middle")
        rule.mainClock.advanceTimeBy(1500)
        val after = frame("after")
        assertFalse("selection must move from its starting frame", before.sameAs(middle))
        assertFalse("80 ms must be an intermediate frame, not an instant jump", middle.sameAs(after))
        rule.onNodeWithTag("trigger_kind_key").performClick()
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithTag("trigger_kind_automation").performClick()
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithTag("trigger_kind_automation").assertIsSelected()
        rule.onNodeWithText("When").assertDoesNotExist()
        rule.onNodeWithText("If", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun inlineRecordingShowsHeldComboAndBackStopsItWithoutClosingTheEditor() {
        val back = showEditor()
        rule.onNodeWithTag("assign_key_card").performClick()
        rule.onNodeWithTag("inline_key_picker").assertIsDisplayed()
        rule.onNodeWithTag("save_trigger_button").assertDoesNotExist()
        rule.onRoot().captureRoboImage("build/outputs/editor-inline-key/idle-dark.png")
        rule.onNodeWithTag("record_key_button").performClick()
        rule.runOnIdle { heldKeys = listOf(25, 26) }
        rule.onNodeWithText("Volume Down + Power").assertIsDisplayed()
        rule.onNodeWithTag("trigger_option_25").assertIsNotEnabled()
        rule.onNodeWithTag("trigger_press_menu").assertIsNotEnabled()
        rule.onRoot().captureRoboImage("build/outputs/editor-inline-key/listening-dark.png")
        rule.runOnIdle { back() }
        rule.onNodeWithTag("inline_key_picker").assertDoesNotExist()
        rule.onNodeWithTag("run_once_button").assertIsDisplayed()
        assertFalse(recordingKey)
        assertEquals(0, closes)
    }

    @Test fun savedKeyClosesTheDockAndPressTypeChangesInTheSentence() {
        showEditor()
        rule.onNodeWithTag("assign_key_card").performClick()
        rule.onNodeWithTag("trigger_option_25").performClick()
        rule.onNodeWithTag("inline_key_picker").assertDoesNotExist()
        assertEquals(listOf(25), draft.trigger?.keyCodes)
        rule.onNodeWithTag("trigger_press_menu").performClick()
        rule.onNodeWithTag("press_type_LONG_PRESS").performClick()
        assertEquals(TriggerPressType.LONG_PRESS, draft.trigger?.pressType)
        for ((name, mode) in listOf("light" to ThemeMode.LIGHT, "dark" to ThemeMode.DARK, "sepia" to ThemeMode.SEPIA)) {
            rule.runOnIdle { theme = mode }
            rule.onRoot().captureRoboImage("build/outputs/editor-inline-key/recorded-$name.png")
        }
    }

    @Test fun inlineAssistantSelectionLocksPressTypeAndKeepsSetupAndWarningVisible() {
        draft = draft.copy(pressType = TriggerPressType.LONG_PRESS)
        showEditor()
        rule.onNodeWithTag("assign_key_card").performClick()
        rule.onNodeWithTag("assistant_trigger_option").performClick()
        assertEquals(TriggerPressType.SINGLE_PRESS, draft.trigger?.pressType)
        rule.onNodeWithTag("inline_key_picker").assertDoesNotExist()
        rule.onNodeWithTag("trigger_press_menu").assertIsNotEnabled()
        rule.onNodeWithText("Make KeySnap your digital assistant").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/outputs/editor-inline-key/assistant-dark.png")
    }

    @Test fun sharedKeyPickerCanOmitSurfaceWithoutLosingAssistantOrSavedKeys() {
        var selected: Int? = null
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                InlineKeyPicker(
                    savedKeys = listOf(PhysicalKeyOption(25, "Volume down", "Vol−")), isRecording = false,
                    onDismiss = {}, onSelectKey = { selected = it },
                    onStartRecording = {}, onCancelRecording = {}, onSwitchToSurface = null,
                )
            }
        }
        rule.onNodeWithTag("trigger_picker_use_surface").assertDoesNotExist()
        rule.onNodeWithTag("trigger_option_25").performClick()
        assertEquals(25, selected)
        rule.onNodeWithTag("assistant_trigger_option").performClick()
        assertEquals(KeyEventMapping.ASSISTANT_GESTURE, selected)
    }

    @Test
    @Config(qualifiers = "w290dp-h600dp-mdpi")
    fun inlineDockCanScrollAtLargeFontAndDoneIsReachable() {
        showEditor(fontScale = 2f)
        rule.onNodeWithTag("assign_key_card").performScrollTo().performClick()
        rule.onNodeWithTag("inline_key_done").assertIsDisplayed()
        rule.onNodeWithTag("record_key_button").performScrollTo().assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/outputs/editor-inline-key/large-text-dock.png")
        rule.onNodeWithTag("trigger_picker_use_surface").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("inline_key_done").assertIsDisplayed().performClick()
        rule.onNodeWithTag("inline_key_picker").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w290dp-h600dp-mdpi")
    fun largeTextKeepsFooterAndMultipleActionsReachable() {
        draft = draft.copy(
            automation = true,
            actions = listOf(ScriptAction.ToggleFlashlight, ScriptAction("launch_app", mapOf("packageName" to "com.example.a.very.long.package.name"))),
            constraints = listOf(ScriptConstraint("device_unlocked"), ScriptConstraint("screen_off")),
        )
        showEditor(fontScale = 2f)
        rule.onNodeWithTag("run_once_button").assertIsDisplayed()
        rule.onNodeWithTag("script_done_button").assertIsDisplayed()
        rule.onNodeWithTag("editor_chain").performScrollToNode(hasTestTag("action_menu_1"))
        rule.onRoot().captureRoboImage("build/outputs/editor-a/large-text-actions.png")
        rule.onNodeWithTag("action_menu_1").performScrollTo().performClick()
        rule.onNodeWithTag("remove_action_1").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/outputs/editor-a/large-text-action-menu.png")
    }
    @Test
    @Config(qualifiers = "w327dp-h780dp-mdpi")
    fun veryLargeTextKeepsFooterLabelsOnOneLine() {
        showEditor(fontScale = 3.2f)
        listOf("Test", "Save").forEach { label ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                    it(layouts)
                }
            assertEquals("$label stays readable on one line", 1, layouts.single().lineCount)
            val layout = layouts.single()
            org.junit.Assert.assertTrue("$label fits its text bounds",
                layout.getLineRight(0) - layout.getLineLeft(0) <= layout.size.width + 1f)
            org.junit.Assert.assertTrue("$label fits its text height",
                layout.getLineBottom(0) - layout.getLineTop(0) <= layout.size.height + 1f)
        }
    }

    @Test
    @Config(qualifiers = "w780dp-h327dp-mdpi")
    fun veryLargeTextInLandscapeLeavesTheChainScrollable() {
        showEditor(fontScale = 3.2f)
        val chain = rule.onNodeWithTag("editor_chain")
        org.junit.Assert.assertTrue("wide screens keep space for the chain",
            chain.fetchSemanticsNode().boundsInRoot.height > 80f)
        chain.performScrollToNode(hasTestTag("editor_fine_tune"))
        rule.onNodeWithTag("editor_fine_tune").assertIsDisplayed()
        rule.onNodeWithTag("run_once_button").assertIsDisplayed()
        rule.onNodeWithTag("script_done_button").assertIsDisplayed()
    }
}
