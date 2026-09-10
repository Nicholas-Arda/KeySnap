package com.example.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KeyMappingRepositoryTest {
    @Test
    fun missingTimingPreferencesUseDefaultsAndPreserveLegacyValues() {
        val prefs = testPreferences()
        prefs.edit()
            .putBoolean("consume_original", false)
            .putBoolean("vibrate", false)
            .putInt("target_key_code", 24)
            .commit()

        val config = KeyMappingRepository(prefs).config.value

        assertFalse(config.consumeOriginalEvent)
        assertFalse(config.vibrateOnTrigger)
        assertEquals(24, config.targetKeyCode)
        assertEquals(300L, config.doublePressWindowMs)
        assertEquals(500L, config.longPressThresholdMs)
        assertEquals(60L, config.hapticDurationMs)
    }

    @Test
    fun timingSettersPersistNormalizedValues() {
        val prefs = testPreferences()
        val repository = KeyMappingRepository(prefs)

        repository.setDoublePressWindowMs(900)
        repository.setLongPressThresholdMs(749)
        repository.setHapticDurationMs(64)
        val restored = KeyMappingRepository(prefs).config.value

        assertEquals(750L, restored.doublePressWindowMs)
        assertEquals(700L, restored.longPressThresholdMs)
        assertEquals(60L, restored.hapticDurationMs)
        assertTrue(prefs.contains("double_press_window_ms"))
        assertTrue(prefs.contains("long_press_threshold_ms"))
        assertTrue(prefs.contains("haptic_duration_ms"))
    }

    private fun testPreferences() = RuntimeEnvironment
        .getApplication()
        .getSharedPreferences("timing_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
}
