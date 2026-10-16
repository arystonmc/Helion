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
    public static final String TERRAIN_DEPTH_SAMPLER = "TerrainDepthSampler";
    public static final GpuFormat FOCUS_FORMAT = GpuFormat.R32_FLOAT;
    private static final String TERRAIN_DEPTH_DEFINE = "HELION_DOF_TERRAIN_DEPTH";
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
        .withBindGroupLayout(downsampleBindings(false))
        .withColorTargetState(writeAll(HALF_FORMAT))
        .build();

    public static final RenderPipeline DOWNSAMPLE_TERRAIN_DEPTH = HelionPipelines.fullscreen(
            "depth_of_field/downsample_terrain_depth", "depth_of_field/downsample"
        )
        .withBindGroupLayout(downsampleBindings(true))
        .withColorTargetState(writeAll(HALF_FORMAT))
        .withShaderDefine(TERRAIN_DEPTH_DEFINE)
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
        .withBindGroupLayout(blendBindings(false))
        .withColorTargetState(blendTarget())
        .build();

    public static final RenderPipeline BLEND_TERRAIN_DEPTH = HelionPipelines.fullscreen(
            "depth_of_field/blend_terrain_depth", "depth_of_field/blend"
        )
        .withBindGroupLayout(blendBindings(true))
        .withColorTargetState(blendTarget())
        .withShaderDefine(TERRAIN_DEPTH_DEFINE)
        .build();

    private DepthOfFieldPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(FOCUS, DOWNSAMPLE, DOWNSAMPLE_TERRAIN_DEPTH, GATHER, BLEND, BLEND_TERRAIN_DEPTH);
    }

    private static BindGroupLayout downsampleBindings(boolean terrainDepth) {
        BindGroupLayout.Builder bindings = BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(FOCUS_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
        return (terrainDepth ? bindings.withUniform(TERRAIN_DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER) : bindings).build();
    }

    private static BindGroupLayout blendBindings(boolean terrainDepth) {
        BindGroupLayout.Builder bindings = BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(BLURRED_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(FOCUS_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
        return (terrainDepth ? bindings.withUniform(TERRAIN_DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER) : bindings).build();
    }

    private static ColorTargetState blendTarget() {
        return new ColorTargetState(
            Optional.of(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)),
            SCENE_COLOR_FORMAT,
            ColorTargetState.WRITE_COLOR
        );
    }

    private static ColorTargetState writeAll(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }
}
