package com.aryston.helion.render.motion;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class MotionBlurPipelines {
    public static final String SETTINGS = "HelionMotionBlur";
    public static final String PROJECTION = "Projection";
    public static final String SCENE_COLOR_SAMPLER = "SceneColorSampler";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String TILE_MAX_SAMPLER = "TileMaxSampler";
    public static final String NEIGHBOR_MAX_SAMPLER = "NeighborMaxSampler";
    public static final String BLURRED_SAMPLER = "BlurredSampler";
    public static final GpuFormat VELOCITY_FORMAT = GpuFormat.RG16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final int TILE_SIZE = 16;

    public static final RenderPipeline TILE_MAX = HelionPipelines.fullscreen("motion_blur/tile_max")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(VELOCITY_FORMAT))
        .build();

    public static final RenderPipeline NEIGHBOR_MAX = HelionPipelines.fullscreen("motion_blur/neighbor_max")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(TILE_MAX_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(VELOCITY_FORMAT))
        .build();

    public static final RenderPipeline GATHER = HelionPipelines.fullscreen("motion_blur/gather")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(NEIGHBOR_MAX_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(SCENE_COLOR_FORMAT))
        .build();

    public static final RenderPipeline APPLY = HelionPipelines.fullscreen("motion_blur/apply")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(BLURRED_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR))
        .build();

    private MotionBlurPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(TILE_MAX, NEIGHBOR_MAX, GATHER, APPLY);
    }

    private static ColorTargetState writeAll(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }
}
