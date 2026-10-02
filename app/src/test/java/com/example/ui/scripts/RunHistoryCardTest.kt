package com.example.ui.scripts

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActionResult
import com.example.data.RunFailure
import com.example.data.RunRecord
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import io.github.nicholasarda.keysnap.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunHistoryCardTest {

    @get:Rule val rule = createComposeRule()
    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    private fun show(runs: List<RunRecord>) = rule.setContent {
        MyApplicationTheme(themeMode = ThemeMode.DARK) { RunHistoryCard(runs, emptyList()) }
    }

    @Test fun aFailedRunNamesTheActionAndWhy() {
        show(
            listOf(
                RunRecord(
                    "s1", 0L, RunRecord.SOURCE_KEY, KeyEvent.KEYCODE_VOLUME_DOWN, "DOUBLE_PRESS",
                    listOf(
                        ActionResult("toggle_flashlight", ok = true),
                        ActionResult("toggle_wifi", ok = false, reason = RunFailure.ADVANCED_MODE_OFF.name),
                    ),
                ),
            ),
        )
        rule.onNodeWithText("${res.getString(R.string.action_toggle_wifi)} · ${res.getString(R.string.badge_expert_mode)}")
            .assertIsDisplayed()
    }

    @Test fun aCleanAutomationRunSaysEverythingRan() {
        show(
            listOf(
                RunRecord(
                    "s1", 0L, RunRecord.SOURCE_AUTOMATION, null, null,
                    listOf(ActionResult("delay", ok = true), ActionResult("vibrate", ok = true)),
                ),
            ),
        )
        rule.onNodeWithText(res.getString(R.string.trigger_kind_automation)).assertIsDisplayed()
        rule.onNodeWithText(res.getQuantityString(R.plurals.run_all, 2, 2)).assertIsDisplayed()
    }

    @Test fun widgetAndTileRunsNameTheirSource() {
        show(
            listOf(
                RunRecord("s1", 1L, RunRecord.SOURCE_WIDGET, null, null, listOf(ActionResult("vibrate", ok = true))),
                RunRecord("s1", 0L, RunRecord.SOURCE_TILE, null, null, listOf(ActionResult("vibrate", ok = true))),
            ),
        )
        rule.onNodeWithText(res.getString(R.string.run_source_widget)).assertIsDisplayed()
        rule.onNodeWithText(res.getString(R.string.run_source_tile)).assertIsDisplayed()
    }

    @Test fun noRunsSaysSo() {
        show(emptyList())
        rule.onNodeWithText(res.getString(R.string.run_history_empty)).assertIsDisplayed()
    }
}
