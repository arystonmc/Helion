package com.aryston.helion.render.clouds;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class CloudPipelines {
    public static final String SETTINGS = "HelionClouds";
    public static final String NOISE_SAMPLER = "CloudNoiseSampler";
    public static final String SKY_LIGHT_SAMPLER = "SkyLightSampler";
    public static final String COLOR_SAMPLER = "CloudColorSampler";
    public static final String DISTANCE_SAMPLER = "CloudDistanceSampler";
    public static final String HISTORY_COLOR_SAMPLER = "HistoryColorSampler";
    public static final String HISTORY_DISTANCE_SAMPLER = "HistoryDistanceSampler";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final GpuFormat NOISE_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat COLOR_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat DISTANCE_FORMAT = GpuFormat.RG16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final int NOISE_ATLAS_SIZE = 528;
    public static final int COLOR_TARGET = 0;
    public static final int DISTANCE_TARGET = 1;

    public static final RenderPipeline NOISE = HelionPipelines.fullscreen("clouds/noise")
        .withColorTargetState(new ColorTargetState(Optional.empty(), NOISE_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    public static final RenderPipeline MARCH = cloudTargets(HelionPipelines.fullscreen("clouds/march")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(NOISE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SKY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build()))
        .build();

    public static final RenderPipeline RESOLVE = cloudTargets(HelionPipelines.fullscreen("clouds/resolve")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DISTANCE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(HISTORY_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(HISTORY_DISTANCE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build()))
        .build();

    public static final RenderPipeline COMPOSITE = HelionPipelines.fullscreen("clouds/composite")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR))
        .build();

    private CloudPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(NOISE, MARCH, RESOLVE, COMPOSITE);
    }

    private static RenderPipeline.Builder cloudTargets(RenderPipeline.Builder builder) {
        return builder
            .withColorTargetState(COLOR_TARGET, new ColorTargetState(Optional.empty(), COLOR_FORMAT, ColorTargetState.WRITE_ALL))
            .withColorTargetState(DISTANCE_TARGET, new ColorTargetState(Optional.empty(), DISTANCE_FORMAT, ColorTargetState.WRITE_ALL));
    }
}
