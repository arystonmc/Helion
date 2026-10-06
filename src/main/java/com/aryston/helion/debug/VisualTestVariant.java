package com.aryston.helion.debug;

import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.post.BloomSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.post.SharpeningSettings;
import com.aryston.helion.render.post.ToneMapper;
import java.util.Locale;

enum VisualTestVariant {
    VANILLA(false, ToneMapper.NEUTRAL, false),
    NONE(true, ToneMapper.NONE, false),
    NEUTRAL(true, ToneMapper.NEUTRAL, false),
    FILMIC(true, ToneMapper.FILMIC, false),
    BLOOM_ONLY(true, ToneMapper.NEUTRAL, true),
    PARITY(true, ToneMapper.NEUTRAL, false);

    private final boolean helion;
    private final ToneMapper toneMapper;
    private final boolean bloomOnly;

    VisualTestVariant(boolean helion, ToneMapper toneMapper, boolean bloomOnly) {
        this.helion = helion;
        this.toneMapper = toneMapper;
        this.bloomOnly = bloomOnly;
    }

    boolean helion() {
        return helion;
    }

    RenderSettings applyTo(RenderSettings settings) {
        BloomSettings bloom = new BloomSettings(
            true, (float) BloomSettings.DEFAULT_INTENSITY, (float) BloomSettings.DEFAULT_THRESHOLD, bloomOnly
        );
        return new RenderSettings(
            settings.ambientOcclusion(),
            new ImageSettings(toneMapper, ImageSettings.DEFAULT_EXPOSURE, true, bloom, SharpeningSettings.DISABLED),
            settings.geometry()
        );
    }

    boolean comparesWithVanilla() {
        return this == PARITY;
    }

    String fileName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
