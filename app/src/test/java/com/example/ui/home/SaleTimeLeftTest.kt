package com.example.ui.home

import android.icu.util.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaleTimeLeftTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test fun showsTheTwoLargestUnits() {
        assertEquals(listOf(6 to MeasureUnit.DAY, 4 to MeasureUnit.HOUR), saleTimeLeft(6 * day + 4 * hour + 30 * minute, 0))
        assertEquals(listOf(3 to MeasureUnit.HOUR, 5 to MeasureUnit.MINUTE), saleTimeLeft(3 * hour + 5 * minute, 0))
        assertEquals(listOf(1 to MeasureUnit.MINUTE), saleTimeLeft(20_000, 0))
    }

    @Test fun hidesOnceEnded() {
        assertNull(saleTimeLeft(1_000, 1_000))
    }
}
