package com.example.ui.scripts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.TriggerPressType
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
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
