package com.example.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenPointPickerTest {

    @After fun tearDown() = ScreenPointPicker.cancel()

    @Test fun requestingMarksTheModeActiveWithNoResultYet() {
        ScreenPointPicker.request(PointPickMode.TWO_POINTS)
        assertEquals(PointPickMode.TWO_POINTS, ScreenPointPicker.activeRequest.value)
        assertNull(ScreenPointPicker.result.value)
    }

    @Test fun submittingClearsTheRequestAndPublishesTheResult() {
        ScreenPointPicker.request(PointPickMode.SINGLE)
        ScreenPointPicker.submit(PickedPoints(ScreenPoint(540, 1180), null))
        assertNull(ScreenPointPicker.activeRequest.value)
        assertEquals(PickedPoints(ScreenPoint(540, 1180), null), ScreenPointPicker.result.value)
    }

    @Test fun aConsumedResultIsHandedOverExactlyOnce() {
        // Otherwise reopening the sheet would silently re-apply the previous pick.
        ScreenPointPicker.request(PointPickMode.SINGLE)
        ScreenPointPicker.submit(PickedPoints(ScreenPoint(1, 2), null))
        assertEquals(PickedPoints(ScreenPoint(1, 2), null), ScreenPointPicker.consumeResult())
        assertNull(ScreenPointPicker.consumeResult())
        assertNull(ScreenPointPicker.result.value)
    }

    @Test fun cancellingLeavesNeitherARequestNorAResult() {
        ScreenPointPicker.request(PointPickMode.TWO_POINTS)
        ScreenPointPicker.cancel()
        assertNull(ScreenPointPicker.activeRequest.value)
        assertNull(ScreenPointPicker.result.value)
    }

    @Test fun aStaleResultIsDiscardedWhenANewPickStarts() {
        ScreenPointPicker.request(PointPickMode.SINGLE)
        ScreenPointPicker.submit(PickedPoints(ScreenPoint(1, 2), null))
        ScreenPointPicker.request(PointPickMode.SINGLE)
        assertNull(ScreenPointPicker.result.value)
    }

    @Test fun cancellingProducesTheStateTheServiceFallsBackToWhenTheOverlayCannotAttach() {
        // ScreenPointPickerService.showOverlay() calls cancel() when addView throws (e.g.
        // SYSTEM_ALERT_WINDOW missing or revoked), so the sheet can fall back to manual X/Y
        // fields instead of the app crashing. That fallback is only correct if cancel() leaves
        // no stale request or result behind for the sheet to misread.
        ScreenPointPicker.request(PointPickMode.SINGLE)
        ScreenPointPicker.cancel()
        assertNull(ScreenPointPicker.activeRequest.value)
        assertNull(ScreenPointPicker.result.value)
    }
}
