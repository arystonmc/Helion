package com.aryston.helion.debug;

import com.aryston.helion.render.atmosphere.PhysicalSkySettings;
import com.aryston.helion.render.clouds.CloudQuality;
import com.aryston.helion.render.clouds.CloudSettings;
import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.lighting.DeferredLightingSettings;
import com.aryston.helion.render.lighting.DynamicLightSettings;
import com.aryston.helion.render.post.BloomSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.rtx.RayTracingSettings;
import com.aryston.helion.render.post.MotionBlurSettings;
import com.aryston.helion.render.post.SharpeningSettings;
import com.aryston.helion.render.post.ToneMapper;
import com.aryston.helion.render.shadow.ShadowQuality;
import com.aryston.helion.render.shadow.ShadowSettings;
import com.aryston.helion.render.temporal.RenderScale;
import com.aryston.helion.render.temporal.TemporalSettings;
import com.aryston.helion.render.voxel.VoxelLightingMode;
import com.aryston.helion.render.voxel.VoxelLightingQuality;
import com.aryston.helion.render.voxel.VoxelLightingSettings;
import java.util.Locale;

enum VisualTestVariant {
    VANILLA(false, ToneMapper.NEUTRAL, false),
    NONE(true, ToneMapper.NONE, false),
    NEUTRAL(true, ToneMapper.NEUTRAL, false),
    FILMIC(true, ToneMapper.FILMIC, false),
    BLOOM_ONLY(true, ToneMapper.NEUTRAL, true),
    LIGHTING(true, ToneMapper.NEUTRAL, false),
    LIGHT_ONLY(true, ToneMapper.NEUTRAL, false),
    TEMPORAL(true, ToneMapper.NEUTRAL, false),
    PHYSICAL_SKY(true, ToneMapper.NEUTRAL, false),
    SHADOWS(true, ToneMapper.NEUTRAL, false),
    SHADOW_LIGHT(true, ToneMapper.NEUTRAL, false),
    VOXEL_LIGHT(true, ToneMapper.NEUTRAL, false),
    VOXEL_LIGHT_LOW(true, ToneMapper.NEUTRAL, false),
    VOXEL_BOUNCE_OFF(true, ToneMapper.NEUTRAL, false),
    PARITY(true, ToneMapper.NEUTRAL, false);

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
        if (showsDeferredLighting()) {
            return new RenderSettings(
                AmbientOcclusionSettings.DISABLED,
                ImageSettings.FOUNDATION,
                new GeometryBufferSettings(true, GeometryBufferView.NONE),
                new DeferredLightingSettings(
                    true,
                    DeferredLightingSettings.DEFAULT_BLOCK_LIGHT_INTENSITY,
                    DeferredLightingSettings.DEFAULT_SKY_LIGHT_INTENSITY,
                    this == LIGHT_ONLY || this == SHADOW_LIGHT
                ),
                TemporalSettings.DISABLED,
                showsShadows() ? PhysicalSkySettings.enabledWithDefaults() : PhysicalSkySettings.DISABLED,
                showsShadows() ? new ShadowSettings(true, ShadowQuality.MEDIUM) : ShadowSettings.DISABLED,
                DynamicLightSettings.DISABLED,
                showsShadows() ? VOLUMETRIC_CLOUDS : CloudSettings.DISABLED,
                voxelLighting(),
                settings.rayTracing()
            );
        }
        if (this == TEMPORAL) {
            return new RenderSettings(
                AmbientOcclusionSettings.DISABLED,
                ImageSettings.FOUNDATION,
                settings.geometry().withoutView(),
                DeferredLightingSettings.DISABLED,
                new TemporalSettings(true, RenderScale.NATIVE),
                PhysicalSkySettings.DISABLED,
                ShadowSettings.DISABLED,
                DynamicLightSettings.DISABLED,
                CloudSettings.DISABLED,
                VoxelLightingSettings.DISABLED,
                RayTracingSettings.DISABLED
            );
        }
        if (this == PHYSICAL_SKY) {
            return new RenderSettings(
                AmbientOcclusionSettings.DISABLED,
                ImageSettings.FOUNDATION,
                settings.geometry().withoutView(),
                DeferredLightingSettings.DISABLED,
                TemporalSettings.DISABLED,
                PhysicalSkySettings.enabledWithDefaults(),
                ShadowSettings.DISABLED,
                DynamicLightSettings.DISABLED,
                VOLUMETRIC_CLOUDS,
                VoxelLightingSettings.DISABLED,
                RayTracingSettings.DISABLED
            );
        }
        BloomSettings bloom = new BloomSettings(
            true, (float) BloomSettings.DEFAULT_INTENSITY, (float) BloomSettings.DEFAULT_THRESHOLD, bloomOnly
        );
        return new RenderSettings(
            settings.ambientOcclusion(),
            new ImageSettings(
                toneMapper, ImageSettings.DEFAULT_EXPOSURE, settings.image().autoExposure(), true, bloom, SharpeningSettings.DISABLED, MotionBlurSettings.DISABLED,
                settings.image().colorGrade(), settings.image().depthOfField(), settings.image().edgeSmoothing()
            ),
            settings.geometry(),
            settings.lighting(),
            settings.temporal(),
            settings.sky(),
            settings.shadows(),
            settings.dynamicLights(),
            settings.clouds(),
            settings.voxels(),
            settings.rayTracing()
        );
    }

    private boolean showsDeferredLighting() {
        return this == LIGHTING || this == LIGHT_ONLY || this == VOXEL_LIGHT || this == VOXEL_LIGHT_LOW || this == VOXEL_BOUNCE_OFF || showsShadows();
    }

    private VoxelLightingSettings voxelLighting() {
        if (this == VOXEL_LIGHT) {
            return new VoxelLightingSettings(VoxelLightingMode.SHADER, VoxelLightingQuality.MEDIUM, true);
        }
        if (this == VOXEL_LIGHT_LOW) {
            return new VoxelLightingSettings(VoxelLightingMode.SHADER, VoxelLightingQuality.LOW, true);
        }
        if (this == VOXEL_BOUNCE_OFF) {
            return new VoxelLightingSettings(VoxelLightingMode.SHADER, VoxelLightingQuality.MEDIUM, false);
        }
        return VoxelLightingSettings.DISABLED;
    }

    private boolean showsShadows() {
        return this == SHADOWS || this == SHADOW_LIGHT;
    }

    boolean comparesWithVanilla() {
        return this == PARITY;
    }

    String fileName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
