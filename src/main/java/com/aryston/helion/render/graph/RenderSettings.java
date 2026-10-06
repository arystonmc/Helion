package com.aryston.helion.render.graph;

import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.lighting.DeferredLightingSettings;
import com.aryston.helion.render.post.ImageSettings;

public record RenderSettings(
    AmbientOcclusionSettings ambientOcclusion,
    ImageSettings image,
    GeometryBufferSettings geometry,
    DeferredLightingSettings lighting
) {
    public static final RenderSettings OFF = new RenderSettings(
        AmbientOcclusionSettings.DISABLED, ImageSettings.FOUNDATION, GeometryBufferSettings.DISABLED, DeferredLightingSettings.DISABLED
    );

    public RenderSettings foundation() {
        return new RenderSettings(
            AmbientOcclusionSettings.DISABLED, ImageSettings.FOUNDATION, geometry.withoutView(), DeferredLightingSettings.DISABLED
        );
    }
}
