package com.aryston.helion.render.shadow;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class ShadowPipelines {
    public static final String SETTINGS = "HelionShadow";
    public static final String PROJECTION = "Projection";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String GEOMETRY_NORMAL_SAMPLER = "GeometryNormalSampler";
    public static final String SHADOW_MAP_SAMPLER = "ShadowMapSampler";
    public static final GpuFormat SHADOW_MAP_FORMAT = GpuFormat.D32_FLOAT;
    public static final GpuFormat MASK_FORMAT = GpuFormat.R8_UNORM;
    public static final GpuFormat SCENE_COLOR_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final double CLEAR_DEPTH = 1.0;
    private static final float SLOPE_DEPTH_BIAS = 1.5F;
    private static final float CONSTANT_DEPTH_BIAS = 1.0F;

    public static final DepthStencilState CASTER_DEPTH = new DepthStencilState(
        CompareOp.LESS_THAN_OR_EQUAL, true, SLOPE_DEPTH_BIAS, CONSTANT_DEPTH_BIAS
    );

    public static final RenderPipeline MASK = HelionPipelines.fullscreen("shadow/mask")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(GEOMETRY_NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SHADOW_MAP_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), MASK_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    private ShadowPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(MASK);
    }
}
