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
    public static final GpuFormat LIGHT_BUFFER_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final String LIGHT_ONLY_DEFINE = "HELION_LIGHT_ONLY";
    private static final String SHADING_SHADER = "lighting/deferred_shading";

    public static final RenderPipeline LIGHT = HelionPipelines.fullscreen("lighting/deferred_light")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(GEOMETRY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), LIGHT_BUFFER_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    public static final RenderPipeline SHADING = shading(SHADING_SHADER).build();

    public static final RenderPipeline LIGHT_ONLY_VIEW = shading("lighting/light_only_view")
        .withShaderDefine(LIGHT_ONLY_DEFINE)
        .build();

    private DeferredLightingPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(LIGHT, SHADING, LIGHT_ONLY_VIEW);
    }

    private static RenderPipeline.Builder shading(String name) {
        return HelionPipelines.fullscreen(name, SHADING_SHADER)
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform(FOG, UniformType.UNIFORM_BUFFER)
                .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
                .withUniform(LIGHT_BUFFER_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(ALBEDO_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(GEOMETRY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build())
            .withColorTargetState(new ColorTargetState(Optional.empty(), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR));
    }
}
