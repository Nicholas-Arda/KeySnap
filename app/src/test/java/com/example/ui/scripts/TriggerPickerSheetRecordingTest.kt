package com.example.ui.scripts

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.KeyEventMapping
import com.example.data.TriggerPressType
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import io.github.nicholasarda.keysnap.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Key recording is global coordinator state, so a sheet that armed it must disarm it however it is
 * dismissed — Back, tapping outside, the X, or the editor closing underneath it. All of those end
 * in the sheet leaving composition, which is what this pins.
 */
@RunWith(RobolectricTestRunner::class)
class TriggerPickerSheetRecordingTest {

    @get:Rule val rule = createComposeRule()

    @Test fun assistantCanBePickedWithoutRecordingAndHidesPhysicalPressChoices() {
        var keyCodes by mutableStateOf(emptyList<Int>())
        var press by mutableStateOf(TriggerPressType.DOUBLE_PRESS)
        var recordings = 0
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                TriggerPickerSheet(
                    pressType = press, keyCodes = keyCodes, savedKeys = emptyList(), isRecordingKey = false,
                    onDismiss = {}, onSelectKey = { keyCodes = listOf(it) },
                    onStartRecording = { recordings++ }, onCancelRecording = {}, onSetPressType = { press = it },
                )
            }
        }
        rule.onNodeWithTag("assistant_trigger_option").performClick()
        rule.runOnIdle {
            assertEquals(listOf(KeyEventMapping.ASSISTANT_GESTURE), keyCodes)
            assertEquals(TriggerPressType.SINGLE_PRESS, press)
            assertEquals(0, recordings)
        }
        rule.onNodeWithTag("press_type_DOUBLE_PRESS").assertDoesNotExist()
        rule.onNodeWithTag("press_type_LONG_PRESS").assertDoesNotExist()
    }

    @Test fun assistantDetailsShowOnlyWhileTheGestureIsSelected() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        val hint = res.getString(R.string.assistant_trigger_hint)
        val setup = res.getString(R.string.assistant_setup_title)
        val status = res.getString(R.string.assistant_not_selected)
        val warning = res.getString(R.string.assistant_voice_wakeup_warning)
        var keyCodes by mutableStateOf(listOf(KeyEvent.KEYCODE_VOLUME_UP))
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                TriggerPickerSheet(
                    pressType = TriggerPressType.SINGLE_PRESS,
                    keyCodes = keyCodes,
                    savedKeys = emptyList(),
                    isRecordingKey = false,
                    onDismiss = {},
                    onSelectKey = { keyCodes = listOf(it) },
                    onStartRecording = {},
                    onCancelRecording = {},
                    onSetPressType = {},
                )
            }
        }
        rule.onNodeWithTag("assistant_trigger_option").assertIsDisplayed()
        listOf(hint, setup, status, warning).forEach { rule.onNodeWithText(it).assertDoesNotExist() }

        rule.onNodeWithTag("assistant_trigger_option").performClick()
        rule.waitForIdle()
        listOf(hint, setup, status, warning).forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithTag("assistant_trigger_option").assertIsDisplayed()
    }

    @Test fun leavingCompositionWhileListeningCancelsRecording() {
        var cancels = 0
        var visible by mutableStateOf(true)
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                if (visible) {
                    TriggerPickerSheet(
                        pressType = TriggerPressType.SINGLE_PRESS,
                        keyCodes = emptyList(),
                        savedKeys = emptyList(),
                        isRecordingKey = true,
                        onDismiss = { visible = false },
                        onSelectKey = {},
                        onStartRecording = {},
                        onCancelRecording = { cancels++ },
                        onSetPressType = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals("still open: recording stays armed", 0, cancels)

        rule.runOnIdle { visible = false }
        rule.waitForIdle()
        assertEquals("dismissed: recording disarmed exactly once", 1, cancels)
    }
}
