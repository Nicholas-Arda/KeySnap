package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.bridge.KeyEventMapping
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NameKeyDialogTipTest {

    @get:Rule val rule = createComposeRule()

    private fun show(code: Int) {
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) { NameKeyDialog(code, onDismiss = {}, onSave = {}) }
        }
        rule.waitForIdle()
    }

    @Test fun anInventedOemCodeShowsTheTip() {
        show(KeyEventMapping.parse("/dev/input/event4: EV_KEY KEY_F13 DOWN").androidKeyCode)
        rule.onNodeWithTag("oem_key_tip").assertExists()
    }

    /** KEYCODE_F1 is unlabelled in the app, so the dialog opens, but Android knows it: no tip. */
    @Test fun anUnlabelledRealCodeDoesNotShowTheTip() {
        show(131)
        rule.onNodeWithTag("oem_key_tip").assertDoesNotExist()
    }
}
