package com.example.ui.scripts

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.ScriptAction
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** CLAUDE.md: the user sees the literal command, URL or intent of a privileged action before it can run. */
@RunWith(RobolectricTestRunner::class)
class ShortcutReviewDialogTest {

    @get:Rule val rule = createComposeRule()

    @Test fun everyPrivilegedParameterIsShownLiterally() {
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                ShortcutReviewDialog(
                    title = "Import this shortcut?",
                    name = "Mixed",
                    automation = false,
                    actions = listOf(
                        ScriptAction("shell_command", mapOf("command" to "settings put global adb_enabled 0")),
                        ScriptAction("http_request", mapOf("method" to "POST", "url" to "https://example.org/hook?token=abc", "body" to "x=1")),
                        ScriptAction("send_intent", mapOf("action" to "android.intent.action.VIEW", "uri" to "geo:0,0", "extras" to "a=b")),
                        ScriptAction("modify_setting", mapOf("namespace" to "secure", "key" to "location_mode", "value" to "0")),
                        ScriptAction("show_notification", mapOf("title" to "Hi", "onTap" to "url", "url" to "https://example.net/tap")),
                        ScriptAction("toggle_flashlight"),
                    ),
                    note = "note",
                    confirmLabel = "Import",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
        listOf(
            "settings put global adb_enabled 0", "https://example.org/hook?token=abc", "x=1",
            "android.intent.action.VIEW", "geo:0,0", "a=b", "secure", "location_mode",
            "https://example.net/tap",
        ).forEach { rule.onNodeWithText(it, substring = true).assertExists() }
    }
}
