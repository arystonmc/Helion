package com.aryston.helion.render.graph;

import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.lighting.DeferredLightingSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.temporal.TemporalSettings;

public record RenderSettings(
    AmbientOcclusionSettings ambientOcclusion,
    ImageSettings image,
    GeometryBufferSettings geometry,
    DeferredLightingSettings lighting,
    TemporalSettings temporal
) {
    public static final RenderSettings OFF = new RenderSettings(
        AmbientOcclusionSettings.DISABLED,
        ImageSettings.FOUNDATION,
        GeometryBufferSettings.DISABLED,
        DeferredLightingSettings.DISABLED,
        TemporalSettings.DISABLED
    );

    public RenderSettings foundation() {
        return new RenderSettings(
            AmbientOcclusionSettings.DISABLED,
            ImageSettings.FOUNDATION,
            geometry.withoutView(),
            DeferredLightingSettings.DISABLED,
            TemporalSettings.DISABLED
        );
    }
}
