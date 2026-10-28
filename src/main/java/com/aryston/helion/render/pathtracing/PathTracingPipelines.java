package com.aryston.helion.render.pathtracing;

import com.aryston.helion.render.geometry.GeometryBufferPipelines;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class PathTracingPipelines {
    public static final String FOG = "Fog";
    public static final String IRRADIANCE_SAMPLER = "IrradianceSampler";
    public static final String SPECULAR_SAMPLER = "SpecularSampler";
    public static final String ALBEDO_SAMPLER = "AlbedoSampler";
    public static final String GEOMETRY_LIGHT_SAMPLER = "GeometryLightSampler";
    public static final String GEOMETRY_NORMAL_SAMPLER = "GeometryNormalSampler";

    public static final RenderPipeline RADIANCE = HelionPipelines.fullscreen("pathtracing/radiance")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(FOG, UniformType.UNIFORM_BUFFER)
            .withUniform(IRRADIANCE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(SPECULAR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(ALBEDO_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(GEOMETRY_LIGHT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(GEOMETRY_NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), GeometryBufferPipelines.OUTPUT_FORMAT, ColorTargetState.WRITE_COLOR))
        .build();

    private PathTracingPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(RADIANCE);
    }
}
