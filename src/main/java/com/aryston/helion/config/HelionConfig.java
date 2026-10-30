package com.aryston.helion.config;

import com.aryston.helion.render.atmosphere.PhysicalSkySettings;
import com.aryston.helion.render.atmosphere.StarDensity;
import com.aryston.helion.render.clouds.CloudQuality;
import com.aryston.helion.render.clouds.CloudSettings;
import com.aryston.helion.render.geometry.GeometryBufferSettings;
import com.aryston.helion.render.geometry.GeometryBufferView;
import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.post.BloomSettings;
import com.aryston.helion.render.post.ColorGrade;
import com.aryston.helion.render.post.DepthOfFieldSettings;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.post.MotionBlurSettings;
import com.aryston.helion.render.post.SharpeningSettings;
import com.aryston.helion.render.post.ToneMapper;
import com.aryston.helion.render.temporal.DynamicScaleSettings;
import com.aryston.helion.render.temporal.FrameRateTarget;
import com.aryston.helion.render.temporal.RenderScale;
import com.aryston.helion.render.temporal.TemporalSettings;
import com.aryston.helion.render.rtx.RayTracing;
import com.aryston.helion.render.rtx.RayTracingSettings;
import com.aryston.helion.render.water.ReflectionSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HelionConfig {
    static final double MIN_EXPOSURE = -2.0;
    static final double MAX_EXPOSURE = 2.0;
    static final double MIN_BLOOM_INTENSITY = 0.0;
    static final double MAX_BLOOM_INTENSITY = 1.0;
    static final double MIN_BLOOM_THRESHOLD = 0.5;
    static final double MAX_BLOOM_THRESHOLD = 3.0;
    static final double MIN_SHARPENING_STRENGTH = 0.0;
    static final double MAX_SHARPENING_STRENGTH = 1.0;
    static final double MIN_MOTION_BLUR_STRENGTH = 0.0;
    static final double MAX_MOTION_BLUR_STRENGTH = 1.0;
    static final double MIN_DEPTH_OF_FIELD_STRENGTH = 0.0;
    static final double MAX_DEPTH_OF_FIELD_STRENGTH = 1.0;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .translation("helion.configuration.enabled")
        .define("enabled", true);

    public static final ModConfigSpec.BooleanValue DEBUG_MODE = BUILDER
        .translation("helion.configuration.debugMode")
        .define("debugMode", false);

    static final ModConfigSpec.EnumValue<ToneMapper> TONE_MAPPER;
    static final ModConfigSpec.DoubleValue EXPOSURE;
    static final ModConfigSpec.BooleanValue AUTO_EXPOSURE;
    static final ModConfigSpec.EnumValue<ColorGrade> COLOR_GRADE;
    static final ModConfigSpec.BooleanValue EDGE_SMOOTHING;
    static final ModConfigSpec.BooleanValue DITHER;
    static final ModConfigSpec.BooleanValue BLOOM_ENABLED;
    static final ModConfigSpec.DoubleValue BLOOM_INTENSITY;
    static final ModConfigSpec.DoubleValue BLOOM_THRESHOLD;
    static final ModConfigSpec.BooleanValue BLOOM_DEBUG_VIEW;
    static final ModConfigSpec.BooleanValue SHARPENING_ENABLED;
    static final ModConfigSpec.DoubleValue SHARPENING_STRENGTH;
    static final ModConfigSpec.BooleanValue MOTION_BLUR_ENABLED;
    static final ModConfigSpec.DoubleValue MOTION_BLUR_STRENGTH;
    static final ModConfigSpec.BooleanValue DEPTH_OF_FIELD_ENABLED;
    static final ModConfigSpec.DoubleValue DEPTH_OF_FIELD_STRENGTH;
    static final ModConfigSpec.BooleanValue GEOMETRY_BUFFER_ENABLED;
    static final ModConfigSpec.EnumValue<GeometryBufferView> GEOMETRY_BUFFER_VIEW;
    static final ModConfigSpec.BooleanValue RAY_TRACING_ENABLED;
    static final ModConfigSpec.BooleanValue WATER_REFLECTIONS;
    static final ModConfigSpec.BooleanValue PATH_TRACING;
    static final ModConfigSpec.BooleanValue SCREEN_REFLECTIONS;
    static final ModConfigSpec.BooleanValue RAIN_WETNESS;
    static final ModConfigSpec.BooleanValue REALISTIC_WATER;
    static final ModConfigSpec.BooleanValue TEMPORAL_ENABLED;
    static final ModConfigSpec.EnumValue<RenderScale> RENDER_SCALE;
    static final ModConfigSpec.BooleanValue DYNAMIC_RENDER_SCALE;
    static final ModConfigSpec.EnumValue<FrameRateTarget> TARGET_FRAME_RATE;
    static final ModConfigSpec.BooleanValue PHYSICAL_SKY_ENABLED;
    static final ModConfigSpec.EnumValue<StarDensity> STAR_DENSITY;
    static final ModConfigSpec.BooleanValue MILKY_WAY;
    static final ModConfigSpec.BooleanValue LIGHT_SHAFTS;
    static final ModConfigSpec.BooleanValue CLOUDS_ENABLED;
    static final ModConfigSpec.EnumValue<CloudQuality> CLOUD_QUALITY;

    static {
        BUILDER.translation("helion.configuration.image").push("image");
        TONE_MAPPER = BUILDER
            .translation("helion.configuration.image.toneMapper")
            .defineEnum("toneMapper", ToneMapper.NEUTRAL);
        EXPOSURE = BUILDER
            .translation("helion.configuration.image.exposure")
            .defineInRange("exposure", ImageSettings.DEFAULT_EXPOSURE, MIN_EXPOSURE, MAX_EXPOSURE);
        AUTO_EXPOSURE = BUILDER
            .translation("helion.configuration.image.autoExposure")
            .define("autoExposure", false);
        COLOR_GRADE = BUILDER
            .translation("helion.configuration.image.colorGrade")
            .defineEnum("colorGrade", ColorGrade.NEUTRAL);
        EDGE_SMOOTHING = BUILDER
            .translation("helion.configuration.image.edgeSmoothing")
            .define("edgeSmoothing", false);
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
        BUILDER.translation("helion.configuration.image.motionBlur").push("motionBlur");
        MOTION_BLUR_ENABLED = BUILDER
            .translation("helion.configuration.image.motionBlur.enabled")
            .define("enabled", false);
        MOTION_BLUR_STRENGTH = BUILDER
            .translation("helion.configuration.image.motionBlur.strength")
            .defineInRange("strength", MotionBlurSettings.DEFAULT_STRENGTH, MIN_MOTION_BLUR_STRENGTH, MAX_MOTION_BLUR_STRENGTH);
        BUILDER.pop();
        BUILDER.translation("helion.configuration.image.depthOfField").push("depthOfField");
        DEPTH_OF_FIELD_ENABLED = BUILDER
            .translation("helion.configuration.image.depthOfField.enabled")
            .define("enabled", false);
        DEPTH_OF_FIELD_STRENGTH = BUILDER
            .translation("helion.configuration.image.depthOfField.strength")
            .defineInRange("strength", DepthOfFieldSettings.DEFAULT_STRENGTH, MIN_DEPTH_OF_FIELD_STRENGTH, MAX_DEPTH_OF_FIELD_STRENGTH);
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

        BUILDER.translation("helion.configuration.rayTracing").push("rayTracing");
        RAY_TRACING_ENABLED = BUILDER
            .translation("helion.configuration.rayTracing.enabled")
            .gameRestart()
            .define("enabled", false);
        WATER_REFLECTIONS = BUILDER
            .translation("helion.configuration.rayTracing.waterReflections")
            .define("waterReflections", true);
        PATH_TRACING = BUILDER
            .translation("helion.configuration.rayTracing.pathTracing")
            .define("pathTracing", false);
        BUILDER.pop();

        BUILDER.translation("helion.configuration.reflections").push("reflections");
        SCREEN_REFLECTIONS = BUILDER
            .translation("helion.configuration.reflections.screenSpace")
            .define("screenSpace", false);
        RAIN_WETNESS = BUILDER
            .translation("helion.configuration.reflections.rainWetness")
            .define("rainWetness", true);
        REALISTIC_WATER = BUILDER
            .translation("helion.configuration.reflections.realisticWater")
            .define("realisticWater", true);
        BUILDER.pop();

        BUILDER.translation("helion.configuration.temporalAntiAliasing").push("temporalAntiAliasing");
        TEMPORAL_ENABLED = BUILDER
            .translation("helion.configuration.temporalAntiAliasing.enabled")
            .define("enabled", false);
        RENDER_SCALE = BUILDER
            .translation("helion.configuration.temporalAntiAliasing.renderScale")
            .defineEnum("renderScale", RenderScale.NATIVE);
        DYNAMIC_RENDER_SCALE = BUILDER
            .translation("helion.configuration.temporalAntiAliasing.dynamicRenderScale")
            .define("dynamicRenderScale", false);
        TARGET_FRAME_RATE = BUILDER
            .translation("helion.configuration.temporalAntiAliasing.targetFrameRate")
            .defineEnum("targetFrameRate", FrameRateTarget.FPS_144);
        BUILDER.pop();

        BUILDER.translation("helion.configuration.physicalSky").push("physicalSky");
        PHYSICAL_SKY_ENABLED = BUILDER
            .translation("helion.configuration.physicalSky.enabled")
            .define("enabled", false);
        STAR_DENSITY = BUILDER
            .translation("helion.configuration.physicalSky.starDensity")
            .defineEnum("starDensity", StarDensity.MEDIUM);
        MILKY_WAY = BUILDER
            .translation("helion.configuration.physicalSky.milkyWay")
            .define("milkyWay", true);
        LIGHT_SHAFTS = BUILDER
            .translation("helion.configuration.physicalSky.lightShafts")
            .define("lightShafts", true);
        BUILDER.pop();

        BUILDER.translation("helion.configuration.clouds").push("clouds");
        CLOUDS_ENABLED = BUILDER
            .translation("helion.configuration.clouds.enabled")
            .define("enabled", false);
        CLOUD_QUALITY = BUILDER
            .translation("helion.configuration.clouds.quality")
            .defineEnum("quality", CloudQuality.MEDIUM);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HelionConfig() {
    }

    public static RenderSettings renderSettings() {
        return new RenderSettings(
            imageSettings(),
            geometryBufferSettings(),
            new TemporalSettings(
                TEMPORAL_ENABLED.getAsBoolean(),
                RENDER_SCALE.get(),
                new DynamicScaleSettings(DYNAMIC_RENDER_SCALE.getAsBoolean(), TARGET_FRAME_RATE.get())
            ),
            new PhysicalSkySettings(PHYSICAL_SKY_ENABLED.getAsBoolean(), STAR_DENSITY.get(), MILKY_WAY.getAsBoolean(), LIGHT_SHAFTS.getAsBoolean()),
            new CloudSettings(CLOUDS_ENABLED.getAsBoolean(), CLOUD_QUALITY.get()),
            new RayTracingSettings(RAY_TRACING_ENABLED.getAsBoolean() || RayTracing.forced(), WATER_REFLECTIONS.getAsBoolean(), PATH_TRACING.getAsBoolean()),
            new ReflectionSettings(SCREEN_REFLECTIONS.getAsBoolean(), RAIN_WETNESS.getAsBoolean(), REALISTIC_WATER.getAsBoolean())
        );
    }

    private static GeometryBufferSettings geometryBufferSettings() {
        return new GeometryBufferSettings(GEOMETRY_BUFFER_ENABLED.getAsBoolean(), GEOMETRY_BUFFER_VIEW.get());
    }

    private static ImageSettings imageSettings() {
        return new ImageSettings(
            TONE_MAPPER.get(),
            EXPOSURE.get().floatValue(),
            AUTO_EXPOSURE.getAsBoolean(),
            DITHER.getAsBoolean(),
            new BloomSettings(
                BLOOM_ENABLED.getAsBoolean(),
                BLOOM_INTENSITY.get().floatValue(),
                BLOOM_THRESHOLD.get().floatValue(),
                BLOOM_DEBUG_VIEW.getAsBoolean()
            ),
            new SharpeningSettings(SHARPENING_ENABLED.getAsBoolean(), SHARPENING_STRENGTH.get().floatValue()),
            new MotionBlurSettings(MOTION_BLUR_ENABLED.getAsBoolean(), MOTION_BLUR_STRENGTH.get().floatValue()),
            COLOR_GRADE.get(),
            new DepthOfFieldSettings(DEPTH_OF_FIELD_ENABLED.getAsBoolean(), DEPTH_OF_FIELD_STRENGTH.get().floatValue()),
            EDGE_SMOOTHING.getAsBoolean()
        );
    }
}
