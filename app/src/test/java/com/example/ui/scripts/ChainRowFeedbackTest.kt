package com.example.ui.scripts

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.ActionTestResult
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptRepository
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.data.constraintLabel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.ScriptDraft
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChainRowFeedbackTest {
    @get:Rule val rule = createComposeRule()
    private val http = ScriptAction("http_request", mapOf("url" to "https://example.com/private", "method" to "GET"))
    private val success = ActionTestResult(true, null, 200, 400, false, emptyList())

    @Composable
    private fun ActionRow(
        action: ScriptAction = http,
        index: Int = 0,
        advanced: Boolean = false,
        onSetup: () -> Unit = {},
        runAction: suspend (ScriptAction) -> ActionTestResult = { success },
        onResult: (EditorFeedback) -> Unit = {},
    ) {
        ActionChainCard(
            action, index, 1, false, advanced, false,
            onOpenMenu = {}, onDismissMenu = {}, onConfigure = {}, onRemove = {},
            onMoveUp = {}, onMoveDown = {}, onTestAction = runAction,
            onTestResult = onResult, onSetUpAdvancedMode = onSetup,
        )
    }

    private fun showTest(result: ActionTestResult, onRun: (ScriptAction) -> Unit = {}) {
        var feedback by mutableStateOf<EditorFeedback?>(null)
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Column {
                    ActionRow(runAction = { onRun(it); result }, onResult = { feedback = it })
                    EditorFeedbackPill(feedback)
                }
            }
        }
        rule.onNodeWithTag("test_action_0").performClick()
    }

    @Test fun successRunsOnlyTheSuppliedActionAndDisplaysStatus() {
        val executed = mutableListOf<ScriptAction>()
        showTest(success) { executed += it }
        rule.onNodeWithTag("test_action_0").assertContentDescriptionEquals("Test succeeded")
        rule.onNodeWithText("✓ HTTP · 200 OK · 0.4 s").assertIsDisplayed()
        assertEquals(listOf(http), executed)
    }

    @Test fun failureDisplaysHttpReasonWithoutPayload() {
        showTest(ActionTestResult(false, null, 503, 100, false, listOf("secret response")))
        rule.onNodeWithTag("test_action_0").assertContentDescriptionEquals("Test failed")
        rule.onNodeWithText("✗ HTTP · HTTP 503 · 0.1 s").assertIsDisplayed()
        rule.onNodeWithText("secret response").assertDoesNotExist()
        rule.onNodeWithText(http.params.getValue("url")).assertDoesNotExist()
    }

    @Test fun timeoutDisplaysSafeReason() {
        showTest(ActionTestResult(false, null, null, 5_000, true, emptyList()))
        rule.onNodeWithText("✗ HTTP · Timed out · 5.0 s").assertIsDisplayed()
    }

    @Test fun runningShowsProgressAndPreventsDuplicateTaps() {
        val pending = CompletableDeferred<ActionTestResult>()
        var runs = 0
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Column { ActionRow(runAction = { runs++; pending.await() }) }
            }
        }
        rule.onNodeWithTag("test_action_0").performClick()
        rule.onNodeWithTag("action_test_progress_0", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("test_action_0").assertIsNotEnabled()
        assertEquals(1, runs)
        rule.runOnIdle { pending.complete(success) }
        rule.onNodeWithTag("test_action_0").assertContentDescriptionEquals("Test succeeded")
    }

    @Composable
    private fun Conditions(constraints: List<ScriptConstraint>, onSetParams: (Int, Map<String, String>) -> Unit) {
        ConstraintsCard(constraints, {}, {}, {}, emptyList(), false, onSetParams = onSetParams)
    }

    @Test fun notTogglePersistsAndPreservesConfiguration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("chain_not_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        fun repository() = ScriptRepository(prefs, Moshi.Builder().build())
        val saved = ShortcutScript(
            "not-test", "Home", true,
            listOf(ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS)), listOf(ScriptAction.ToggleFlashlight),
            constraints = listOf(ScriptConstraint("connected_to_wifi_network", mapOf("ssid" to "Home"))),
        )
        repository().upsert(saved)
        var conditions by mutableStateOf(saved.constraints)
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Conditions(conditions) { index, params ->
                    conditions = conditions.mapIndexed { position, condition -> if (position == index) condition.copy(params = params) else condition }
                    repository().upsert(saved.copy(constraints = conditions))
                }
            }
        }
        rule.onNodeWithTag("constraint_not").performClick().assertIsSelected()
        rule.onNodeWithText(context.resources.constraintLabel(conditions.single())).assertExists()
        val reloaded = repository().scripts.value.single().constraints.single()
        assertEquals(mapOf("ssid" to "Home", "negate" to "true"), reloaded.params)
        rule.onNodeWithTag("constraint_is").performClick().assertIsSelected()
        assertEquals(mapOf("ssid" to "Home"), repository().scripts.value.single().constraints.single().params)
    }

    @Test fun modeConditionHasNoNotToggle() {
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Conditions(listOf(ScriptConstraint("mode_is", mapOf("mode" to "1")))) { _, _ -> }
            }
        }
        rule.onNodeWithTag("constraint_not").assertDoesNotExist()
    }

    @Test fun actionErrorAppearsAndClearsAfterConfiguration() {
        var action by mutableStateOf(ScriptAction("http_request"))
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) { Column { ActionRow(action) } }
        }
        rule.onNodeWithTag("action_error_0").assertIsDisplayed()
        rule.onNodeWithText("Action can't be empty").assertIsDisplayed()
        rule.onNodeWithTag("test_action_0").assertIsNotEnabled()
        rule.runOnIdle { action = http }
        rule.onNodeWithTag("action_error_0").assertDoesNotExist()
        rule.onNodeWithTag("test_action_0").assertIsEnabled()
    }

    @Test fun conditionErrorAppearsAndClearsAfterConfiguration() {
        var constraints by mutableStateOf(listOf(ScriptConstraint("cooldown")))
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) { Conditions(constraints) { _, _ -> } }
        }
        rule.onNodeWithTag("constraint_error_0").assertIsDisplayed()
        rule.runOnIdle { constraints = listOf(ScriptConstraint("cooldown", mapOf("minutes" to "10"))) }
        rule.onNodeWithTag("constraint_error_0").assertDoesNotExist()
    }

    @Test fun shellWarningOpensSetupAndDisappearsInAdvancedMode() {
        var advanced by mutableStateOf(false)
        var setups = 0
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Column { ActionRow(ScriptAction("shell_command", mapOf("command" to "echo hello")), advanced = advanced, onSetup = { setups++ }) }
            }
        }
        rule.onNodeWithTag("shell_limited_warning").assertIsDisplayed()
        rule.onNodeWithTag("shell_set_up").performClick()
        assertEquals(1, setups)
        rule.runOnIdle { advanced = true }
        rule.onNodeWithTag("shell_limited_warning").assertDoesNotExist()
    }

    @Test fun constraintSheetSavesNotAlongsideExistingParameters() {
        var params: Map<String, String>? = null
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                ConstraintConfigSheet("cooldown", mapOf("minutes" to "10"), true, emptyList(), { params = it }, {})
            }
        }
        rule.onNodeWithTag("constraint_not").performClick()
        rule.onNodeWithTag("constraint_config_confirm").performClick()
        assertEquals("true", params?.get("negate"))
        assertEquals("10", params?.get("minutes"))
    }

    @Test fun saveExplainsMissingConfigurationInsteadOfBeingDisabled() {
        val draft = ScriptDraft("test", "Test", true, ScriptTrigger(listOf(24), TriggerPressType.SINGLE_PRESS), actions = listOf(ScriptAction("http_request")), isNew = true)
        var saves = 0
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                ScriptEditorScreen(
                    draft, null, {}, {}, { _, _ -> }, {}, {}, {}, { _, _ -> }, {}, {}, {},
                    onPickConstraint = {}, onOpenScriptSettings = {}, onRunOnce = {}, onDeleteScript = {},
                    onDone = { saves++ }, onClose = {},
                )
            }
        }
        rule.onNodeWithTag("script_done_button").assertIsEnabled().performClick()
        rule.onNodeWithTag("chain_feedback").assertIsDisplayed()
        assertEquals(0, saves)
    }

    @Test fun messageSendingActionsHaveNoTestButton() {
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Column {
                    ActionRow(ScriptAction("reply_to_notification"), index = 0)
                    ActionRow(ScriptAction("send_message"), index = 1)
                }
            }
        }
        rule.onNodeWithTag("test_action_0").assertDoesNotExist()
        rule.onNodeWithTag("test_action_1").assertDoesNotExist()
        assertFalse(canTestChainAction(ScriptAction("reply_notification")))
    }
}
