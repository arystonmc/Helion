package com.aryston.helion.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HelionConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .translation("helion.configuration.enabled")
        .define("enabled", true);

    public static final ModConfigSpec.BooleanValue GPU_TIMINGS = BUILDER
        .translation("helion.configuration.gpuTimings")
        .define("gpuTimings", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HelionConfig() {
    }
}
