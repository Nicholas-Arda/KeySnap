package com.example.service

import android.os.Looper
import android.view.KeyEvent
import com.example.data.ScriptAction
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import java.lang.reflect.Modifier
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class DiagnosticRecorderTest {
    @Before
    fun start() {
        DiagnosticRecorder.start(accessibility = true, advancedMode = false, paused = false, scriptCount = 1, consumeOriginal = true)
    }

    @After
    fun clear() {
        DiagnosticRecorder.clear()
    }

    @Test
    fun scriptNamesAndActionParametersNeverEnterRecording() = runBlocking {
        val secret = "SECRET_TOKEN_123"
        val script = ShortcutScript(
            id = secret,
            name = secret,
            triggers = listOf(ScriptTrigger(listOf(KeyEvent.KEYCODE_VOLUME_UP), TriggerPressType.SINGLE_PRESS)),
            actions = listOf(
                ScriptAction("shell_command", mapOf("command" to "echo $secret")),
                ScriptAction("open_url", mapOf("url" to "https://example.com/?token=$secret")),
                ScriptAction("input_text", mapOf("text" to secret)),
                ScriptAction("send_intent", mapOf("uri" to secret, "extras" to secret)),
                ScriptAction("modify_setting", mapOf("key" to secret, "value" to secret)),
                ScriptAction("http_request", mapOf("body" to secret, "headers" to secret)),
                ScriptAction("send_message", mapOf("number" to secret, "contact" to secret, "text" to secret)),
                ScriptAction("show_notification", mapOf("title" to secret, "text" to secret)),
                ScriptAction(secret, mapOf(secret to secret)),
            ),
        )
        val executor = ScriptActionExecutor { true }
        DiagnosticRecorder.gesture(script.id.hashCode(), KeyEvent.KEYCODE_VOLUME_UP, TriggerPressType.SINGLE_PRESS, DiagnosticRecorder.InputPath.ACTIVITY, true)
        executor.executeEach(script.actions, notificationKey = secret, diagnosticScriptId = script.id.hashCode(), diagnosticPressType = TriggerPressType.SINGLE_PRESS)

        val text = DiagnosticRecorder.render()
        assertFalse(text.contains(secret))
        assertFalse(text.contains("https://"))
        assertTrue(text.contains("type=shell_command result=ok"))
        assertTrue(text.contains("type=UNKNOWN result=ok"))
        assertEquals(script.actions.size, text.lineSequence().count { " action " in it })
    }

    @Test
    fun recorderApiAcceptsOnlyPrimitiveAndEnumFields() {
        val methods = DiagnosticRecorder::class.java.declaredMethods.filter {
            Modifier.isPublic(it.modifiers) && !it.isSynthetic
        }
        assertTrue(methods.isNotEmpty())
        methods.forEach { method ->
            method.parameterTypes.forEach { type ->
                assertTrue("${method.name} accepts $type", type.isPrimitive || type.isEnum || type == java.lang.Integer::class.java || type == java.lang.Boolean::class.java)
            }
        }
    }

    @Test
    fun bridgeReportsCannotInjectNamesOrActionPayloads() {
        val secret = "SECRET_TOKEN_123"
        HardwareKeyTriggerCoordinator.onBridgeTrigger(
            keyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            pressType = secret,
            scriptId = secret,
            success = false,
            actionResults = listOf(secret to false),
        )
        val text = DiagnosticRecorder.render()
        assertFalse(text.contains(secret))
        assertTrue(text.contains("type=UNKNOWN result=failed failure=BRIDGE_ACTION_FAILED path=ADVANCED_MODE_BRIDGE"))
    }

    @Test
    fun exceptionMessagesNeverEnterRecordingAndExceptionStillPropagates() = runBlocking {
        val error = IllegalStateException("SECRET_TOKEN_123")
        val executor = ScriptActionExecutor { throw error }
        try {
            executor.executeEach(listOf(ScriptAction("go_back")), diagnosticScriptId = 42)
            throw AssertionError("The executor must still throw")
        } catch (actual: IllegalStateException) {
            assertTrue(actual === error)
        }
        val text = DiagnosticRecorder.render()
        assertFalse(text.contains("SECRET_TOKEN_123"))
        assertTrue(text.contains("failure=EXCEPTION"))
    }

    @Test
    fun autoStopsAt500EventLinesAndRejectsLaterEvents() {
        repeat(499) { keyEvent() }
        assertTrue(DiagnosticRecorder.isRecording.value)
        keyEvent()
        assertFalse(DiagnosticRecorder.recording)
        assertFalse(DiagnosticRecorder.isRecording.value)
        val stopped = DiagnosticRecorder.render()
        repeat(20) { keyEvent() }
        assertEquals(stopped, DiagnosticRecorder.render())
        assertEquals(500, stopped.lineSequence().count { it.startsWith("+") })
        assertTrue(stopped.contains("reason=LINE_LIMIT"))
    }

    @Test
    fun autoStopsAfterTenMinutesWithoutEvents() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))
        assertFalse(DiagnosticRecorder.isRecording.value)
        assertTrue(DiagnosticRecorder.render().contains("reason=TIME_LIMIT"))
    }

    @Test
    fun elapsedDeadlineStillAppliesWhenTheMainLooperHasNotRun() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(10))
        DiagnosticRecorder.checkTimeout()
        assertFalse(DiagnosticRecorder.isRecording.value)
        assertTrue(DiagnosticRecorder.render().contains("reason=TIME_LIMIT"))
    }

    @Test
    fun discardClearsEverythingAndRestartDoesNotKeepOldEvents() {
        keyEvent()
        DiagnosticRecorder.clear()
        assertEquals("", DiagnosticRecorder.render())
        assertFalse(DiagnosticRecorder.hasRecording.value)
        start()
        assertEquals(0, DiagnosticRecorder.render().lineSequence().count { it.startsWith("+") })
    }

    @Test
    fun recordsEachRepeatedExecutionAndSkipsActionsOwnedByBridge() = runBlocking {
        val executor = ScriptActionExecutor { false }
        executor.executeEach(
            listOf(
                ScriptAction("go_back"),
                ScriptAction("repeat_previous", mapOf("count" to "2")),
                ScriptAction("toggle_wifi"),
            ),
            diagnosticScriptId = 42,
            diagnosticPressType = TriggerPressType.LONG_PRESS,
        ) { it.type != "toggle_wifi" }

        val text = DiagnosticRecorder.render()
        assertEquals(3, text.lineSequence().count { "type=go_back result=failed failure=ACTION_FAILED" in it })
        assertFalse(text.contains("type=toggle_wifi"))
        assertTrue(text.contains("scriptId=42 press=LONG_PRESS"))
    }

    @Test
    fun keyMetadataUsesPlatformNamesAndHexValues() {
        keyEvent()
        val text = DiagnosticRecorder.render()
        val platformName = KeyEvent.keyCodeToString(KeyEvent.KEYCODE_VOLUME_UP)
        assertTrue(text, text.contains("code=24 name=$platformName DOWN repeat=2 flags=0x80 source=0x101 deviceId=3 deduplicated=true matched=false consumed=false"))
    }

    private fun keyEvent() {
        DiagnosticRecorder.keyEvent(
            DiagnosticRecorder.InputPath.ACCESSIBILITY,
            DiagnosticRecorder.Stage.ENTRY,
            KeyEvent.KEYCODE_VOLUME_UP,
            isDown = true,
            repeatCount = 2,
            flags = 0x80,
            source = 0x101,
            deviceId = 3,
            deduplicated = true,
            matched = false,
            consumed = false,
        )
    }

    @Test
    fun typingKeysAreMaskedButHardwareButtonsAreKept() {
        listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_AT).forEach {
            DiagnosticRecorder.keyEvent(DiagnosticRecorder.InputPath.ACCESSIBILITY, DiagnosticRecorder.Stage.ENTRY, it, isDown = true)
        }
        DiagnosticRecorder.keyEvent(DiagnosticRecorder.InputPath.ACCESSIBILITY, DiagnosticRecorder.Stage.ENTRY, KeyEvent.KEYCODE_VOLUME_UP, isDown = true)

        val text = DiagnosticRecorder.render()
        listOf("KEYCODE_A ", "KEYCODE_7", "KEYCODE_SPACE", "KEYCODE_NUMPAD_5", "KEYCODE_AT", "code=29 ", "code=14 ").forEach {
            assertFalse(it, text.contains(it))
        }
        assertEquals(5, Regex("code=TEXT_KEY").findAll(text).count())
        assertTrue(text, text.contains("code=24 name="))
    }
}
