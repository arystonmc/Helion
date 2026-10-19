package com.aryston.helion.render.graph;

import com.aryston.helion.render.atmosphere.PhysicalSkySettings;
import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.lighting.DeferredLightingSettings;
import com.aryston.helion.render.lighting.DynamicLightSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.shadow.ShadowSettings;
import com.aryston.helion.render.temporal.TemporalSettings;

public record RenderSettings(
    AmbientOcclusionSettings ambientOcclusion,
    ImageSettings image,
    GeometryBufferSettings geometry,
    DeferredLightingSettings lighting,
    TemporalSettings temporal,
    PhysicalSkySettings sky,
    ShadowSettings shadows,
    DynamicLightSettings dynamicLights
) {
    public static final RenderSettings OFF = new RenderSettings(
        AmbientOcclusionSettings.DISABLED,
        ImageSettings.FOUNDATION,
        GeometryBufferSettings.DISABLED,
        DeferredLightingSettings.DISABLED,
        TemporalSettings.DISABLED,
        PhysicalSkySettings.DISABLED,
        ShadowSettings.DISABLED,
        DynamicLightSettings.DISABLED
    );

    public RenderSettings screenOnly() {
        return new RenderSettings(
            AmbientOcclusionSettings.DISABLED,
            image.withoutMotionBlur(),
            GeometryBufferSettings.DISABLED,
            DeferredLightingSettings.DISABLED,
            TemporalSettings.DISABLED,
            PhysicalSkySettings.DISABLED,
            ShadowSettings.DISABLED,
            DynamicLightSettings.DISABLED
        );
    }

    public RenderSettings foundation() {
        return new RenderSettings(
            AmbientOcclusionSettings.DISABLED,
            ImageSettings.FOUNDATION,
            geometry.withoutView(),
            DeferredLightingSettings.DISABLED,
            TemporalSettings.DISABLED,
            PhysicalSkySettings.DISABLED,
            ShadowSettings.DISABLED,
            DynamicLightSettings.DISABLED
        );
    }
}
