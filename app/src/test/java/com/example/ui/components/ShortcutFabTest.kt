package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutFabTest {
    @get:Rule val rule = createComposeRule()

    @Test fun visualBounceDoesNotMoveOrExpandTheTouchTargetAndDoubleTapLaunchesOnce() {
        var launches = 0
        var measuredCenter = Offset.Zero
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) {
                    ShortcutFab(
                        onClick = { launches++ }, onCenterChanged = { measuredCenter = it },
                        modifier = Modifier.align(Alignment.BottomCenter).size(58.dp),
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val initial = rule.onNodeWithTag("new_script_button").fetchSemanticsNode().boundsInRoot
        assertEquals(58f, initial.width, .1f)
        assertEquals(58f, initial.height, .1f)
        assertEquals(initial.center, measuredCenter)
        rule.onNodeWithTag("new_script_button").performTouchInput { click(center); click(center) }
        rule.mainClock.advanceTimeBy(180)
        rule.waitForIdle()
        assertEquals(1, launches)
        assertEquals(initial, rule.onNodeWithTag("new_script_button").fetchSemanticsNode().boundsInRoot)
        assertEquals(initial.center, measuredCenter)
        rule.mainClock.advanceTimeBy(650)
        rule.waitForIdle()
        // If a quick close retains Home, the same button must become usable again.
        rule.onNodeWithTag("new_script_button").performTouchInput { click(center) }
        assertEquals(2, launches)
    }
}
