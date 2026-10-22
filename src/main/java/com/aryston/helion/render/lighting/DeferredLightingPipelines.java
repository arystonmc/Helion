package com.aryston.helion.render.lighting;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class DeferredLightingPipelines {
    public static final String FOG = "Fog";
    public static final String SETTINGS = "HelionLighting";
    public static final String GEOMETRY_LIGHT_SAMPLER = "GeometryLightSampler";
    public static final String ALBEDO_SAMPLER = "AlbedoSampler";
    public static final String GEOMETRY_NORMAL_SAMPLER = "GeometryNormalSampler";
    public static final String SKY_LIGHT_SAMPLER = "SkyLightSampler";
    public static final String SHADOW_MASK_SAMPLER = "ShadowMaskSampler";
    public static final String VOXEL_LIGHT_SAMPLER = "VoxelLightSampler";
    public static final String VOXEL_CONFIDENCE_SAMPLER = "VoxelConfidenceSampler";
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat INDIRECT_SHARE_FORMAT = GpuFormat.R8_UNORM;
    private static final int INDIRECT_SHARE_TARGET = 1;
    private static final String LIGHT_ONLY_DEFINE = "HELION_LIGHT_ONLY";
    private static final String SHADING_SHADER = "lighting/deferred_shading";
    private static final String PHYSICAL_SKY_LIGHT_DEFINE = "HELION_PHYSICAL_SKY_LIGHT";
    private static final String SHADOWS_DEFINE = "HELION_SHADOWS";
    private static final String VOXEL_LIGHT_DEFINE = "HELION_VOXEL_LIGHT";
    private static final String VOXEL_SUFFIX = "_voxel";

    private static final List<RenderPipeline> STANDARD = variants(false);
    private static final List<RenderPipeline> VOXEL = variants(true);

    private DeferredLightingPipelines() {
    }

    public static List<RenderPipeline> all() {
        return Stream.concat(STANDARD.stream(), VOXEL.stream()).toList();
    }

    static List<RenderPipeline> standard() {
        return STANDARD;
    }

    static List<RenderPipeline> voxel() {
        return VOXEL;
    }

    private static List<RenderPipeline> variants(boolean voxel) {
        return List.of(
            pipeline("lighting/deferred_shading", Variant.PLAIN, false, voxel),
            pipeline("lighting/light_only_view", Variant.PLAIN, true, voxel),
            pipeline("lighting/deferred_shading_physical_sky", Variant.PHYSICAL_SKY, false, voxel),
            pipeline("lighting/light_only_view_physical_sky", Variant.PHYSICAL_SKY, true, voxel),
            pipeline("lighting/deferred_shading_shadowed", Variant.SHADOWED, false, voxel),
            pipeline("lighting/light_only_view_shadowed", Variant.SHADOWED, true, voxel)
        );
    }

    private static RenderPipeline pipeline(String name, Variant variant, boolean lightOnly, boolean voxel) {
        RenderPipeline.Builder builder = HelionPipelines.fullscreen(voxel ? name + VOXEL_SUFFIX : name, SHADING_SHADER)
            .withBindGroupLayout(bindings(variant, voxel))
            .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR));
        if (variant != Variant.PLAIN) {
            builder.withShaderDefine(PHYSICAL_SKY_LIGHT_DEFINE);
        }
        if (variant == Variant.SHADOWED) {
            builder.withShaderDefine(SHADOWS_DEFINE)
                .withColorTargetState(INDIRECT_SHARE_TARGET, new ColorTargetState(Optional.empty(), INDIRECT_SHARE_FORMAT, ColorTargetState.WRITE_ALL));
        }
        if (lightOnly) {
            builder.withShaderDefine(LIGHT_ONLY_DEFINE);
        }
        if (voxel) {
            builder.withShaderDefine(VOXEL_LIGHT_DEFINE);
        }
        return builder.build();
    }

    private static BindGroupLayout bindings(Variant variant, boolean voxel) {
        BindGroupLayout.Builder builder = BindGroupLayout.builder()
            .withUniform(FOG, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(ALBEDO_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(GEOMETRY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
        if (variant != Variant.PLAIN) {
            builder.withUniform(GEOMETRY_NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(SKY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
        }
        if (variant == Variant.SHADOWED) {
            builder.withUniform(SHADOW_MASK_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
        }
        if (voxel) {
            builder.withUniform(VOXEL_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(VOXEL_CONFIDENCE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
        }
        return builder.build();
    }

    private enum Variant {
        PLAIN,
        PHYSICAL_SKY,
        SHADOWED
    }
}
