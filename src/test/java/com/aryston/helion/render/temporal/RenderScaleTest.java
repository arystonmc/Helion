package com.aryston.helion.render.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RenderScaleTest {
    private static final float TOLERANCE = 1.0e-3F;

    @Test
    void internalSizesFollowTheUpscalerRatios() {
        assertEquals(1920, RenderScale.NATIVE.internalSize(1920));
        assertEquals(1477, RenderScale.ULTRA_QUALITY.internalSize(1920));
        assertEquals(1280, RenderScale.QUALITY.internalSize(1920));
        assertEquals(1129, RenderScale.BALANCED.internalSize(1920));
        assertEquals(960, RenderScale.PERFORMANCE.internalSize(1920));
        assertEquals(1, RenderScale.PERFORMANCE.internalSize(1));
    }

    @Test
    void renderScaleNeedsTemporalAntiAliasing() {
        assertEquals(RenderScale.QUALITY, new TemporalSettings(true, RenderScale.QUALITY).effectiveScale());
        assertEquals(RenderScale.NATIVE, new TemporalSettings(false, RenderScale.QUALITY).effectiveScale());
    }

    @Test
    void frameSizeTellsWhetherTheFrameIsUpscaled() {
        FrameSize full = new FrameSize(1920, 1080, 1920, 1080);
        FrameSize quality = new FrameSize(1280, 720, 1920, 1080);

        assertFalse(full.upscales());
        assertTrue(quality.upscales());
        assertEquals(1.5F, quality.upscaleRatio(), TOLERANCE);
    }
}
