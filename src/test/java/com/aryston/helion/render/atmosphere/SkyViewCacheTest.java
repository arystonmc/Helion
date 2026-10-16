package com.aryston.helion.render.atmosphere;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyViewCacheTest {
    private static final float NOON_SUN = 0.9F;
    private static final float GROUND = PhysicalSkyResources.viewHeight(64.0F);

    @Test
    void aNewTableIsNeverCurrent() {
        assertFalse(new SkyViewCache().isCurrent(NOON_SUN, GROUND));
    }

    @Test
    void aWrittenTableStaysCurrentWhileTheLightAndTheCameraBarelyMove() {
        SkyViewCache cache = new SkyViewCache();
        cache.markWritten(NOON_SUN, GROUND);

        assertTrue(cache.isCurrent(NOON_SUN + SkyViewCache.LIGHT_HEIGHT_TOLERANCE * 0.5F, GROUND));
        assertTrue(cache.isCurrent(NOON_SUN, PhysicalSkyResources.viewHeight(64.5F)));
    }

    @Test
    void theTableIsRedrawnOnceTheLightOrTheCameraMovedFarEnough() {
        SkyViewCache cache = new SkyViewCache();
        cache.markWritten(NOON_SUN, GROUND);

        assertFalse(cache.isCurrent(NOON_SUN - SkyViewCache.LIGHT_HEIGHT_TOLERANCE * 2.0F, GROUND));
        assertFalse(cache.isCurrent(NOON_SUN, PhysicalSkyResources.viewHeight(70.0F)));
    }

    @Test
    void invalidatingForgetsTheWrittenTable() {
        SkyViewCache cache = new SkyViewCache();
        cache.markWritten(NOON_SUN, GROUND);
        cache.invalidate();

        assertFalse(cache.isCurrent(NOON_SUN, GROUND));
    }
}
