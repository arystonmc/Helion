package com.aryston.helion.render.post;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImageSettingsTest {
    private static final float BRIGHTER = 1.0F;
    private static final BloomSettings BLOOM_ON = new BloomSettings(
        true, (float) BloomSettings.DEFAULT_INTENSITY, (float) BloomSettings.DEFAULT_THRESHOLD, false
    );
    private static final SharpeningSettings SHARPENING_ON = new SharpeningSettings(true, (float) SharpeningSettings.DEFAULT_STRENGTH);

    @Test
    void neutralWithoutEffectsSkipsTheComposite() {
        assertFalse(settings(ToneMapper.NEUTRAL, ImageSettings.DEFAULT_EXPOSURE, BloomSettings.DISABLED, SharpeningSettings.DISABLED)
            .needsComposite());
    }

    @Test
    void noneWithoutEffectsSkipsTheComposite() {
        assertFalse(settings(ToneMapper.NONE, ImageSettings.DEFAULT_EXPOSURE, BloomSettings.DISABLED, SharpeningSettings.DISABLED)
            .needsComposite());
    }

    @Test
    void bloomNeedsTheComposite() {
        assertTrue(settings(ToneMapper.NEUTRAL, ImageSettings.DEFAULT_EXPOSURE, BLOOM_ON, SharpeningSettings.DISABLED)
            .needsComposite());
    }

    @Test
    void sharpeningNeedsTheComposite() {
        assertTrue(settings(ToneMapper.NEUTRAL, ImageSettings.DEFAULT_EXPOSURE, BloomSettings.DISABLED, SHARPENING_ON)
            .needsComposite());
    }

    @Test
    void exposureNeedsTheComposite() {
        assertTrue(settings(ToneMapper.NEUTRAL, BRIGHTER, BloomSettings.DISABLED, SharpeningSettings.DISABLED)
            .needsComposite());
    }

    @Test
    void filmicNeedsTheComposite() {
        assertTrue(settings(ToneMapper.FILMIC, ImageSettings.DEFAULT_EXPOSURE, BloomSettings.DISABLED, SharpeningSettings.DISABLED)
            .needsComposite());
    }

    private static ImageSettings settings(ToneMapper toneMapper, float exposure, BloomSettings bloom, SharpeningSettings sharpening) {
        return new ImageSettings(toneMapper, exposure, true, bloom, sharpening);
    }
}
