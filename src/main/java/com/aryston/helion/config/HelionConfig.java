package com.aryston.helion.config;

import com.aryston.helion.render.atmosphere.PhysicalSkySettings;
import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.lighting.AmbientOcclusionAlgorithm;
import com.aryston.helion.render.lighting.AmbientOcclusionQuality;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.lighting.DeferredLightingSettings;
import com.aryston.helion.render.post.BloomSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.post.SharpeningSettings;
import com.aryston.helion.render.post.ToneMapper;
import com.aryston.helion.render.temporal.TemporalSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HelionConfig {
    private static final double MIN_STRENGTH = 0.0;
    private static final double MAX_STRENGTH = 2.0;
    private static final double MIN_RADIUS = 0.5;
    private static final double MAX_RADIUS = 4.0;
    private static final double MIN_EXPOSURE = -2.0;
    private static final double MAX_EXPOSURE = 2.0;
    private static final double MIN_BLOOM_INTENSITY = 0.0;
    private static final double MAX_BLOOM_INTENSITY = 1.0;
    private static final double MIN_BLOOM_THRESHOLD = 0.5;
    private static final double MAX_BLOOM_THRESHOLD = 3.0;
    private static final double MIN_SHARPENING_STRENGTH = 0.0;
    private static final double MAX_SHARPENING_STRENGTH = 1.0;
    private static final double MIN_BLOCK_LIGHT_INTENSITY = 0.5;
    private static final double MAX_BLOCK_LIGHT_INTENSITY = 4.0;
    private static final double MIN_SKY_LIGHT_INTENSITY = 0.5;
    private static final double MAX_SKY_LIGHT_INTENSITY = 2.0;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .translation("helion.configuration.enabled")
        .define("enabled", true);

    public static final ModConfigSpec.BooleanValue DEBUG_MODE = BUILDER
        .translation("helion.configuration.debugMode")
        .define("debugMode", false);

    private static final ModConfigSpec.BooleanValue AMBIENT_OCCLUSION_ENABLED;
    private static final ModConfigSpec.EnumValue<AmbientOcclusionAlgorithm> AMBIENT_OCCLUSION_ALGORITHM;
    private static final ModConfigSpec.EnumValue<AmbientOcclusionQuality> AMBIENT_OCCLUSION_QUALITY;
    private static final ModConfigSpec.DoubleValue AMBIENT_OCCLUSION_STRENGTH;
    private static final ModConfigSpec.DoubleValue AMBIENT_OCCLUSION_RADIUS;
    private static final ModConfigSpec.BooleanValue AMBIENT_OCCLUSION_DEBUG_VIEW;
    private static final ModConfigSpec.EnumValue<ToneMapper> TONE_MAPPER;
    private static final ModConfigSpec.DoubleValue EXPOSURE;
    private static final ModConfigSpec.BooleanValue DITHER;
    private static final ModConfigSpec.BooleanValue BLOOM_ENABLED;
    private static final ModConfigSpec.DoubleValue BLOOM_INTENSITY;
    private static final ModConfigSpec.DoubleValue BLOOM_THRESHOLD;
    private static final ModConfigSpec.BooleanValue BLOOM_DEBUG_VIEW;
    private static final ModConfigSpec.BooleanValue SHARPENING_ENABLED;
    private static final ModConfigSpec.DoubleValue SHARPENING_STRENGTH;
    private static final ModConfigSpec.BooleanValue GEOMETRY_BUFFER_ENABLED;
    private static final ModConfigSpec.EnumValue<GeometryBufferView> GEOMETRY_BUFFER_VIEW;
    private static final ModConfigSpec.BooleanValue LIGHTING_ENABLED;
    private static final ModConfigSpec.DoubleValue BLOCK_LIGHT_INTENSITY;
    private static final ModConfigSpec.DoubleValue SKY_LIGHT_INTENSITY;
    private static final ModConfigSpec.BooleanValue LIGHT_ONLY_VIEW;
    private static final ModConfigSpec.BooleanValue TEMPORAL_ENABLED;
    private static final ModConfigSpec.BooleanValue PHYSICAL_SKY_ENABLED;

    static {
        BUILDER.translation("helion.configuration.ambientOcclusion").push("ambientOcclusion");
        AMBIENT_OCCLUSION_ENABLED = BUILDER
            .translation("helion.configuration.ambientOcclusion.enabled")
            .define("enabled", true);
        AMBIENT_OCCLUSION_ALGORITHM = BUILDER
            .translation("helion.configuration.ambientOcclusion.algorithm")
            .defineEnum("algorithm", AmbientOcclusionAlgorithm.GTAO);
        AMBIENT_OCCLUSION_QUALITY = BUILDER
            .translation("helion.configuration.ambientOcclusion.quality")
            .defineEnum("quality", AmbientOcclusionQuality.MEDIUM);
        AMBIENT_OCCLUSION_STRENGTH = BUILDER
            .translation("helion.configuration.ambientOcclusion.strength")
            .defineInRange("strength", AmbientOcclusionSettings.DEFAULT_STRENGTH, MIN_STRENGTH, MAX_STRENGTH);
        AMBIENT_OCCLUSION_RADIUS = BUILDER
            .translation("helion.configuration.ambientOcclusion.radius")
            .defineInRange("radius", AmbientOcclusionSettings.DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS);
        AMBIENT_OCCLUSION_DEBUG_VIEW = BUILDER
            .translation("helion.configuration.ambientOcclusion.debugView")
            .define("debugView", false);
        BUILDER.pop();

        BUILDER.translation("helion.configuration.image").push("image");
        TONE_MAPPER = BUILDER
            .translation("helion.configuration.image.toneMapper")
            .defineEnum("toneMapper", ToneMapper.NEUTRAL);
        EXPOSURE = BUILDER
            .translation("helion.configuration.image.exposure")
            .defineInRange("exposure", ImageSettings.DEFAULT_EXPOSURE, MIN_EXPOSURE, MAX_EXPOSURE);
        DITHER = BUILDER
            .translation("helion.configuration.image.dither")
            .define("dither", true);
        BUILDER.translation("helion.configuration.image.bloom").push("bloom");
        BLOOM_ENABLED = BUILDER
            .translation("helion.configuration.image.bloom.enabled")
            .define("enabled", true);
        BLOOM_INTENSITY = BUILDER
            .translation("helion.configuration.image.bloom.intensity")
            .defineInRange("intensity", BloomSettings.DEFAULT_INTENSITY, MIN_BLOOM_INTENSITY, MAX_BLOOM_INTENSITY);
        BLOOM_THRESHOLD = BUILDER
            .translation("helion.configuration.image.bloom.threshold")
            .defineInRange("threshold", BloomSettings.DEFAULT_THRESHOLD, MIN_BLOOM_THRESHOLD, MAX_BLOOM_THRESHOLD);
        BLOOM_DEBUG_VIEW = BUILDER
            .translation("helion.configuration.image.bloom.debugView")
            .define("debugView", false);
        BUILDER.pop();
        BUILDER.translation("helion.configuration.image.sharpening").push("sharpening");
        SHARPENING_ENABLED = BUILDER
            .translation("helion.configuration.image.sharpening.enabled")
            .define("enabled", false);
        SHARPENING_STRENGTH = BUILDER
            .translation("helion.configuration.image.sharpening.strength")
            .defineInRange("strength", SharpeningSettings.DEFAULT_STRENGTH, MIN_SHARPENING_STRENGTH, MAX_SHARPENING_STRENGTH);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.translation("helion.configuration.geometryBuffer").push("geometryBuffer");
        GEOMETRY_BUFFER_ENABLED = BUILDER
            .translation("helion.configuration.geometryBuffer.enabled")
            .define("enabled", false);
        GEOMETRY_BUFFER_VIEW = BUILDER
            .translation("helion.configuration.geometryBuffer.view")
            .defineEnum("view", GeometryBufferView.NONE);
        BUILDER.pop();

        BUILDER.translation("helion.configuration.lighting").push("lighting");
        LIGHTING_ENABLED = BUILDER
            .translation("helion.configuration.lighting.enabled")
            .define("enabled", false);
        BLOCK_LIGHT_INTENSITY = BUILDER
            .translation("helion.configuration.lighting.blockLightIntensity")
            .defineInRange(
                "blockLightIntensity", DeferredLightingSettings.DEFAULT_BLOCK_LIGHT_INTENSITY, MIN_BLOCK_LIGHT_INTENSITY, MAX_BLOCK_LIGHT_INTENSITY
            );
        SKY_LIGHT_INTENSITY = BUILDER
            .translation("helion.configuration.lighting.skyLightIntensity")
            .defineInRange("skyLightIntensity", DeferredLightingSettings.DEFAULT_SKY_LIGHT_INTENSITY, MIN_SKY_LIGHT_INTENSITY, MAX_SKY_LIGHT_INTENSITY);
        LIGHT_ONLY_VIEW = BUILDER
            .translation("helion.configuration.lighting.lightOnlyView")
            .define("lightOnlyView", false);
        BUILDER.pop();

        BUILDER.translation("helion.configuration.temporalAntiAliasing").push("temporalAntiAliasing");
        TEMPORAL_ENABLED = BUILDER
            .translation("helion.configuration.temporalAntiAliasing.enabled")
            .define("enabled", false);
        BUILDER.pop();

        BUILDER.translation("helion.configuration.physicalSky").push("physicalSky");
        PHYSICAL_SKY_ENABLED = BUILDER
            .translation("helion.configuration.physicalSky.enabled")
            .define("enabled", false);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HelionConfig() {
    }

    public static RenderSettings renderSettings() {
        return new RenderSettings(
            ambientOcclusionSettings(),
            imageSettings(),
            geometryBufferSettings(),
            lightingSettings(),
            new TemporalSettings(TEMPORAL_ENABLED.getAsBoolean()),
            new PhysicalSkySettings(PHYSICAL_SKY_ENABLED.getAsBoolean())
        );
    }

    private static AmbientOcclusionSettings ambientOcclusionSettings() {
        return new AmbientOcclusionSettings(
            AMBIENT_OCCLUSION_ENABLED.getAsBoolean(),
            AMBIENT_OCCLUSION_ALGORITHM.get(),
            AMBIENT_OCCLUSION_QUALITY.get(),
            AMBIENT_OCCLUSION_STRENGTH.get().floatValue(),
            AMBIENT_OCCLUSION_RADIUS.get().floatValue(),
            AMBIENT_OCCLUSION_DEBUG_VIEW.getAsBoolean()
        );
    }

    private static GeometryBufferSettings geometryBufferSettings() {
        return new GeometryBufferSettings(GEOMETRY_BUFFER_ENABLED.getAsBoolean(), GEOMETRY_BUFFER_VIEW.get());
    }

    private static DeferredLightingSettings lightingSettings() {
        return new DeferredLightingSettings(
            LIGHTING_ENABLED.getAsBoolean(),
            BLOCK_LIGHT_INTENSITY.get().floatValue(),
            SKY_LIGHT_INTENSITY.get().floatValue(),
            LIGHT_ONLY_VIEW.getAsBoolean()
        );
    }

    private static ImageSettings imageSettings() {
        return new ImageSettings(
            TONE_MAPPER.get(),
            EXPOSURE.get().floatValue(),
            DITHER.getAsBoolean(),
            new BloomSettings(
                BLOOM_ENABLED.getAsBoolean(),
                BLOOM_INTENSITY.get().floatValue(),
                BLOOM_THRESHOLD.get().floatValue(),
                BLOOM_DEBUG_VIEW.getAsBoolean()
            ),
            new SharpeningSettings(SHARPENING_ENABLED.getAsBoolean(), SHARPENING_STRENGTH.get().floatValue())
        );
    }
}
