package com.aryston.helion.render.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EyeAdaptationTest {
    private static final float TOLERANCE = 1.0e-4F;
    private static final long FRAME_NANOS = 16_666_667L;
    private static final long SECOND_NANOS = 1_000_000_000L;

    @Test
    void aSceneAtTheTargetKeepsTheVanillaBrightness() {
        assertEquals(0.0F, EyeAdaptation.targetExposure(EyeAdaptation.TARGET_LOG2_LUMINANCE), TOLERANCE);
    }

    @Test
    void theExposureStaysWithinItsLimits() {
        assertEquals(EyeAdaptation.MAX_EXPOSURE, EyeAdaptation.targetExposure(EyeAdaptation.TARGET_LOG2_LUMINANCE - 10.0F), TOLERANCE);
        assertEquals(EyeAdaptation.MIN_EXPOSURE, EyeAdaptation.targetExposure(EyeAdaptation.TARGET_LOG2_LUMINANCE + 10.0F), TOLERANCE);
    }

    @Test
    void darkScenesAdaptOnlyPartway() {
        assertEquals(1.0F, EyeAdaptation.targetExposure(EyeAdaptation.TARGET_LOG2_LUMINANCE - 2.0F), TOLERANCE);
    }

    @Test
    void ordinaryDaylightIsNeverDarkened() {
        assertEquals(0.0F, EyeAdaptation.targetExposure(EyeAdaptation.DARKEN_FROM_LOG2_LUMINANCE - 0.5F), TOLERANCE);
        assertEquals(-0.5F, EyeAdaptation.targetExposure(EyeAdaptation.DARKEN_FROM_LOG2_LUMINANCE + 0.5F), TOLERANCE);
    }

    @Test
    void theFirstMeasurementIsAppliedAtOnce() {
        EyeAdaptation adaptation = new EyeAdaptation();
        adaptation.update(EyeAdaptation.TARGET_LOG2_LUMINANCE - 1.0F, 0L);

        assertEquals(0.5F, adaptation.exposure(), TOLERANCE);
    }

    @Test
    void theEyeAdaptsGraduallyAndDarkensFasterThanItBrightens() {
        EyeAdaptation brightening = adaptedAfterOneFrame(EyeAdaptation.TARGET_LOG2_LUMINANCE, EyeAdaptation.TARGET_LOG2_LUMINANCE - 1.0F);
        EyeAdaptation darkening = adaptedAfterOneFrame(EyeAdaptation.TARGET_LOG2_LUMINANCE - 1.0F, EyeAdaptation.TARGET_LOG2_LUMINANCE);

        assertTrue(brightening.exposure() > 0.0F && brightening.exposure() < EyeAdaptation.MAX_EXPOSURE);
        assertTrue(EyeAdaptation.MAX_EXPOSURE - darkening.exposure() > brightening.exposure());
    }

    @Test
    void aLongPauseCountsAsAShortStep() {
        EyeAdaptation adaptation = new EyeAdaptation();
        adaptation.update(EyeAdaptation.TARGET_LOG2_LUMINANCE, 0L);
        adaptation.update(EyeAdaptation.TARGET_LOG2_LUMINANCE - 1.0F, 10L * SECOND_NANOS);

        assertTrue(adaptation.exposure() < EyeAdaptation.MAX_EXPOSURE * 0.5F);
    }

    private static EyeAdaptation adaptedAfterOneFrame(float start, float metered) {
        EyeAdaptation adaptation = new EyeAdaptation();
        adaptation.update(start, 0L);
        adaptation.update(metered, FRAME_NANOS);
        return adaptation;
    }
}
