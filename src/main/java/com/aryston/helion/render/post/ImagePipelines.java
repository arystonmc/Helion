package com.aryston.helion.render.post;

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

public final class ImagePipelines {
    public static final String SETTINGS = "HelionImage";
    public static final String SCENE_COLOR_SAMPLER = "SceneColorSampler";
    public static final String SCENE_DEPTH_SAMPLER = "SceneDepthSampler";
    public static final String SOURCE_SAMPLER = "SourceSampler";
    public static final String BLOOM_SAMPLER = "BloomSampler";
    public static final GpuFormat BLOOM_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat OUTPUT_FORMAT = GpuFormat.RGBA8_UNORM;

    public static final RenderPipeline BLOOM_PREFILTER = HelionPipelines.fullscreen("image/bloom_prefilter")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SCENE_DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(BLOOM_FORMAT))
        .build();

    public static final RenderPipeline BLOOM_DOWNSAMPLE = HelionPipelines.fullscreen("image/bloom_downsample")
        .withBindGroupLayout(sourceOnly())
        .withColorTargetState(writeAll(BLOOM_FORMAT))
        .build();

    public static final RenderPipeline BLOOM_UPSAMPLE = HelionPipelines.fullscreen("image/bloom_upsample")
        .withBindGroupLayout(sourceOnly())
        .withColorTargetState(new ColorTargetState(
            Optional.of(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)),
            BLOOM_FORMAT,
            ColorTargetState.WRITE_COLOR
        ))
        .build();

    public static final RenderPipeline COMPOSITE = HelionPipelines.fullscreen("image/composite")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(BLOOM_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(OUTPUT_FORMAT))
        .build();

    public static final RenderPipeline BLOOM_DEBUG = HelionPipelines.fullscreen("image/bloom_debug")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(BLOOM_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(writeAll(OUTPUT_FORMAT))
        .build();

    private ImagePipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(BLOOM_PREFILTER, BLOOM_DOWNSAMPLE, BLOOM_UPSAMPLE, COMPOSITE, BLOOM_DEBUG);
    }

    private static BindGroupLayout sourceOnly() {
        return BindGroupLayout.builder()
            .withUniform(SOURCE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    }

    private static ColorTargetState writeAll(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }
}
