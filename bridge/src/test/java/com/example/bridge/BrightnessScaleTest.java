package com.example.bridge;

import org.junit.Test;

import static org.junit.Assert.*;

/** No Android runtime: the exact mapping used by both UIDs and the capsule. */
public class BrightnessScaleTest {
    @Test public void legacyValuesKeepTheirSettingMeaningOn255Devices() {
        BrightnessScale scale = new BrightnessScale(255, false);
        for (int value = 0; value <= 255; value++) assertEquals(value, scale.fromLegacy(value));
        assertEquals(255, scale.fromLegacy(Integer.MAX_VALUE));
        assertEquals(0, scale.fromLegacy(Integer.MIN_VALUE));
        assertEquals(1606, new BrightnessScale(4095, false).fromLegacy(100)); // Not 100%.
    }

    @Test public void savedValuesScaleTo4095AndOddMaximumWithoutBitDepthAssumptions() {
        for (int maximum : new int[] {255, 4095, 8191, 16383, 1237}) {
            BrightnessScale scale = new BrightnessScale(maximum, false);
            assertEquals(0, scale.fromLegacy(0));
            assertEquals(maximum, scale.fromLegacy(255));
            assertEquals(128.0 / 255, scale.fromLegacy(128) / (double) maximum, 0.5 / maximum);
        }
    }

    @Test public void settingSliderRoundTripsAreMonotonicAcrossRangesAndCurves() {
        for (int maximum : new int[] {255, 4095, 1237}) {
            for (boolean perceptual : new boolean[] {false, true}) {
                BrightnessScale scale = new BrightnessScale(maximum, perceptual);
                float previous = -1;
                for (int raw = 0; raw <= maximum; raw++) {
                    float slider = scale.sliderFraction(raw);
                    assertTrue(slider >= previous);
                    assertEquals(raw, scale.fromSlider(slider));
                    previous = slider;
                }
                assertEquals(0, scale.fromSlider(-1));
                assertEquals(maximum, scale.fromSlider(2));
            }
        }
    }

    @Test public void aospUsesGammaButOplusSettingsAlreadyRepresentSliderPosition() {
        BrightnessScale aosp = new BrightnessScale(4095, false);
        assertEquals(0.5, aosp.sliderFraction(341), 0.001);
        assertTrue(aosp.sliderFraction(2102) > 0.87);
        BrightnessScale oplus = BrightnessScale.resolve(4095, 255, 255);
        // PLG110 Android 16: user set 100%, then approximately 50%, with auto mode off.
        assertEquals(1, oplus.sliderFraction(4095), 0);
        assertEquals(0.5133, oplus.sliderFraction(2102), 0.0001);
    }

    @Test public void stepRetainsOldMidrangeIncreaseAndAlwaysMakesProgress() {
        assertEquals(154, new BrightnessScale(255, false).step(128, 1));
        for (int maximum : new int[] {255, 4095, 1237}) {
            for (boolean perceptual : new boolean[] {false, true}) {
                BrightnessScale scale = new BrightnessScale(maximum, perceptual);
                assertEquals(maximum, scale.step(maximum, 1));
                assertEquals(0, scale.step(0, -1));
                for (int current = 0; current <= maximum; current++) {
                    if (current < maximum) assertTrue(scale.step(current, 1) > current);
                    if (current > 0) assertTrue(scale.step(current, -1) < current);
                    assertEquals(current, scale.step(current, 0));
                }
            }
        }
    }

    @Test public void stepsAreEvenInSliderSpaceExceptIntegerQuantizationAndEndpoints() {
        for (int maximum : new int[] {255, 4095, 1237}) {
            for (boolean perceptual : new boolean[] {false, true}) {
                BrightnessScale scale = new BrightnessScale(maximum, perceptual);
                for (double slider : new double[] {0.5, 0.65, 0.8, 0.95}) {
                    int current = scale.fromSlider(slider);
                    for (int direction : new int[] {-1, 1}) {
                        int next = scale.step(current, direction);
                        double expected = scale.sliderFraction(current) + direction * BrightnessScale.SLIDER_STEP;
                        double quantization = Math.max(
                                scale.sliderFraction(Math.min(maximum, next + 1)) - scale.sliderFraction(next),
                                scale.sliderFraction(next) - scale.sliderFraction(Math.max(0, next - 1)));
                        assertEquals(expected, scale.sliderFraction(next), quantization + 0.000001);
                    }
                }
            }
        }
    }

    @Test public void resolverPrefersOemThenFrameworkAndFallsBackTo255() {
        assertEquals(4095, BrightnessScale.resolve(4095, 255, 255).maximum);
        assertEquals(8191, BrightnessScale.resolve(0, 8191, 255).maximum);
        assertEquals(16383, BrightnessScale.resolve(0, 255, 16383).maximum);
        assertEquals(1237, BrightnessScale.resolve(0, 1237, 0).maximum);
        assertEquals(255, BrightnessScale.resolve(-1, 0, -1).maximum);
        assertFalse(BrightnessScale.resolve(0, 4095, 0).settingIsPerceptual);
    }

    @Test public void unknownOemRangeIsNotMistakenForA255DeviceAtFullBrightness() {
        BrightnessScale fallback = BrightnessScale.resolve(0, 0, 0);
        assertFalse(fallback.contains(2502));
        assertFalse(fallback.contains(-1));
        assertTrue(fallback.contains(255));
    }
}
