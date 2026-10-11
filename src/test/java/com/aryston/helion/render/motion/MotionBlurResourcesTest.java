package com.aryston.helion.render.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MotionBlurResourcesTest {
    private static final float TOLERANCE = 1.0e-4F;
    private static final float STRENGTH = 0.5F;

    @Test
    void theBlurAtTheReferenceFrameRateIsTheStrength() {
        assertEquals(STRENGTH, MotionBlurResources.blurScale(STRENGTH, MotionBlurResources.REFERENCE_FRAME_SECONDS), TOLERANCE);
    }

    @Test
    void fasterFramesBlurLongerSoTheLookStaysTheSame() {
        float twiceAsFast = MotionBlurResources.REFERENCE_FRAME_SECONDS / 2.0F;

        assertEquals(STRENGTH * 2.0F, MotionBlurResources.blurScale(STRENGTH, twiceAsFast), TOLERANCE);
    }

    @Test
    void theFrameScaleStaysWithinItsLimits() {
        assertEquals(STRENGTH * MotionBlurResources.MAX_FRAME_SCALE, MotionBlurResources.blurScale(STRENGTH, 1.0e-6F), TOLERANCE);
        assertEquals(STRENGTH * MotionBlurResources.MIN_FRAME_SCALE, MotionBlurResources.blurScale(STRENGTH, 1.0F), TOLERANCE);
    }
}
