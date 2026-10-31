package com.example.ui.scripts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.example.bridge.ActionTestResult
import com.example.data.PrivilegedEditor
import com.example.data.ScriptAction
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import io.github.nicholasarda.keysnap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-mdpi")
class PrivilegedActionEditorTest {
    @get:Rule val rule = createComposeRule()

    private fun host(content: @Composable () -> Unit) {
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.fillMaxWidth().height(800.dp)) { content() }
            }
        }
    }

    private fun editable(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun httpUrlErrorsAndPlainHttpAndBodyVisibility() {
        host {
            PrivilegedEditorPage(PrivilegedEditor.HTTP, emptyMap(), false, emptyList(), {}, {})
        }
        // Untouched: nothing in red yet. Typed then cleared: the empty URL is named.
        rule.onNodeWithText("Add a URL").assertDoesNotExist()
        rule.onNodeWithTag("action_config_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("config_field_body").assertDoesNotExist()
        rule.onNodeWithTag("config_field_url").performTextReplacement("h")
        rule.onNodeWithTag("config_field_url").performTextReplacement("")
        rule.onNodeWithText("Add a URL").assertIsDisplayed()
        rule.onNodeWithTag("config_field_url").performTextReplacement("ftp://files")
        rule.onNodeWithText("Use http:// or https://").assertIsDisplayed()
        rule.onNodeWithTag("action_config_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("config_field_url").performTextReplacement("https://example.com")
        rule.onNodeWithText("Use http:// or https://").assertDoesNotExist()
        rule.onNodeWithText("Add a URL").assertDoesNotExist()
        rule.onNodeWithTag("action_config_confirm").assertIsEnabled()
        rule.onNodeWithTag("config_choice_method_POST").performClick()
        rule.onNodeWithTag("config_field_body").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("config_choice_method_GET").performClick()
        rule.onNodeWithTag("config_field_body").assertDoesNotExist()
        rule.onNodeWithTag("config_field_url").performTextReplacement("http://example.com")
        rule.onNodeWithTag("config_plain_http").assertIsDisplayed()
        rule.onNodeWithText("Plain HTTP").assertIsDisplayed()
        rule.onNodeWithTag("action_config_confirm").assertIsEnabled()
    }

    @Test
    fun anOldHttpUrlOpensInTheField() {
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.HTTP,
                mapOf("url" to "https://old.example/hook"),
                false,
                emptyList(),
                {},
                {},
            )
        }
        assertEquals("https://old.example/hook", editable("config_field_url"))
    }

    @Test
    fun httpMasksAuthorizationPrefixesBearerOnTapAndPreviewsWithoutTheSecret() {
        val ran = mutableListOf<ScriptAction>()
        var saved: Map<String, String>? = null
        host {
            PrivilegedEditorPage(
                actionType = PrivilegedEditor.HTTP,
                initialParams = emptyMap(),
                startsFromNotification = false,
                installedApps = emptyList(),
                onConfirm = { saved = it },
                onDismiss = {},
                runTest = { action ->
                    ran += action
                    ActionTestResult(true, null, 200, 400, false, listOf("secret-body"))
                },
            )
        }
        rule.onNodeWithTag("config_field_url").performTextReplacement("https://example.com/h")
        rule.onNodeWithTag("config_choice_method_POST").performClick()
        rule.onNodeWithTag("config_choice_content_type_json").performScrollTo().performClick()
        rule.onNodeWithTag("config_add_header").performScrollTo().performClick()
        rule.onNodeWithTag("config_header_value_0").performTextReplacement("secret")
        rule.onNodeWithTag("config_header_name_0").performTextReplacement("Authorization")
        rule.waitForIdle()
        rule.onNodeWithTag("config_header_masked_0").assertExists()
        assertTrue(editable("config_header_value_0").all { it == '•' })
        assertFalse(editable("config_header_value_0").contains("secret"))
        rule.onNodeWithText("secret").assertDoesNotExist()
        rule.onNodeWithTag("config_bearer_chip").performScrollTo().performClick()
        rule.onNodeWithTag("config_bearer_chip").assertDoesNotExist()
        rule.onNodeWithText("POST https://example.com/h · 1 header · JSON").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("config_header_reveal_0").performClick()
        rule.onNodeWithTag("config_header_revealed_0").assertExists()
        assertEquals("Bearer secret", editable("config_header_value_0"))
        val preview = rule.onNodeWithTag("config_what_will_run").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assertEquals("POST https://example.com/h · 1 header · JSON", preview)
        assertFalse(preview.contains("secret"))
        rule.onNodeWithTag("action_test_button").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("200 OK · 0.4 s").performScrollTo().assertIsDisplayed()
        assertEquals(1, ran.size)
        assertFalse(ran.single().params["headers"].orEmpty().contains("secret-body"))
        assertTrue(ran.single().params["headers"].orEmpty().contains("Authorization:Bearer secret"))
        rule.onNodeWithTag("action_config_confirm").performScrollTo().performClick()
        assertTrue(saved!!["headers"].orEmpty().contains("Authorization:Bearer secret"))
    }

    @Test
    fun httpFailureShowsTheStatusWithoutCallingItOk() {
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.HTTP,
                mapOf("url" to "https://example.com"),
                false,
                emptyList(),
                {},
                {},
                runTest = { ActionTestResult(false, null, 500, 400, false, emptyList()) },
            )
        }
        rule.onNodeWithTag("action_test_button").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("500 · 0.4 s").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("500 OK", substring = true).assertDoesNotExist()
    }

    @Test
    fun shellContextChipSetupAndReadyCommandFillWithoutRunning() {
        var setup = 0
        val ran = mutableListOf<ScriptAction>()
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.SHELL,
                emptyMap(),
                false,
                emptyList(),
                {},
                {},
                advancedActive = false,
                onSetUpAdvancedMode = { setup++ },
                runTest = { action ->
                    ran += action
                    ActionTestResult(true, 0, null, 200, false, listOf("hello"))
                },
            )
        }
        rule.onNodeWithText("App only · limited").assertIsDisplayed()
        rule.onNodeWithTag("config_shell_context").assertIsDisplayed()
        rule.onNodeWithTag("config_setup_advanced").performClick()
        assertEquals(1, setup)
        rule.onNodeWithTag("action_config_sheet").assertIsDisplayed()
        val ready = PrivilegedEditor.shellReadyCommands().first { it.titleRes == R.string.recipe_screen_record }
        rule.onNodeWithText("Record Screen for 30 s").performScrollTo().performClick()
        assertEquals(0, ran.size)
        assertEquals(ready.command, editable("config_field_command"))
        rule.onNodeWithTag("action_test_button").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(ready.command, ran.single().params["command"])
        rule.onNodeWithText("Exit 0 · 0.2 s").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("config_shell_output").assertIsDisplayed()
        rule.onNodeWithText("hello").assertIsDisplayed()
    }

    @Test
    fun shellAdvancedModeChipHasNoSetup() {
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.SHELL,
                mapOf("command" to "ls"),
                false,
                emptyList(),
                {},
                {},
                advancedActive = true,
            )
        }
        rule.onNodeWithText("Runs in Advanced Mode").assertIsDisplayed()
        rule.onNodeWithTag("config_setup_advanced").assertDoesNotExist()
    }

    @Test
    fun intentRejectsABadExtraAndStillPreviewsTheGoodOne() {
        val ran = mutableListOf<ScriptAction>()
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.INTENT,
                emptyMap(),
                false,
                emptyList(),
                {},
                {},
                runTest = { action ->
                    ran += action
                    ActionTestResult(false, null, null, 100, false, emptyList())
                },
            )
        }
        rule.onNodeWithTag("config_field_action").performTextReplacement("com.example.ACTION")
        rule.onNodeWithTag("config_add_extra").performScrollTo().performClick()
        rule.onNodeWithTag("config_extra_key_0").performTextReplacement("name")
        rule.onNodeWithTag("config_extra_value_0").performTextReplacement("Gym")
        rule.onNodeWithTag("config_add_extra").performScrollTo().performClick()
        rule.onNodeWithTag("config_extra_key_1").performTextReplacement("note")
        rule.onNodeWithTag("config_extra_value_1").performTextReplacement("a;b")
        rule.onNodeWithText("\";\" or \"=\" can't go here").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("action_config_confirm").assertIsNotEnabled()
        val broadcast = rule.onNodeWithTag("config_what_will_run").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assertEquals("am broadcast -a com.example.ACTION --es name Gym", broadcast)
        assertFalse(broadcast.contains("a;b"))
        rule.onNodeWithTag("config_choice_target_activity").performScrollTo().performClick()
        rule.onNodeWithText("am start -a com.example.ACTION -f 0x10000000 --es name Gym", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("action_test_button").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("action_test_button").performClick()
        rule.waitForIdle()
        assertEquals("name=Gym;note=a;b", ran.single().params["extras"])
    }

    @Test
    fun replyChatChipFillsAndThereIsNoTestButton() {
        host {
            PrivilegedEditorPage(
                actionType = PrivilegedEditor.REPLY,
                initialParams = mapOf("packageName" to "com.chat", "text" to "busy"),
                startsFromNotification = false,
                installedApps = emptyList(),
                onConfirm = {},
                onDismiss = {},
                everyChatWarning = true,
                visibleChats = { listOf("Mom") },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("action_test_button").assertDoesNotExist()
        rule.onNodeWithTag("every_chat_warning").assertIsDisplayed()
        rule.onNodeWithTag("config_reply_cooldown").assertIsDisplayed()
        rule.onNodeWithText("Mom").performScrollTo().performClick()
        rule.onNodeWithText("Preview · Mom").assertIsDisplayed()
        rule.onNodeWithTag("every_chat_warning").assertDoesNotExist()
        rule.onNodeWithTag("config_chat_type_name").performScrollTo().performClick()
        rule.onNodeWithTag("config_field_chat").performTextReplacement("Ann")
        rule.onNodeWithText("Preview · Ann").assertIsDisplayed()
    }

    @Test
    fun aSavedChatThatIsNotShowingOpensTheNameField() {
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.REPLY,
                mapOf("packageName" to "com.chat", "chat" to "Old", "text" to "busy"),
                false,
                emptyList(),
                {},
                {},
                visibleChats = { listOf("Mom") },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("config_field_chat").assertIsDisplayed()
        assertEquals("Old", editable("config_field_chat"))
    }

    @Test
    fun replyWarningAndCooldownDismissStayExact() {
        var removed = 0
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.REPLY,
                emptyMap(),
                false,
                emptyList(),
                {},
                {},
                everyChatWarning = true,
                onRemoveReplyCooldown = { removed++ },
            )
        }
        rule.onNodeWithTag("every_chat_warning").assertIsDisplayed()
        // A fresh form names nothing in red until the user starts filling it in.
        rule.onNodeWithText("Add a message").assertDoesNotExist()
        rule.onNodeWithText("Add a chat").assertDoesNotExist()
        rule.onNodeWithTag("config_reply_cooldown").performScrollTo().performClick()
        assertEquals(1, removed)
        rule.onNodeWithTag("config_reply_cooldown").assertDoesNotExist()
    }

    @Test
    fun aFiveMinuteCooldownIsNotReplacedByTheChip() {
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.REPLY,
                emptyMap(),
                false,
                emptyList(),
                {},
                {},
                everyChatWarning = true,
                existingCooldownMinutes = "5",
            )
        }
        rule.onNodeWithTag("every_chat_warning").assertIsDisplayed()
        rule.onNodeWithTag("config_reply_cooldown").assertDoesNotExist()
    }

    @Test
    fun settingSuggestionFillsTheKeyAndDoesNotRun() {
        var runs = 0
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.SETTING,
                emptyMap(),
                false,
                emptyList(),
                {},
                {},
                runTest = {
                    runs++
                    ActionTestResult(true, null, null, 10, false, emptyList())
                },
            )
        }
        rule.onNodeWithTag("config_setting_suggestion_font_scale").performScrollTo().performClick()
        assertEquals(0, runs)
        assertEquals("font_scale", editable("config_field_key"))
        rule.onNodeWithTag("config_setting_search").performTextReplacement("font")
        rule.onNodeWithTag("config_setting_suggestion_font_scale").assertIsDisplayed()
        rule.onNodeWithTag("config_setting_suggestion_screen_brightness").assertDoesNotExist()
        rule.onNodeWithTag("config_setting_search").performTextReplacement("adb")
        rule.onNodeWithTag("config_setting_suggestion_font_scale").assertDoesNotExist()
        rule.onNodeWithTag("config_setting_suggestion_screen_off_timeout").assertDoesNotExist()
        rule.onNodeWithTag("config_setting_suggestion_screen_brightness").assertDoesNotExist()
    }

    @Test
    fun settingNowReadsOnlyTheSelectedKeyAndSecureNeedsAdvancedMode() {
        var reads = 0
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.SETTING,
                emptyMap(),
                false,
                emptyList(),
                {},
                {},
                advancedActive = false,
                readSetting = { _, _ ->
                    reads++
                    "30000"
                },
            )
        }
        rule.onNodeWithTag("config_setting_suggestion_screen_off_timeout").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithText("Now: 30000").assertIsDisplayed()
        assertTrue(reads > 0)
        val beforeSecure = reads
        rule.onNodeWithTag("config_choice_namespace_secure").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithTag("config_setting_needs_advanced").assertIsDisplayed()
        rule.onNodeWithText("Now: 30000").assertDoesNotExist()
        assertEquals(beforeSecure, reads)
    }

    @Test
    fun settingTestWritesAndUndoRestoresTheValueHeldInMemory() {
        val writes = mutableListOf<Pair<String, String>>()
        var shelled = false
        host {
            PrivilegedEditorPage(
                PrivilegedEditor.SETTING,
                emptyMap(),
                false,
                emptyList(),
                {},
                {},
                advancedActive = false,
                canWriteSettings = true,
                readSetting = { _, _ -> "30000" },
                writeSystemSetting = { key, value ->
                    writes += key to value
                    ActionTestResult(true, null, null, 100, false, emptyList())
                },
                runTest = {
                    shelled = true
                    ActionTestResult(false, null, null, 0, false, emptyList())
                },
            )
        }
        rule.onNodeWithTag("config_setting_suggestion_screen_off_timeout").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithTag("config_field_value").performTextReplacement("15000")
        rule.onNodeWithTag("action_test_button").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Written").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("config_undo").performClick()
        rule.waitForIdle()
        assertEquals(listOf("screen_off_timeout" to "15000", "screen_off_timeout" to "30000"), writes)
        assertFalse(shelled)
        rule.onNodeWithTag("config_undo").assertDoesNotExist()
    }
}
