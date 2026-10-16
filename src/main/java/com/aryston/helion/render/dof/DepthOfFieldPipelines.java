package com.aryston.helion.render.dof;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class DepthOfFieldPipelines {
    public static final String SETTINGS = "HelionDepthOfField";
    public static final String PROJECTION = "Projection";
    public static final String SCENE_COLOR_SAMPLER = "SceneColorSampler";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String FOCUS_SAMPLER = "FocusSampler";
    public static final String PREVIOUS_FOCUS_SAMPLER = "PreviousFocusSampler";
    public static final String HALF_SAMPLER = "HalfSampler";
    public static final String BLURRED_SAMPLER = "BlurredSampler";
    public static final GpuFormat FOCUS_FORMAT = GpuFormat.R32_FLOAT;
    public static final GpuFormat HALF_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;

    public static final RenderPipeline FOCUS = HelionPipelines.fullscreen("depth_of_field/focus")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PREVIOUS_FOCUS_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(FOCUS_FORMAT))
        .build();

    public static final RenderPipeline DOWNSAMPLE = HelionPipelines.fullscreen("depth_of_field/downsample")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(FOCUS_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(HALF_FORMAT))
        .build();

    public static final RenderPipeline GATHER = HelionPipelines.fullscreen("depth_of_field/gather")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(HALF_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(HALF_FORMAT))
        .build();

    public static final RenderPipeline BLEND = HelionPipelines.fullscreen("depth_of_field/blend")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(BLURRED_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(FOCUS_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(
            Optional.of(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)),
            SCENE_COLOR_FORMAT,
            ColorTargetState.WRITE_COLOR
        ))
        .build();

    private DepthOfFieldPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(FOCUS, DOWNSAMPLE, GATHER, BLEND);
    }

    private static ColorTargetState writeAll(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }
}
