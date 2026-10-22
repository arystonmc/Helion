package com.aryston.helion.render.voxel;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class VoxelLightPipelines {
    public static final GpuFormat LIGHT_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat CONFIDENCE_FORMAT = GpuFormat.R8_UNORM;
    public static final String SETTINGS = "HelionVoxelLight";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String GEOMETRY_NORMAL_SAMPLER = "GeometryNormalSampler";
    public static final String VOXEL_SAMPLER = "VoxelSampler";
    public static final String COARSE_SAMPLER = "VoxelCoarseSampler";
    public static final String READY_SAMPLER = "VoxelReadySampler";
    public static final String LIGHT_LIST_SAMPLER = "VoxelLightListSampler";
    public static final String LIGHT_GRID_SAMPLER = "VoxelLightGridSampler";
    public static final String LIGHT_SAMPLER = "VoxelLightSampler";
    public static final String CONFIDENCE_SAMPLER = "VoxelConfidenceSampler";
    private static final int CONFIDENCE_TARGET = 1;

    public static final RenderPipeline DIRECT = HelionPipelines.fullscreen("voxel/direct_light")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(GEOMETRY_NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(VOXEL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(COARSE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(READY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(LIGHT_LIST_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(LIGHT_GRID_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(target(LIGHT_FORMAT))
        .withColorTargetState(CONFIDENCE_TARGET, target(CONFIDENCE_FORMAT))
        .build();

    private VoxelLightPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(DIRECT);
    }

    private static ColorTargetState target(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }
}
