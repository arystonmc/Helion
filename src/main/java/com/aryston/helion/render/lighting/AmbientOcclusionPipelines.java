package com.aryston.helion.render.lighting;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class AmbientOcclusionPipelines {
    public static final String PROJECTION = "Projection";
    public static final String FOG = "Fog";
    public static final String SETTINGS = "HelionAmbientOcclusion";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String VIEW_DEPTH_SAMPLER = "ViewDepthSampler";
    public static final String OCCLUSION_SAMPLER = "OcclusionSampler";
    public static final String INDIRECT_SHARE_SAMPLER = "IndirectShareSampler";
    private static final String INDIRECT_SHARE_DEFINE = "HELION_INDIRECT_SHARE";
    private static final String HORIZON_SEARCH_SHADER = "ambient_occlusion/gtao";
    private static final String SLICE_COUNT_DEFINE = "HELION_AO_SLICE_COUNT";
    private static final String STEPS_PER_SLICE_DEFINE = "HELION_AO_STEPS_PER_SLICE";
    private static final String VISIBILITY_BITMASK_DEFINE = "HELION_AO_VISIBILITY_BITMASK";
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

    private static final Map<AmbientOcclusionAlgorithm, Map<AmbientOcclusionQuality, RenderPipeline>> HORIZON_SEARCH = horizonSearchVariants();

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

    public static final RenderPipeline APPLY_INDIRECT = HelionPipelines.fullscreen("ambient_occlusion/apply_indirect", "ambient_occlusion/apply")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(OCCLUSION_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(INDIRECT_SHARE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withShaderDefine(INDIRECT_SHARE_DEFINE)
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
        return Stream.concat(
            Stream.of(VIEW_DEPTH, DENOISE, DENOISE_AND_RESOLVE, APPLY, DEBUG_VIEW, APPLY_INDIRECT),
            HORIZON_SEARCH.values().stream().flatMap(byQuality -> byQuality.values().stream())
        ).toList();
    }

    static RenderPipeline horizonSearch(AmbientOcclusionAlgorithm algorithm, AmbientOcclusionQuality quality) {
        return HORIZON_SEARCH.get(algorithm).get(quality);
    }

    private static Map<AmbientOcclusionAlgorithm, Map<AmbientOcclusionQuality, RenderPipeline>> horizonSearchVariants() {
        Map<AmbientOcclusionAlgorithm, Map<AmbientOcclusionQuality, RenderPipeline>> variants = new EnumMap<>(AmbientOcclusionAlgorithm.class);
        for (AmbientOcclusionAlgorithm algorithm : AmbientOcclusionAlgorithm.values()) {
            Map<AmbientOcclusionQuality, RenderPipeline> byQuality = new EnumMap<>(AmbientOcclusionQuality.class);
            for (AmbientOcclusionQuality quality : AmbientOcclusionQuality.values()) {
                byQuality.put(quality, horizonSearchPipeline(algorithm, quality));
            }
            variants.put(algorithm, byQuality);
        }
        return variants;
    }

    private static RenderPipeline horizonSearchPipeline(AmbientOcclusionAlgorithm algorithm, AmbientOcclusionQuality quality) {
        String name = HORIZON_SEARCH_SHADER + "_" + algorithm.name().toLowerCase(Locale.ROOT) + "_" + quality.name().toLowerCase(Locale.ROOT);
        RenderPipeline.Builder builder = HelionPipelines.fullscreen(name, HORIZON_SEARCH_SHADER)
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(PROJECTION, UniformType.UNIFORM_BUFFER)
                .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
                .withUniform(VIEW_DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build())
            .withShaderDefine(SLICE_COUNT_DEFINE, quality.slices())
            .withShaderDefine(STEPS_PER_SLICE_DEFINE, quality.stepsPerSlice())
            .withColorTargetState(writeAll(OCCLUSION_AND_EDGES_FORMAT));
        if (algorithm == AmbientOcclusionAlgorithm.VISIBILITY_BITMASK) {
            builder.withShaderDefine(VISIBILITY_BITMASK_DEFINE);
        }
        return builder.build();
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
