package com.example.bridge;

/** Pure setting/slider mapping shared by the app, detached bridge and Pro capsule. */
public final class BrightnessScale {
    public static final int LEGACY_MAXIMUM = 255;
    // Preserve the old +26 feel at the editor's default (128), now evenly in slider space.
    public static final double SLIDER_STEP = gamma(154.0 / 255) - gamma(128.0 / 255);

    public final int maximum;
    public final boolean settingIsPerceptual;

    public BrightnessScale(int maximum, boolean settingIsPerceptual) {
        if (maximum <= 0) throw new IllegalArgumentException("Non-positive brightness maximum");
        this.maximum = maximum;
        this.settingIsPerceptual = settingIsPerceptual;
    }

    /** OEM setting maximum wins over the often-unmodified Android 255 resource. */
    public static BrightnessScale resolve(int oplusMaximum, int resourceMaximum, int powerMaximum) {
        if (oplusMaximum > LEGACY_MAXIMUM) return new BrightnessScale(oplusMaximum, true);
        int maximum = Math.max(resourceMaximum, powerMaximum);
        return new BrightnessScale(maximum > 0 ? maximum : LEGACY_MAXIMUM, false);
    }

    /** Saved editor values are 0..255 setting units, NOT percentages or gamma slider units. */
    public int fromLegacy(int value) {
        return (int) Math.round(Math.max(0, Math.min(LEGACY_MAXIMUM, value))
                / (double) LEGACY_MAXIMUM * maximum);
    }

    public float sliderFraction(int raw) {
        double fraction = Math.max(0, Math.min(maximum, raw)) / (double) maximum;
        return (float) (settingIsPerceptual ? fraction : gamma(fraction));
    }

    public int fromSlider(double fraction) {
        fraction = Math.max(0, Math.min(1, fraction));
        double linear = settingIsPerceptual ? fraction : linear(fraction);
        return (int) Math.round(linear * maximum);
    }

    /** An unexplained out-of-range reading must not turn an increase into a large decrease. */
    public boolean contains(int raw) {
        return raw >= 0 && raw <= maximum;
    }

    public int step(int current, int direction) {
        int next = fromSlider(sliderFraction(current) + Integer.signum(direction) * SLIDER_STEP);
        // At very low AOSP levels integer quantization can otherwise swallow a small step.
        if (direction > 0 && current < maximum) next = Math.max(current + 1, next);
        if (direction < 0 && current > 0) next = Math.min(current - 1, next);
        return Math.max(0, Math.min(maximum, next));
    }

    private static double gamma(double fraction) {
        double scaled = fraction * 12;
        return Math.max(0, Math.min(1, scaled <= 1 ? 0.5 * Math.sqrt(scaled)
                : 0.17883277 * Math.log(scaled - 0.28466892) + 0.55991073));
    }

    private static double linear(double fraction) {
        double scaled = fraction <= 0.5 ? Math.pow(fraction / 0.5, 2)
                : Math.exp((fraction - 0.55991073) / 0.17883277) + 0.28466892;
        return Math.max(0, Math.min(1, scaled / 12));
    }
}
