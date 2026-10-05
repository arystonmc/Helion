package com.aryston.helion.render.lighting;

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

public final class AmbientOcclusionPipelines {
    public static final String PROJECTION = "Projection";
    public static final String FOG = "Fog";
    public static final String SETTINGS = "HelionAmbientOcclusion";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String VIEW_DEPTH_SAMPLER = "ViewDepthSampler";
    public static final String OCCLUSION_SAMPLER = "OcclusionSampler";
    public static final String EDGES_SAMPLER = "EdgesSampler";
    public static final String SCENE_COLOR_SAMPLER = "SceneColorSampler";
    public static final GpuFormat VIEW_DEPTH_FORMAT = GpuFormat.R32_FLOAT;
    public static final GpuFormat OCCLUSION_AND_EDGES_FORMAT = GpuFormat.RG8_UNORM;
    public static final GpuFormat OCCLUSION_FORMAT = GpuFormat.R8_UNORM;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;

    public static final RenderPipeline VIEW_DEPTH = HelionPipelines.fullscreen("ambient_occlusion/view_depth")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(VIEW_DEPTH_FORMAT))
        .build();

    public static final RenderPipeline HORIZON_SEARCH = HelionPipelines.fullscreen("ambient_occlusion/gtao")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(VIEW_DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(OCCLUSION_AND_EDGES_FORMAT))
        .build();

    public static final RenderPipeline DENOISE = denoise("ambient_occlusion/denoise").build();

    public static final RenderPipeline DENOISE_AND_RESOLVE = denoise("ambient_occlusion/denoise_resolve")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
            .withUniform(FOG, UniformType.UNIFORM_BUFFER)
            .withUniform(VIEW_DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .build();

    public static final RenderPipeline APPLY = HelionPipelines.fullscreen("ambient_occlusion/apply")
        .withBindGroupLayout(occlusionOnly())
        .withColorTargetState(new ColorTargetState(
            Optional.of(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO)),
            SCENE_COLOR_FORMAT,
            ColorTargetState.WRITE_COLOR
        ))
        .build();

    public static final RenderPipeline DEBUG_VIEW = HelionPipelines.fullscreen("ambient_occlusion/debug", "ambient_occlusion/apply")
        .withBindGroupLayout(occlusionOnly())
        .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR))
        .build();

    private AmbientOcclusionPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(VIEW_DEPTH, HORIZON_SEARCH, DENOISE, DENOISE_AND_RESOLVE, APPLY, DEBUG_VIEW);
    }

    private static RenderPipeline.Builder denoise(String fragmentShader) {
        return HelionPipelines.fullscreen(fragmentShader)
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
                .withUniform(OCCLUSION_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(EDGES_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build())
            .withColorTargetState(writeAll(OCCLUSION_FORMAT));
    }

    private static BindGroupLayout occlusionOnly() {
        return BindGroupLayout.builder()
            .withUniform(OCCLUSION_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    }

    private static ColorTargetState writeAll(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }
}
