package com.example.data

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KeyModelsTest {

    private val res = RuntimeEnvironment.getApplication().resources

    @Test
    fun defaultMappingConfigStartsReadyForDoublePress() {
        val config = KeyMappingConfig()

        assertTrue(config.isEnabled)
        assertNull(config.targetKeyCode)
        assertEquals(TriggerPressType.DOUBLE_PRESS, config.pressType)
        assertTrue(config.consumeOriginalEvent)
        assertTrue(config.vibrateOnTrigger)
        assertEquals(300L, config.doublePressWindowMs)
        assertEquals(500L, config.longPressThresholdMs)
        assertEquals(60L, config.hapticDurationMs)
    }

    @Test
    fun timingValuesSnapToSupportedRanges() {
        assertEquals(150L, normalizeTimingValue(100L, TriggerTimingSpecs.doublePressWindow))
        assertEquals(350L, normalizeTimingValue(374L, TriggerTimingSpecs.doublePressWindow))
        assertEquals(750L, normalizeTimingValue(900L, TriggerTimingSpecs.doublePressWindow))
        assertEquals(500L, normalizeTimingValue(500L, TriggerTimingSpecs.longPressThreshold))
        assertEquals(60L, normalizeTimingValue(64L, TriggerTimingSpecs.hapticDuration))
    }

    @Test
    fun triggerPressTypesExposeRequiredLabels() {
        TriggerPressType.entries.forEach { type ->
            assertTrue(res.getString(type.titleRes).isNotBlank())
            assertTrue(res.getString(type.shortBadgeRes).isNotBlank())
            assertTrue(res.getString(type.descriptionRes).isNotBlank())
        }
        assertEquals("1x", res.getString(TriggerPressType.SINGLE_PRESS.shortBadgeRes))
        assertEquals("2x", res.getString(TriggerPressType.DOUBLE_PRESS.shortBadgeRes))
        assertEquals("Hold", res.getString(TriggerPressType.LONG_PRESS.shortBadgeRes))
    }

    @Test
    fun knownPhysicalKeysHaveReadableNames() {
        assertEquals("Volume Down", getReadableKeyName(res, KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals("Volume Up", getReadableKeyName(res, KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals("Power", getReadableKeyName(res, KeyEvent.KEYCODE_POWER))
    }

    @Test
    fun unknownPhysicalKeysKeepTheirNumericCode() {
        assertEquals("Key code #999", getReadableKeyName(res, 999))
    }

    @Test
    fun unlabelledKeysAreDetectedByCodeNotByName() {
        assertTrue(isUnlabelledKey(999))
        assertFalse(isUnlabelledKey(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun namesGeneratedByOlderTurkishBuildsStillCountAsGenerated() {
        // Saved key names persist across upgrades, so a key auto-named before the copy was
        // translated must not be mistaken for a name the user typed.
        assertTrue(isGeneratedKeyName("Key code #742"))
        assertTrue(isGeneratedKeyName("Tuş Kodu #742"))
        assertFalse(isGeneratedKeyName("Camera button"))
    }

    @Test
    fun physicalKeyOptionDefaultsToNonHijackedKey() {
        val option = PhysicalKeyOption(42, "Test key", "Test")

        assertFalse(option.requiresHijack)
        assertEquals(42, option.keyCode)
    }
}
