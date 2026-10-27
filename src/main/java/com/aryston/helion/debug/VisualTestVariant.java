package com.aryston.helion.debug;

import com.aryston.helion.render.atmosphere.PhysicalSkySettings;
import com.aryston.helion.render.clouds.CloudQuality;
import com.aryston.helion.render.clouds.CloudSettings;
import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.post.BloomSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.rtx.RayTracingSettings;
import com.aryston.helion.render.post.MotionBlurSettings;
import com.aryston.helion.render.post.SharpeningSettings;
import com.aryston.helion.render.post.ToneMapper;
import com.aryston.helion.render.temporal.RenderScale;
import com.aryston.helion.render.temporal.TemporalSettings;
import com.aryston.helion.render.water.ReflectionSettings;
import java.util.Locale;

enum VisualTestVariant {
    VANILLA(false, ToneMapper.NEUTRAL, false),
    NONE(true, ToneMapper.NONE, false),
    NEUTRAL(true, ToneMapper.NEUTRAL, false),
    FILMIC(true, ToneMapper.FILMIC, false),
    BLOOM_ONLY(true, ToneMapper.NEUTRAL, true),
    TEMPORAL(true, ToneMapper.NEUTRAL, false),
    PHYSICAL_SKY(true, ToneMapper.NEUTRAL, false),
    SCREEN_REFLECTIONS(true, ToneMapper.NEUTRAL, false),
    HYBRID_REFLECTIONS(true, ToneMapper.NEUTRAL, false),
    PARITY(true, ToneMapper.NEUTRAL, false),
    PATH_TRACING(true, ToneMapper.NEUTRAL, false);

    private static final CloudSettings VOLUMETRIC_CLOUDS = new CloudSettings(true, CloudQuality.MEDIUM);

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
        if (showsReflections()) {
            return new RenderSettings(
                ImageSettings.FOUNDATION,
                new GeometryBufferSettings(true, GeometryBufferView.NONE),
                TemporalSettings.DISABLED,
                PhysicalSkySettings.enabledWithDefaults(),
                VOLUMETRIC_CLOUDS,
                this == SCREEN_REFLECTIONS ? RayTracingSettings.DISABLED : settings.rayTracing(),
                new ReflectionSettings(true, settings.reflections().rainWetness(), settings.reflections().realisticWater())
            );
        }
        if (this == TEMPORAL) {
            return new RenderSettings(
                ImageSettings.FOUNDATION,
                settings.geometry().withoutView(),
                new TemporalSettings(true, RenderScale.NATIVE),
                PhysicalSkySettings.DISABLED,
                CloudSettings.DISABLED,
                RayTracingSettings.DISABLED,
                ReflectionSettings.DISABLED
            );
        }
        if (this == PHYSICAL_SKY) {
            return new RenderSettings(
                ImageSettings.FOUNDATION,
                settings.geometry().withoutView(),
                TemporalSettings.DISABLED,
                PhysicalSkySettings.enabledWithDefaults(),
                VOLUMETRIC_CLOUDS,
                RayTracingSettings.DISABLED,
                ReflectionSettings.DISABLED
            );
        }
        boolean pathTracing = this == PATH_TRACING;
        BloomSettings bloom = new BloomSettings(
            true, (float) BloomSettings.DEFAULT_INTENSITY, (float) BloomSettings.DEFAULT_THRESHOLD, bloomOnly
        );
        return new RenderSettings(
            new ImageSettings(
                toneMapper, ImageSettings.DEFAULT_EXPOSURE, settings.image().autoExposure(), true, bloom, SharpeningSettings.DISABLED, MotionBlurSettings.DISABLED,
                settings.image().colorGrade(), settings.image().depthOfField(), settings.image().edgeSmoothing()
            ),
            pathTracing ? new GeometryBufferSettings(true, GeometryBufferView.NONE) : settings.geometry(),
            settings.temporal(),
            settings.sky(),
            settings.clouds(),
            pathTracing ? new RayTracingSettings(true, settings.rayTracing().waterReflections(), true) : settings.rayTracing(),
            settings.reflections()
        );
    }

    private boolean showsReflections() {
        return this == SCREEN_REFLECTIONS || this == HYBRID_REFLECTIONS;
    }

    boolean comparesWithVanilla() {
        return this == PARITY;
    }

    String fileName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
