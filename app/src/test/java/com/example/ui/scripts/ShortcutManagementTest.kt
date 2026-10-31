package com.example.ui.scripts

import android.content.Intent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.example.data.ScriptAction
import com.example.data.ShortcutScript
import com.example.data.mergeShortcutOrder
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class ShortcutManagementTest {
    @get:Rule val rule = createComposeRule()
    private var scripts by mutableStateOf(listOf(shortcut("a"), shortcut("b"), shortcut("c")))
    private var opened: String? = null
    private var upsells = 0
    private var commits = 0

    private fun shortcut(id: String) = ShortcutScript(id, "Shortcut $id", triggers = emptyList(), actions = listOf(ScriptAction.ToggleFlashlight))

    private fun show(): () -> Unit {
        var back: (() -> Unit)? = null
        rule.setContent {
            val owner = LocalOnBackPressedDispatcherOwner.current!!
            back = { owner.onBackPressedDispatcher.onBackPressed() }
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                ScriptsScreen(
                    scripts, true, ThemeMode.LIGHT,
                    onToggleScriptEnabled = { id, enabled -> scripts = scripts.map { if (it.id == id) it.copy(enabled = enabled) else it } },
                    onOpenScript = { opened = it },
                    onGoHome = {}, onImportText = {}, onImportUri = {},
                    onReorderScripts = { scripts = mergeShortcutOrder(scripts, it); commits++ },
                    onRenameScript = { id, name -> scripts = scripts.map { if (it.id == id) it.copy(name = name) else it } },
                    onDeleteScript = { id -> scripts = scripts.filterNot { it.id == id } },
                    onUpsell = { upsells++ },
                )
            }
        }
        rule.waitForIdle()
        return back!!
    }

    @Test fun normalTapAndSwitchStillWorkAndBackLeavesEditMode() {
        val back = show()
        rule.onNodeWithTag("script_card_a").performClick()
        assertEquals("a", opened)
        rule.onNodeWithTag("script_status_a").performClick()
        assertEquals(false, scripts.first().enabled)
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithText("Tap a shortcut to edit it").assertIsDisplayed()
        rule.onNodeWithTag("script_status_a").assertDoesNotExist()
        rule.onRoot().captureRoboImage("build/outputs/shortcut-management/edit-list.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        rule.runOnIdle { back() }
        rule.onNodeWithTag("script_status_a").assertIsDisplayed()
    }

    @Test fun longPressOnCardEntersEditModeWithoutOpeningOrToggling() {
        show()
        rule.onNodeWithTag("script_card_a").performTouchInput { longClick(Offset(30f, center.y)) }
        rule.waitForIdle()
        rule.onNodeWithText("Tap a shortcut to edit it").assertIsDisplayed()
        rule.onNodeWithTag("script_status_a").assertDoesNotExist()
        assertEquals(null, opened)
        assertEquals(true, scripts.first().enabled)
    }

    @Test fun longPressOnSwitchEntersEditModeWithoutToggling() {
        show()
        rule.onNodeWithTag("script_status_a").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Tap a shortcut to edit it").assertIsDisplayed()
        assertEquals(true, scripts.first().enabled)
        assertEquals(null, opened)
    }

    @Test fun quickRenameValidatesAndOnlyChangesName() {
        show()
        val original = scripts.first()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithTag("script_card_a").performClick()
        rule.onNodeWithTag("quick_shortcut_name").performTextReplacement("   ")
        rule.onNodeWithTag("quick_shortcut_save").performClick()
        rule.onNodeWithText("Enter a name").assertIsDisplayed()
        assertEquals(original, scripts.first())
        rule.onNodeWithTag("quick_shortcut_name").performTextReplacement("Flashlight")
        rule.onNodeWithTag("quick_shortcut_save").performClick()
        assertEquals(original.copy(name = "Flashlight"), scripts.first())
        assertEquals(null, opened)
        rule.onRoot().captureRoboImage("build/outputs/shortcut-management/settings-free.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    @Test fun doneButtonRestoresNormalCardActions() {
        show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithTag("script_status_a").assertIsDisplayed()
        rule.onNodeWithTag("script_card_a").performClick()
        assertEquals("a", opened)
    }

    @Test fun externallyDeletedShortcutDoesNotTrapEditModeBack() {
        val back = show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithTag("script_card_a").performClick()
        rule.runOnIdle { scripts = scripts.filterNot { it.id == "a" } }
        rule.onNodeWithTag("quick_shortcut_name").assertDoesNotExist()
        rule.runOnIdle { back() }
        rule.onNodeWithTag("script_status_b").assertIsDisplayed()
    }

    @Test fun quickRenameKeyboardDoneTrimsAndPersistsOnlyTheName() {
        show()
        val original = scripts.first()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithTag("script_card_a").performClick()
        rule.onNodeWithTag("quick_shortcut_name").performTextReplacement("  Flashlight  ")
        rule.onNodeWithTag("quick_shortcut_name").performImeAction()
        assertEquals(original.copy(name = "Flashlight"), scripts.first())
    }

    @Test fun simpleShareUsesExistingChooserAndDiscardsUnappliedName() {
        show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithTag("script_card_a").performClick()
        rule.onNodeWithTag("quick_shortcut_name").performTextReplacement("Not saved")
        rule.onNodeWithText("Share").performClick()
        val chooser = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("Shortcut a", scripts.first().name)
        rule.onNodeWithTag("quick_shortcut_name").assertIsDisplayed()
        rule.onNodeWithText("Share this shortcut?").assertDoesNotExist()
    }

    @Test fun freeModeRowUsesExistingUpsellAndDeleteRequiresConfirmation() {
        show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithTag("script_card_a").performClick()
        rule.onNodeWithText("Move to mode").performClick()
        assertEquals(1, upsells)
        rule.onNodeWithTag("script_card_a").performClick()
        rule.onNodeWithText("Delete shortcut").performClick()
        assertEquals(3, scripts.size)
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(3, scripts.size)
        rule.onNodeWithText("Delete shortcut").performClick()
        rule.onNodeWithText("Delete", substring = false).performClick()
        assertEquals(listOf("b", "c"), scripts.map { it.id })
    }

    @Test fun handleDragImmediatelyPersistsOrderWithoutOpeningSettings() {
        show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        val first = rule.onNodeWithTag("script_card_a").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("script_card_b").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("Drag to reorder Shortcut a", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, second.center.y - first.center.y + 12f), delayMillis = 32)
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("b", "a", "c"), scripts.map { it.id })
        assertEquals(1, commits)
        rule.onNodeWithTag("quick_shortcut_name").assertDoesNotExist()
    }

    @Test fun repeatedLongPressDragsUseTheLatestPersistedOrder() {
        show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        repeat(2) { attempt ->
            val first = rule.onNodeWithTag("script_card_a").fetchSemanticsNode().boundsInRoot
            val second = rule.onNodeWithTag("script_card_b").fetchSemanticsNode().boundsInRoot
            rule.onNodeWithTag("script_card_a").performTouchInput {
                down(Offset(30f, center.y))
                advanceEventTime(650)
                moveBy(Offset(0f, second.center.y - first.center.y + if (attempt == 0) 12f else -12f), delayMillis = 32)
                up()
            }
            rule.waitForIdle()
        }
        assertEquals(listOf("a", "b", "c"), scripts.map { it.id })
        assertEquals(2, commits)
    }

    @Test fun longPressCardDragPersistsOrder() {
        show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        val first = rule.onNodeWithTag("script_card_a").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("script_card_b").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("script_card_a").performTouchInput {
            down(Offset(30f, center.y))
            advanceEventTime(650)
            moveBy(Offset(0f, second.center.y - first.center.y + 12f), delayMillis = 32)
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("b", "a", "c"), scripts.map { it.id })
        assertEquals(1, commits)
        rule.onNodeWithTag("quick_shortcut_name").assertDoesNotExist()
    }

    @Test fun handleCanCrossMultipleCardsUpwardInOneMove() {
        show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        val first = rule.onNodeWithTag("script_card_a").fetchSemanticsNode().boundsInRoot
        val last = rule.onNodeWithTag("script_card_c").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("Drag to reorder Shortcut c", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, first.center.y - last.center.y - 12f), delayMillis = 32)
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("c", "a", "b"), scripts.map { it.id })
        assertEquals(1, commits)
    }

    @Test fun sensitiveShareRequiresReviewBeforeAndroidChooser() {
        scripts = listOf(shortcut("a").copy(actions = listOf(ScriptAction("input_text", mapOf("text" to "On my way.")))))
        show()
        rule.onNodeWithTag("edit_shortcuts").performClick()
        rule.onNodeWithTag("script_card_a").performClick()
        rule.onNodeWithText("Share").performClick()
        rule.onNodeWithText("Share this shortcut?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(1, scripts.size)
    }
}
