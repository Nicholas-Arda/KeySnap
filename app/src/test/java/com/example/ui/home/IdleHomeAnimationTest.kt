package com.example.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.ui.scripts.ShortcutTutorialCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Idle Home and the empty Shortcuts tutorial must stop asking for frames once their plays finish.
 * A still-running animation writes snapshot state on every frame. The clock stays manual so an
 * infinite transition is not cancelled, then [ComposeContentTestRule.waitForIdle] has to return.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class IdleHomeAnimationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun announcedHomeBecomesIdle() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MyApplicationTheme(ThemeMode.LIGHT) { home(announceWhatsNew = true, isPro = true) }
        }
        // Three 3.2 s shines, plus the ground crossfade.
        rule.mainClock.advanceTimeBy(16_000)
        rule.assertNoSnapshotApplies(4_000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun quietHomeDoesNotTick() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MyApplicationTheme(ThemeMode.LIGHT) { home(announceWhatsNew = false, isPro = false) }
        }
        rule.mainClock.advanceTimeBy(2_000)
        // Longer than the 30 s sale clock. There is no end time, so that clock must not write.
        rule.assertNoSnapshotApplies(35_000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun firstShortcutNudgeRests() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MyApplicationTheme(ThemeMode.LIGHT) { FirstShortcutNudge(onDismiss = {}) }
        }
        // Three nods of 1.6 s, then a 400 ms return to rest.
        rule.mainClock.advanceTimeBy(8_000)
        rule.assertNoSnapshotApplies(4_000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun shortcutTutorialPlaysOnceThenRests() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MyApplicationTheme(ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize()) { ShortcutTutorialCard() }
            }
        }
        // One pass of the four steps is about 19 s; the rest covers the rail springs.
        rule.mainClock.advanceTimeBy(30_000)
        rule.assertNoSnapshotApplies(4_000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test fun setupIllustrationKeepsMovingWhileItsStepIsOpen() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MyApplicationTheme(ThemeMode.LIGHT) { AccessibilityIllustration(reduced = false) }
        }
        rule.mainClock.advanceTimeBy(500)
        val applies = rule.snapshotAppliesDuring(1_000)
        assertTrue("an open setup step should still loop, saw $applies applies", applies > 20)
    }

    @Composable
    private fun home(announceWhatsNew: Boolean, isPro: Boolean) {
        HomeScreen(
            isMappingEnabled = true,
            isServiceActive = true,
            isAdvancedModeRunning = false,
            triggerCount = 1,
            lastSource = "",
            isPro = isPro,
            proPrice = null,
            activeScriptCount = 1,
            activeScriptLimit = 3,
            themeMode = ThemeMode.LIGHT,
            scripts = listOf(flashlight),
            onOpenScript = {},
            onToggleMapping = {},
            onOpenWizard = {},
            onOpenAccessibility = {},
            onPickSuggestion = {},
            onOpenProScreen = {},
            announceWhatsNew = announceWhatsNew,
            nudgeDismissed = true,
        )
    }

    private val flashlight = ShortcutScript(
        id = "torch",
        name = "Torch",
        triggers = listOf(ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)),
        actions = listOf(ScriptAction.ToggleFlashlight),
    )
}

private fun ComposeContentTestRule.assertNoSnapshotApplies(advanceMs: Long) {
    val applies = snapshotAppliesDuring(advanceMs)
    assertEquals("snapshot applies while the clock advanced ${advanceMs}ms after settle", 0, applies)
}

private fun ComposeContentTestRule.snapshotAppliesDuring(advanceMs: Long): Int {
    var applies = 0
    val handle = Snapshot.registerApplyObserver { _, _ -> applies++ }
    try {
        mainClock.advanceTimeBy(advanceMs)
    } finally {
        handle.dispose()
    }
    return applies
}
