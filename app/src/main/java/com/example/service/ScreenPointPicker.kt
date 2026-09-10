package com.example.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PointPickMode { SINGLE, TWO_POINTS }

/** Which two-point gesture the overlay is aiming for, so it can draw the right connector between A
 * and B — a swipe points once from A to B, a pinch converges or diverges depending on direction.
 * Meaningless for [PointPickMode.SINGLE]. */
enum class TwoPointGesture { NONE, SWIPE, PINCH_IN, PINCH_OUT }

/** Absolute screen pixels, matching what `input tap` and GestureDescription consume. */
data class ScreenPoint(val x: Int, val y: Int)

data class PickedPoints(val a: ScreenPoint, val b: ScreenPoint?)

/**
 * The channel between the overlay service and the action configuration sheet. The overlay runs in
 * its own window while the app is backgrounded, so a plain callback would not survive the trip —
 * the picked points are parked here until the sheet reads them once.
 */
object ScreenPointPicker {

    private val _activeRequest = MutableStateFlow<PointPickMode?>(null)
    val activeRequest: StateFlow<PointPickMode?> = _activeRequest.asStateFlow()

    private val _result = MutableStateFlow<PickedPoints?>(null)
    val result: StateFlow<PickedPoints?> = _result.asStateFlow()

    // The sheet's Direction chip (pinch in/out) stays reachable while the overlay is up — it is a
    // non-modal window and the chip sits outside it — so the connector's arrows must track live
    // changes rather than freezing whatever direction was selected when the pick started.
    private val _gesture = MutableStateFlow(TwoPointGesture.NONE)
    val gesture: StateFlow<TwoPointGesture> = _gesture.asStateFlow()

    /** Marks a pick as in flight and drops any previous, now-stale result. */
    fun request(mode: PointPickMode) {
        _result.value = null
        _activeRequest.value = mode
    }

    /** Updates the connector's gesture without restarting the pick, so changing the Direction chip
     * while the overlay is already open redraws its arrows immediately. */
    fun setGesture(gesture: TwoPointGesture) {
        _gesture.value = gesture
    }

    fun submit(points: PickedPoints) {
        _activeRequest.value = null
        _result.value = points
    }

    /** Reads the result exactly once, so reopening the sheet cannot re-apply an old pick. */
    fun consumeResult(): PickedPoints? {
        val value = _result.value
        _result.value = null
        return value
    }

    fun cancel() {
        _activeRequest.value = null
        _result.value = null
    }

    /** Starts the overlay. The caller must have checked `Settings.canDrawOverlays` first. */
    fun start(context: Context, mode: PointPickMode, gesture: TwoPointGesture = TwoPointGesture.NONE) {
        request(mode)
        setGesture(gesture)
        val intent = Intent(context.applicationContext, ScreenPointPickerService::class.java)
            .putExtra(ScreenPointPickerService.EXTRA_MODE, mode.name)
        context.applicationContext.startService(intent)
    }
}
