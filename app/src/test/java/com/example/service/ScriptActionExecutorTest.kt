package com.example.service

import android.app.NotificationManager
import android.media.AudioManager
import com.example.data.ScriptAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pinch geometry is extracted from [ScriptActionExecutor] as a pure function precisely so it
 * can be pinned here without Android's Path or a live AccessibilityService.
 */
class ScriptActionExecutorTest {

    @Test fun pinchingInMovesBothFingersFromTheirPointsToTheMidpoint() {
        val (first, second) = pinchStrokes(ax = 100f, ay = 200f, bx = 500f, by = 600f, closingIn = true)
        assertEquals(GestureStroke(100f, 200f, 300f, 400f), first)
        assertEquals(GestureStroke(500f, 600f, 300f, 400f), second)
    }

    @Test fun pinchingOutMovesBothFingersFromTheMidpointToTheirPoints() {
        val (first, second) = pinchStrokes(ax = 100f, ay = 200f, bx = 500f, by = 600f, closingIn = false)
        assertEquals(GestureStroke(300f, 400f, 100f, 200f), first)
        assertEquals(GestureStroke(300f, 400f, 500f, 600f), second)
    }

    @Test fun aHorizontalAxisReproducesTheOldCenterAndDistanceGesture() {
        // Legacy behavior: center (500, 1000), distance 400 — i.e. points 300 and 700 on one row.
        val (first, second) = pinchStrokes(ax = 300f, ay = 1000f, bx = 700f, by = 1000f, closingIn = true)
        assertEquals(GestureStroke(300f, 1000f, 500f, 1000f), first)
        assertEquals(GestureStroke(700f, 1000f, 500f, 1000f), second)
    }

    @Test fun aVerticalAxisIsSupportedRatherThanFlattenedToHorizontal() {
        val (first, second) = pinchStrokes(ax = 400f, ay = 100f, bx = 400f, by = 900f, closingIn = false)
        assertEquals(GestureStroke(400f, 500f, 400f, 100f), first)
        assertEquals(GestureStroke(400f, 500f, 400f, 900f), second)
    }

    @Test fun vibrateRejectsAZeroDurationRatherThanCoercingItUp() {
        assertEquals(null, resolveVibrateDurationMs("0", maxDurationMs = 5_000L))
    }

    @Test fun vibrateRejectsANegativeDuration() {
        assertEquals(null, resolveVibrateDurationMs("-5", maxDurationMs = 5_000L))
    }

    @Test fun vibrateRejectsAnUnparsableDuration() {
        assertEquals(null, resolveVibrateDurationMs("not-a-number", maxDurationMs = 5_000L))
    }

    @Test fun vibrateClampsAnOverlongDurationToTheMax() {
        assertEquals(5_000L, resolveVibrateDurationMs("99999", maxDurationMs = 5_000L))
    }

    @Test fun vibrateAcceptsAnOrdinaryDurationUnchanged() {
        assertEquals(250L, resolveVibrateDurationMs("250", maxDurationMs = 5_000L))
    }

    @Test fun wordBoundsSelectsTheWordTheCursorSitsInside() {
        assertEquals(0..4, wordBoundsAtCursor("hello world", cursor = 2))
    }

    @Test fun wordBoundsAtAWhitespaceBoundaryPrefersTheWordBeforeIt() {
        assertEquals(0..4, wordBoundsAtCursor("hello world", cursor = 5))
    }

    @Test fun wordBoundsAtTheEndOfTextSelectsTheLastWord() {
        assertEquals(6..10, wordBoundsAtCursor("hello world", cursor = 11))
    }

    @Test fun wordBoundsInsideWhitespaceIsNull() {
        assertEquals(null, wordBoundsAtCursor("hello   world", cursor = 6))
    }

    @Test fun wordBoundsOnEmptyTextIsNull() {
        assertEquals(null, wordBoundsAtCursor("", cursor = 0))
    }

    @Test fun wordBoundsClampsAnOutOfRangeCursor() {
        assertEquals(0..4, wordBoundsAtCursor("hello", cursor = 999))
    }

    @Test fun ringerModeForMapsEachChoiceToItsAudioManagerConstant() {
        assertEquals(AudioManager.RINGER_MODE_NORMAL, ringerModeFor("normal"))
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, ringerModeFor("vibrate"))
        assertEquals(AudioManager.RINGER_MODE_SILENT, ringerModeFor("silent"))
    }

    @Test fun ringerModeForRejectsAnUnknownMode() {
        assertEquals(null, ringerModeFor("loud"))
        assertEquals(null, ringerModeFor(null))
    }

    @Test fun dndTogglesOnFromAllThroughAndOffFromAnyOtherFilter() {
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, dndFilterFor(NotificationManager.INTERRUPTION_FILTER_ALL, null))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dndFilterFor(NotificationManager.INTERRUPTION_FILTER_PRIORITY, null))
        // An OEM leaving the phone on alarms-only must turn DND off, not flip it to priority.
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dndFilterFor(NotificationManager.INTERRUPTION_FILTER_ALARMS, null))
    }

    @Test fun explicitDndActionsIgnoreTheCurrentFilter() {
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, dndFilterFor(NotificationManager.INTERRUPTION_FILTER_PRIORITY, true))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dndFilterFor(NotificationManager.INTERRUPTION_FILTER_ALL, false))
    }

    @Test fun brightnessStepsStayInsideTheBridgeZeroToTwoFiftyFiveRange() {
        assertEquals(154, brightnessLevelFor(128, BRIGHTNESS_STEP))
        assertEquals(102, brightnessLevelFor(128, -BRIGHTNESS_STEP))
        // The bridge clamps rather than refusing, so the +/- actions saturate at both ends.
        assertEquals(MAX_BRIGHTNESS, brightnessLevelFor(250, BRIGHTNESS_STEP))
        assertEquals(0, brightnessLevelFor(10, -BRIGHTNESS_STEP))
        // change_brightness passes its raw param through with no step.
        assertEquals(MAX_BRIGHTNESS, brightnessLevelFor(9000, 0))
        assertEquals(0, brightnessLevelFor(-5, 0))
    }

    @Test fun rotationCyclesThroughAllFourQuartersAndWrapsBackToPortrait() {
        assertEquals(1, nextUserRotation(0))
        assertEquals(2, nextUserRotation(1))
        assertEquals(3, nextUserRotation(2))
        assertEquals(0, nextUserRotation(3))
    }

    @Test fun ringerModeCyclesNormalToVibrateToSilentAndBackToNormal() {
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, nextRingerMode(AudioManager.RINGER_MODE_NORMAL))
        assertEquals(AudioManager.RINGER_MODE_SILENT, nextRingerMode(AudioManager.RINGER_MODE_VIBRATE))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, nextRingerMode(AudioManager.RINGER_MODE_SILENT))
    }

    /**
     * `shouldRun` is how HardwareKeyTriggerCoordinator splits a script between the bridge (already
     * ran the supported actions) and the app (runs the rest) without re-deriving press timing or
     * duplicating execution. These pin that a skipped action neither calls the handler nor breaks
     * "previous" tracking for a `repeat_previous` that follows it.
     */
    @Test fun shouldRunFalseSkipsTheActionWithoutCallingTheHandler() = runBlocking {
        val ran = mutableListOf<String>()
        val executor = ScriptActionExecutor { action -> ran.add(action.type); true }
        val actions = listOf(ScriptAction("toggle_flashlight"), ScriptAction("cycle_ringer_mode"))

        val results = executor.execute(actions) { it.type == "cycle_ringer_mode" }

        assertEquals(listOf("cycle_ringer_mode"), ran)
        assertEquals(listOf(false, true), results)
    }

    @Test fun repeatPreviousReplaysThePreviousActionWhenBothAreRunnable() = runBlocking {
        val ran = mutableListOf<String>()
        val executor = ScriptActionExecutor { action -> ran.add(action.type); true }
        // cycle_ringer_mode is app-run (bridge-unsupported); repeat_previous of it is app-run too.
        val actions = listOf(ScriptAction("cycle_ringer_mode"), ScriptAction("repeat_previous", mapOf("count" to "2")))

        executor.execute(actions) { it.type != "toggle_flashlight" }

        assertEquals(listOf("cycle_ringer_mode", "cycle_ringer_mode", "cycle_ringer_mode"), ran)
    }

    @Test fun repeatPreviousIsSkippedWhenItsTargetIsAlsoSkipped() = runBlocking {
        val ran = mutableListOf<String>()
        val executor = ScriptActionExecutor { action -> ran.add(action.type); true }
        val actions = listOf(ScriptAction("cycle_ringer_mode"), ScriptAction("repeat_previous", mapOf("count" to "2")))

        val results = executor.execute(actions) { it.type != "cycle_ringer_mode" }

        assertEquals(emptyList<String>(), ran)
        assertEquals(listOf(false, false), results)
    }
}
