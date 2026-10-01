package com.example.ui.scripts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import com.example.data.InstalledApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back-to-back swipes inside the list must scroll the list. After every list gesture M3 settles
 * the sheet (a 300 ms no-op tween), and while that runs the sheet grabs the next touch outright:
 * a swipe up went nowhere and a swipe down dragged the sheet away mid-list. At the very top a
 * pull down must still dismiss it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppPickerSheetScrollTest {

    @get:Rule val rule = createComposeRule()

    private var dismissals = 0
    private val apps = List(200) { InstalledApp("pkg.app%03d".format(it), "App %03d".format(it)) }

    private fun show(startIndex: Int = 100) {
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                AppPickerSheet(
                    apps = apps,
                    selected = null,
                    onSelect = {},
                    onDismiss = { dismissals++ },
                    loadIcon = { _, _ -> null },
                )
            }
        }
        rule.waitForIdle()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(startIndex)
        rule.waitForIdle()
        // From here on time moves only when the test says so, as on a real finger.
        rule.mainClock.autoAdvance = false
    }

    /** Index of the topmost composed row — a coarse read of the list's scroll position. */
    private fun topRow(): Int = rule.onAllNodes(
        SemanticsMatcher("app row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("app_option_") == true },
    ).fetchSemanticsNodes().minOf { it.config[SemanticsProperties.TestTag].takeLast(3).toInt() }

    /** A slow drag that stops before lifting, so it carries next to no fling. 120 Hz events. */
    private fun drag(dy: Float) {
        rule.onNodeWithTag("app_picker_sheet").performTouchInput {
            down(center)
            repeat(100) { moveBy(Offset(0f, dy / 100), delayMillis = 8) }
            advanceEventTime(80)
            up()
        }
    }

    private fun quickGap() = rule.mainClock.advanceTimeBy(48)

    @Test fun secondSwipeDownScrollsTheListInsteadOfDismissingTheSheet() {
        show()
        val start = topRow()
        drag(600f)
        quickGap()
        val afterFirst = topRow()
        drag(600f)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        assertEquals("the sheet stays open while the list is mid-way", 0, dismissals)
        assertTrue("first swipe scrolled up: $start -> $afterFirst", afterFirst < start)
        assertTrue("second swipe scrolled up too: $afterFirst -> ${topRow()}", topRow() < afterFirst)
    }

    @Test fun secondSwipeUpScrollsTheList() {
        show()
        drag(-600f)
        quickGap()
        val afterFirst = topRow()
        drag(-600f)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        assertEquals(0, dismissals)
        assertTrue("second swipe scrolled down: $afterFirst -> ${topRow()}", topRow() > afterFirst)
    }

    @Test fun pullDownAtTheTopStillDismisses() {
        show(startIndex = 0)
        drag(600f)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        assertEquals(1, dismissals)
    }
}
