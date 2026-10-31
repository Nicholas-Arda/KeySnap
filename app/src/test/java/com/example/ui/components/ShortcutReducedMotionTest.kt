package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.scripts.ShortcutEditorTransition
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.ScriptDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ShortcutReducedMotionTest {
    @get:Rule val rule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun disablingSystemAnimationsSkipsBothTheFabHintAndEditorTravel() {
        var draft by mutableStateOf<ScriptDraft?>(null)
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                ShortcutEditorTransition(draft, fabLaunchId = "zero", fabOrigin = Offset(195f, 760f)) { target ->
                    if (target == null) Box(Modifier.fillMaxSize()) {
                        ShortcutFab(
                            onClick = { draft = ScriptDraft(id = "zero", name = "", enabled = true,
                                trigger = null, actions = emptyList(), isNew = true) },
                            onCenterChanged = {},
                            modifier = Modifier.align(Alignment.BottomCenter).size(58.dp),
                        )
                    } else Box(Modifier.fillMaxSize().testTag("editor"))
                }
            }
        }
        rule.waitForIdle()
        val before = rule.mainClock.currentTime
        rule.onNodeWithTag("new_script_button").performClick()
        rule.onNodeWithTag("editor").assertIsDisplayed()
        assertTrue("no fixed delay when system animation scale is zero", rule.mainClock.currentTime - before <= 96)
        val bounds = rule.onNodeWithTag("editor").fetchSemanticsNode().boundsInRoot
        assertEquals(390f, bounds.width, .1f)
        assertEquals(844f, bounds.height, .1f)
    }
}
