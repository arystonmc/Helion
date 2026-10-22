package com.aryston.helion.render.lightshaft;

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

public final class LightShaftPipelines {
    public static final String SETTINGS = "HelionLightShafts";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String CLOUD_SAMPLER = "CloudColorSampler";
    public static final String MASK_SAMPLER = "ShaftMaskSampler";
    public static final String SHAFT_SAMPLER = "ShaftSampler";
    public static final String SKY_LIGHT_SAMPLER = "SkyLightSampler";
    public static final GpuFormat MASK_FORMAT = GpuFormat.R8_UNORM;
    public static final GpuFormat SHAFT_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    private static final String CLOUD_DEFINE = "HELION_CLOUD_SHAFTS";
    private static final String MASK_SHADER = "light_shafts/mask";

    public static final RenderPipeline MASK = mask(HelionPipelines.fullscreen(MASK_SHADER), false);

    public static final RenderPipeline CLOUD_MASK = mask(HelionPipelines.fullscreen("light_shafts/cloud_mask", MASK_SHADER), true);

    public static final RenderPipeline BLUR = HelionPipelines.fullscreen("light_shafts/blur")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(MASK_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), SHAFT_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    public static final RenderPipeline COMPOSITE = HelionPipelines.fullscreen("light_shafts/composite")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(SHAFT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SKY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(
            Optional.of(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR)),
            SCENE_COLOR_FORMAT,
            ColorTargetState.WRITE_COLOR
        ))
        .build();

    private LightShaftPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(MASK, CLOUD_MASK, BLUR, COMPOSITE);
    }

    private static RenderPipeline mask(RenderPipeline.Builder builder, boolean clouds) {
        BindGroupLayout.Builder layout = BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
        RenderPipeline.Builder variant = builder;
        if (clouds) {
            layout = layout.withUniform(CLOUD_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER);
            variant = variant.withShaderDefine(CLOUD_DEFINE);
        }
        return variant
            .withBindGroupLayout(layout.build())
            .withColorTargetState(new ColorTargetState(Optional.empty(), MASK_FORMAT, ColorTargetState.WRITE_ALL))
            .build();
    }
}
