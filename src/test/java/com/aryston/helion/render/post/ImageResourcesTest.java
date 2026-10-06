package com.aryston.helion.render.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImageResourcesTest {
    private static final float TOLERANCE = 1.0e-6F;
    private static final float THRESHOLD = 1.0F;
    private static final float DARK_SCALE = 0.5F;
    private static final float DAYLIGHT_SCALE = 8.0F;
    private static final float CAVE = 0.0F;
    private static final float DUSK = 0.5F;
    private static final float NOON = 1.0F;
    private static final BloomSettings BLOOM = new BloomSettings(true, (float) BloomSettings.DEFAULT_INTENSITY, THRESHOLD, false);

    @Test
    void thresholdIsHalvedInDarkness() {
        assertEquals(THRESHOLD * DARK_SCALE, ImageResources.bloomThreshold(BLOOM, CAVE), TOLERANCE);
    }

    @Test
    void thresholdRisesEightfoldInFullDaylight() {
        assertEquals(THRESHOLD * DAYLIGHT_SCALE, ImageResources.bloomThreshold(BLOOM, NOON), TOLERANCE);
    }

    @Test
    void thresholdGrowsWithAmbientLight() {
        float cave = ImageResources.bloomThreshold(BLOOM, CAVE);
        float dusk = ImageResources.bloomThreshold(BLOOM, DUSK);
        float noon = ImageResources.bloomThreshold(BLOOM, NOON);

        assertTrue(cave < dusk);
        assertTrue(dusk < noon);
    }
}
