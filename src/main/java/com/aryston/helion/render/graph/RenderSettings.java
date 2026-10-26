package com.aryston.helion.render.graph;

import com.aryston.helion.render.atmosphere.PhysicalSkySettings;
import com.aryston.helion.render.clouds.CloudSettings;
import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.rtx.RayTracingSettings;
import com.aryston.helion.render.temporal.TemporalSettings;
import com.aryston.helion.render.water.ReflectionSettings;

public record RenderSettings(
    ImageSettings image,
    GeometryBufferSettings geometry,
    TemporalSettings temporal,
    PhysicalSkySettings sky,
    CloudSettings clouds,
    RayTracingSettings rayTracing,
    ReflectionSettings reflections
) {
    public static final RenderSettings OFF = new RenderSettings(
        ImageSettings.FOUNDATION,
        GeometryBufferSettings.DISABLED,
        TemporalSettings.DISABLED,
        PhysicalSkySettings.DISABLED,
        CloudSettings.DISABLED,
        RayTracingSettings.DISABLED,
        ReflectionSettings.DISABLED
    );

    public RenderSettings screenOnly() {
        return new RenderSettings(
            image.withoutMotionBlur(),
            GeometryBufferSettings.DISABLED,
            TemporalSettings.DISABLED,
            PhysicalSkySettings.DISABLED,
            CloudSettings.DISABLED,
            RayTracingSettings.DISABLED,
            ReflectionSettings.DISABLED
        );
    }

    public RenderSettings foundation() {
        return new RenderSettings(
            ImageSettings.FOUNDATION,
            geometry.withoutView(),
            TemporalSettings.DISABLED,
            PhysicalSkySettings.DISABLED,
            CloudSettings.DISABLED,
            RayTracingSettings.DISABLED,
            ReflectionSettings.DISABLED
        );
    }
}
