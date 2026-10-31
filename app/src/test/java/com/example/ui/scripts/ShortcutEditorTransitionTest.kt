package com.example.ui.scripts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.ScriptDraft
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.snapshots.Snapshot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutEditorTransitionTest {
    @get:Rule val rule = createComposeRule()
    private var draft by mutableStateOf<ScriptDraft?>(null)
    private var launchId by mutableStateOf<String?>(null)
    private var launchOrigin by mutableStateOf<Offset?>(null)
    private fun draft(id: String, name: String) = ScriptDraft(
        id = id, name = name, enabled = true, trigger = null, actions = emptyList(), isNew = true,
    )

    private fun showHost() {
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                ShortcutEditorTransition(draft, fabLaunchId = launchId, fabOrigin = launchOrigin) { target ->
                    if (target == null) Box(Modifier.fillMaxSize().testTag("home"))
                    else Box(Modifier.fillMaxSize().testTag("editor")) { Text(target.name) }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun changeDraft(value: ScriptDraft?) {
        rule.runOnIdle {
            draft = value
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    private fun advance(millis: Long) {
        rule.mainClock.advanceTimeBy(millis)
        rule.waitForIdle()
    }

    @Test fun openAndCloseRetainOutgoingContentUntilTheAnimationEnds() {
        showHost()
        changeDraft(draft("one", "One"))
        advance(32)
        rule.onNodeWithTag("home").assertExists()
        rule.onNodeWithTag("editor").assertExists()
        advance(500)
        rule.onNodeWithTag("home").assertDoesNotExist()
        rule.onNodeWithTag("editor").assertIsDisplayed()

        changeDraft(draft?.copy(name = "Renamed"))
        rule.onNodeWithText("One").assertDoesNotExist()
        rule.onNodeWithText("Renamed").assertIsDisplayed()

        changeDraft(null)
        advance(32)
        rule.onNodeWithTag("editor").assertExists()
        advance(500)
        rule.onNodeWithTag("editor").assertDoesNotExist()
        rule.onNodeWithTag("home").assertIsDisplayed()
    }

    @Test fun reopeningDuringExitShowsTheNewDraft() {
        showHost()
        changeDraft(draft("one", "One"))
        advance(500)
        changeDraft(null)
        advance(64)
        changeDraft(draft("two", "Two"))
        advance(1500)
        rule.onNodeWithText("Two").assertIsDisplayed()
        rule.onNodeWithText("One").assertDoesNotExist()
        rule.onNodeWithTag("home").assertDoesNotExist()
    }

    @Test fun layeredHomeKeepsTheSettingsTouchTargetSmallAndThePageClickable() {
        var pageClicks = 0
        var settingsClicks = 0
        rule.setContent {
            ShortcutEditorTransition(null) {
                Box(Modifier.fillMaxSize().clickable { pageClicks++ }.testTag("page"))
                Box(Modifier.align(Alignment.TopEnd).size(48.dp)
                    .clickable { settingsClicks++ }.testTag("settings"))
            }
        }
        val page = rule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val gear = rule.onNodeWithTag("settings").fetchSemanticsNode().boundsInRoot
        assertEquals(48f, gear.width, .1f)
        assertEquals(48f, gear.height, .1f)
        assertEquals(page.right, gear.right, .1f)
        assertEquals(page.top, gear.top, .1f)
        rule.onNodeWithTag("page").performTouchInput { click(center) }
        assertEquals(1, pageClicks)
        assertEquals(0, settingsClicks)
        rule.onNodeWithTag("settings").performClick()
        assertEquals(1, settingsClicks)
        assertEquals(1, pageClicks)
    }

    @Test fun fabEditorStartsAtTheButtonAndReturnsThereBeforeDisposal() {
        launchId = "one"
        launchOrigin = Offset(195f, 760f)
        showHost()
        changeDraft(draft("one", "One"))
        advance(80)
        val early = rule.onNodeWithTag("editor").fetchSemanticsNode().boundsInRoot
        assertTrue("begins near the actual + origin: $early", early.center.y > 600f)
        assertTrue("starts small rather than at full-screen size: $early", early.width < 160f)
        changeDraft(draft?.copy(name = "Renamed"))
        advance(650)
        val full = rule.onNodeWithTag("editor").fetchSemanticsNode().boundsInRoot
        assertEquals(390f, full.width, .1f)
        assertEquals(844f, full.height, .1f)
        rule.onNodeWithText("Renamed").assertIsDisplayed()
        changeDraft(null)
        advance(260)
        val returning = rule.onNodeWithTag("editor").fetchSemanticsNode().boundsInRoot
        assertTrue("the editor shrinks on its return: $returning", returning.width < full.width)
        assertTrue("return is anchored at the +: $returning", returning.center.y > full.center.y)
        advance(600)
        rule.onNodeWithTag("editor").assertDoesNotExist()
        rule.onNodeWithTag("home").assertIsDisplayed()
    }

    @Test fun anotherFabLaunchDoesNotResetTheOutgoingDraftsGeometry() {
        launchId = "one"
        launchOrigin = Offset(195f, 760f)
        showHost()
        changeDraft(draft("one", "One"))
        advance(700)
        changeDraft(null)
        advance(260)
        fun oldBounds() = rule.onNode(hasTestTag("editor") and hasAnyDescendant(hasText("One")))
            .fetchSemanticsNode().boundsInRoot
        val before = oldBounds()
        rule.runOnIdle { launchId = "two"; launchOrigin = Offset(195f, 720f) }
        changeDraft(draft("two", "Two"))
        advance(32)
        val old = rule.onAllNodes(hasTestTag("editor") and hasAnyDescendant(hasText("One"))).fetchSemanticsNodes()
        assertTrue("old editor must retire or keep shrinking, not jump full screen",
            old.isEmpty() || old.single().boundsInRoot.width <= before.width + 1f)
        advance(1200)
        rule.onNodeWithText("Two").assertIsDisplayed()
        rule.onNodeWithText("One").assertDoesNotExist()
        rule.onNodeWithTag("home").assertDoesNotExist()
    }
}
