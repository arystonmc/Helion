package com.aryston.helion.render.edge;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class EdgeSmoothingPipelines {
    public static final String SCENE_COLOR_SAMPLER = "SceneColorSampler";
    public static final String SMOOTHED_SAMPLER = "SmoothedSampler";
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;

    public static final RenderPipeline SMOOTH = HelionPipelines.fullscreen("edge_smoothing/smooth")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    public static final RenderPipeline APPLY = HelionPipelines.fullscreen("edge_smoothing/apply")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SMOOTHED_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR))
        .build();

    private EdgeSmoothingPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(SMOOTH, APPLY);
    }
}
