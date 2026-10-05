package com.aryston.helion.config;

import com.aryston.helion.render.graph.RenderSettings;
import com.aryston.helion.render.lighting.AmbientOcclusionQuality;
import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HelionConfig {
    private static final double MIN_STRENGTH = 0.0;
    private static final double MAX_STRENGTH = 2.0;
    private static final double MIN_RADIUS = 0.5;
    private static final double MAX_RADIUS = 4.0;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .translation("helion.configuration.enabled")
        .define("enabled", true);

    public static final ModConfigSpec.BooleanValue GPU_TIMINGS = BUILDER
        .translation("helion.configuration.gpuTimings")
        .define("gpuTimings", true);

    private static final ModConfigSpec.BooleanValue AMBIENT_OCCLUSION_ENABLED;
    private static final ModConfigSpec.EnumValue<AmbientOcclusionQuality> AMBIENT_OCCLUSION_QUALITY;
    private static final ModConfigSpec.DoubleValue AMBIENT_OCCLUSION_STRENGTH;
    private static final ModConfigSpec.DoubleValue AMBIENT_OCCLUSION_RADIUS;
    private static final ModConfigSpec.BooleanValue AMBIENT_OCCLUSION_DEBUG_VIEW;

    static {
        BUILDER.translation("helion.configuration.ambientOcclusion").push("ambientOcclusion");
        AMBIENT_OCCLUSION_ENABLED = BUILDER
            .translation("helion.configuration.ambientOcclusion.enabled")
            .define("enabled", true);
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
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HelionConfig() {
    }

    public static RenderSettings renderSettings() {
        return new RenderSettings(new AmbientOcclusionSettings(
            AMBIENT_OCCLUSION_ENABLED.getAsBoolean(),
            AMBIENT_OCCLUSION_QUALITY.get(),
            AMBIENT_OCCLUSION_STRENGTH.get().floatValue(),
            AMBIENT_OCCLUSION_RADIUS.get().floatValue(),
            AMBIENT_OCCLUSION_DEBUG_VIEW.getAsBoolean()
        ));
    }
}
