package com.aryston.helion.render.temporal;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class TemporalPipelines {
    public static final String SETTINGS = "HelionTemporal";
    public static final String SCENE_COLOR_SAMPLER = "SceneColorSampler";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String HISTORY_SAMPLER = "HistorySampler";
    public static final GpuFormat HISTORY_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;

    public static final RenderPipeline RESOLVE = HelionPipelines.fullscreen("temporal/resolve")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(HISTORY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), HISTORY_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    public static final RenderPipeline APPLY = HelionPipelines.fullscreen("temporal/apply")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(HISTORY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR))
        .build();

    private TemporalPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(RESOLVE, APPLY);
    }
}
