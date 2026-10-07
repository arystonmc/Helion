package com.aryston.helion.render.lighting;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class DeferredLightingPipelines {
    public static final String FOG = "Fog";
    public static final String SETTINGS = "HelionLighting";
    public static final String GEOMETRY_LIGHT_SAMPLER = "GeometryLightSampler";
    public static final String ALBEDO_SAMPLER = "AlbedoSampler";
    public static final String LIGHT_BUFFER_SAMPLER = "LightBufferSampler";
    public static final String GEOMETRY_NORMAL_SAMPLER = "GeometryNormalSampler";
    public static final String SKY_LIGHT_SAMPLER = "SkyLightSampler";
    public static final String SHADOW_MASK_SAMPLER = "ShadowMaskSampler";
    public static final GpuFormat LIGHT_BUFFER_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final String LIGHT_ONLY_DEFINE = "HELION_LIGHT_ONLY";
    private static final String SHADING_SHADER = "lighting/deferred_shading";
    private static final String LIGHT_SHADER = "lighting/deferred_light";
    private static final String PHYSICAL_SKY_LIGHT_DEFINE = "HELION_PHYSICAL_SKY_LIGHT";
    private static final String SHADOWS_DEFINE = "HELION_SHADOWS";

    public static final RenderPipeline LIGHT = light(LIGHT_SHADER, lightBindings().build()).build();

    public static final RenderPipeline PHYSICAL_SKY_LIGHT = light("lighting/deferred_light_physical_sky", lightBindings()
        .withUniform(GEOMETRY_NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform(SKY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
        .build())
        .withShaderDefine(PHYSICAL_SKY_LIGHT_DEFINE)
        .build();

    public static final RenderPipeline SHADING = shading(SHADING_SHADER, shadingBindings().build()).build();

    public static final RenderPipeline LIGHT_ONLY_VIEW = shading("lighting/light_only_view", shadingBindings().build())
        .withShaderDefine(LIGHT_ONLY_DEFINE)
        .build();

    public static final RenderPipeline SHADOWED_SHADING = shadowed(shading("lighting/deferred_shading_shadowed", shadowedShadingBindings())).build();

    public static final RenderPipeline SHADOWED_LIGHT_ONLY_VIEW = shadowed(shading("lighting/light_only_view_shadowed", shadowedShadingBindings()))
        .withShaderDefine(LIGHT_ONLY_DEFINE)
        .build();

    private DeferredLightingPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(LIGHT, PHYSICAL_SKY_LIGHT, SHADING, LIGHT_ONLY_VIEW, SHADOWED_SHADING, SHADOWED_LIGHT_ONLY_VIEW);
    }

    private static RenderPipeline.Builder light(String name, BindGroupLayout bindings) {
        return HelionPipelines.fullscreen(name, LIGHT_SHADER)
            .withBindGroupLayout(bindings)
            .withColorTargetState(new ColorTargetState(Optional.empty(), LIGHT_BUFFER_FORMAT, ColorTargetState.WRITE_ALL));
    }

    private static BindGroupLayout.Builder lightBindings() {
        return BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(GEOMETRY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
    }

    private static RenderPipeline.Builder shading(String name, BindGroupLayout bindings) {
        return HelionPipelines.fullscreen(name, SHADING_SHADER)
            .withBindGroupLayout(bindings)
            .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR));
    }

    private static BindGroupLayout.Builder shadingBindings() {
        return BindGroupLayout.builder()
            .withUniform(FOG, UniformType.UNIFORM_BUFFER)
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(LIGHT_BUFFER_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(ALBEDO_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(GEOMETRY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
    }

    private static BindGroupLayout shadowedShadingBindings() {
        return shadingBindings()
            .withUniform(GEOMETRY_NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SKY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SHADOW_MASK_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    }

    private static RenderPipeline.Builder shadowed(RenderPipeline.Builder builder) {
        return builder
            .withShaderDefine(PHYSICAL_SKY_LIGHT_DEFINE)
            .withShaderDefine(SHADOWS_DEFINE);
    }
}
