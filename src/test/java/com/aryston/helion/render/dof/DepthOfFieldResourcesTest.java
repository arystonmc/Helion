package com.aryston.helion.render.dof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DepthOfFieldResourcesTest {
    private static final float TOLERANCE = 1.0e-4F;
    private static final float FRAME_SECONDS = 1.0F / 60.0F;

    @Test
    void focusDoesNotMoveWithoutTime() {
        assertEquals(0.0F, DepthOfFieldResources.focusBlend(0.0F), TOLERANCE);
    }

    @Test
    void focusFollowsSmoothlyAndSettlesWithinASecond() {
        float oneFrame = DepthOfFieldResources.focusBlend(FRAME_SECONDS);

        assertTrue(oneFrame > 0.0F && oneFrame < 0.1F);
        assertTrue(DepthOfFieldResources.focusBlend(1.0F) > 0.95F);
    }
}
