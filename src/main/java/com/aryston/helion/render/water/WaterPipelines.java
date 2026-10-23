package com.aryston.helion.render.water;

import com.aryston.helion.render.atmosphere.PhysicalSkyPipelines;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class WaterPipelines {
    public static final GpuFormat SURFACE_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat TINT_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final GpuFormat WAVE_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final String SURFACE_UNIFORM = "HelionWaterSurface";
    public static final String SCENE_DEPTH_SAMPLER = "SceneDepthSampler";
    public static final String REFLECTION_UNIFORM = "HelionWaterReflection";
    public static final String SURFACE_SAMPLER = "WaterSurfaceSampler";
    public static final String DEPTH_SAMPLER = "WaterDepthSampler";
    public static final String REFLECTION_SAMPLER = "ReflectionSampler";
    public static final String SCREEN_REFLECTION_SAMPLER = "ScreenReflectionSampler";
    public static final String SCREEN_REFLECTION_UNIFORM = "HelionScreenReflection";
    public static final String SCENE_COLOR_SAMPLER = "SceneColorSampler";
    public static final GpuFormat SCREEN_REFLECTION_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final String GEOMETRY_NORMAL_SAMPLER = "GeometryNormalSampler";
    public static final String GEOMETRY_LIGHT_SAMPLER = "GeometryLightSampler";
    public static final String POLISHED_SAMPLER = "PolishedSampler";
    public static final String TINT_SAMPLER = "WaterTintSampler";
    public static final String REFRACTION_SAMPLER = "RefractionSampler";
    public static final String WAVE_SAMPLER = "WaveSampler";
    public static final String WAVE_UNIFORM = "HelionWaterWaves";
    private static final int POLISHED_TARGET = 1;
    private static final String FOG = "Fog";
    private static final String REFLECTION_SHADER = "water/reflection";
    private static final String SCREEN_REFLECTION_SHADER = "water/screen_reflection";
    private static final String PHYSICAL_SKY_DEFINE = "HELION_PHYSICAL_SKY";

    public static final RenderPipeline REFLECTION = reflection("water/reflection", BindGroupLayout.builder()
        .withUniform(FOG, UniformType.UNIFORM_BUFFER)).build();

    public static final RenderPipeline REFLECTION_PHYSICAL_SKY = reflection("water/reflection_physical_sky", PhysicalSkyPipelines.skyColorBindings())
        .withShaderDefine(PHYSICAL_SKY_DEFINE)
        .build();

    public static final RenderPipeline SCREEN_REFLECTION = screenReflection("water/screen_reflection", BindGroupLayout.builder()
        .withUniform(FOG, UniformType.UNIFORM_BUFFER)).build();

    public static final RenderPipeline SCREEN_REFLECTION_PHYSICAL_SKY = screenReflection("water/screen_reflection_physical_sky", PhysicalSkyPipelines.skyColorBindings())
        .withShaderDefine(PHYSICAL_SKY_DEFINE)
        .build();

    public static final RenderPipeline WAVE_STEP = HelionPipelines.fullscreen("water/wave_step")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(WAVE_UNIFORM, UniformType.UNIFORM_BUFFER)
            .withUniform(WAVE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), WAVE_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    public static final RenderPipeline POLISHED_COMPOSITE = HelionPipelines.fullscreen("water/polished_composite")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(POLISHED_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR))
        .build();

    private WaterPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(REFLECTION, REFLECTION_PHYSICAL_SKY, SCREEN_REFLECTION, SCREEN_REFLECTION_PHYSICAL_SKY, POLISHED_COMPOSITE, WAVE_STEP);
    }

    private static RenderPipeline.Builder screenReflection(String name, BindGroupLayout.Builder bindings) {
        return HelionPipelines.fullscreen(name, SCREEN_REFLECTION_SHADER)
            .withBindGroupLayout(bindings
                .withUniform(SCREEN_REFLECTION_UNIFORM, UniformType.UNIFORM_BUFFER)
                .withUniform(SURFACE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(SCENE_COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(SCENE_DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(GEOMETRY_NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(GEOMETRY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build())
            .withColorTargetState(new ColorTargetState(Optional.empty(), SCREEN_REFLECTION_FORMAT, ColorTargetState.WRITE_ALL))
            .withColorTargetState(POLISHED_TARGET, new ColorTargetState(Optional.empty(), SCREEN_REFLECTION_FORMAT, ColorTargetState.WRITE_ALL));
    }

    private static RenderPipeline.Builder reflection(String name, BindGroupLayout.Builder bindings) {
        return HelionPipelines.fullscreen(name, REFLECTION_SHADER)
            .withBindGroupLayout(bindings
                .withUniform(REFLECTION_UNIFORM, UniformType.UNIFORM_BUFFER)
                .withUniform(SURFACE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(REFLECTION_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(SCREEN_REFLECTION_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(TINT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform(REFRACTION_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
                .build())
            .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), SCENE_COLOR_FORMAT, ColorTargetState.WRITE_COLOR));
    }
}
