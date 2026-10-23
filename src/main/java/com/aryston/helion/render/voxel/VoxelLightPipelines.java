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
    public static final String DYNAMIC = "HelionVoxelDynamic";
    public static final String DEPTH_SAMPLER = "DepthSampler";
    public static final String GEOMETRY_NORMAL_SAMPLER = "GeometryNormalSampler";
    public static final String VOXEL_SAMPLER = "VoxelSampler";
    public static final String COARSE_SAMPLER = "VoxelCoarseSampler";
    public static final String READY_SAMPLER = "VoxelReadySampler";
    public static final String LIGHT_LIST_SAMPLER = "VoxelLightListSampler";
    public static final String LIGHT_GRID_SAMPLER = "VoxelLightGridSampler";
    public static final String LIGHT_SAMPLER = "VoxelLightSampler";
    public static final String CONFIDENCE_SAMPLER = "VoxelConfidenceSampler";
    public static final GpuFormat BOUNCE_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final GpuFormat PROBE_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final String PROBE = "HelionVoxelProbe";
    public static final String COLOR_SAMPLER = "VoxelColorSampler";
    public static final List<String> PROBE_SAMPLERS = List.of("ProbeRedSampler", "ProbeGreenSampler", "ProbeBlueSampler", "ProbePlacementSampler");
    public static final List<String> PREVIOUS_PROBE_SAMPLERS = List.of("PreviousProbeRedSampler", "PreviousProbeGreenSampler", "PreviousProbeBlueSampler", "PreviousProbePlacementSampler");
    public static final int PROBE_WIDTH = 256;
    public static final int PROBE_HEIGHT = 128;
    private static final int CONFIDENCE_TARGET = 1;
    private static final int BOUNCE_TARGET = 2;

    public static final RenderPipeline DIRECT = HelionPipelines.fullscreen("voxel/direct_light")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(DYNAMIC, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(GEOMETRY_NORMAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(VOXEL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(COARSE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(READY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(LIGHT_LIST_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(LIGHT_GRID_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PROBE_SAMPLERS.get(0), UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PROBE_SAMPLERS.get(1), UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PROBE_SAMPLERS.get(2), UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PROBE_SAMPLERS.get(3), UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(target(LIGHT_FORMAT))
        .withColorTargetState(CONFIDENCE_TARGET, target(CONFIDENCE_FORMAT))
        .withColorTargetState(BOUNCE_TARGET, target(BOUNCE_FORMAT))
        .build();

    public static final RenderPipeline PROBE_UPDATE = HelionPipelines.fullscreen("voxel/probe_update")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(PROBE, UniformType.UNIFORM_BUFFER)
            .withUniform(VOXEL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(COARSE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(COLOR_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(READY_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(LIGHT_LIST_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(LIGHT_GRID_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PREVIOUS_PROBE_SAMPLERS.get(0), UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PREVIOUS_PROBE_SAMPLERS.get(1), UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PREVIOUS_PROBE_SAMPLERS.get(2), UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(PREVIOUS_PROBE_SAMPLERS.get(3), UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(0, target(PROBE_FORMAT))
        .withColorTargetState(1, target(PROBE_FORMAT))
        .withColorTargetState(2, target(PROBE_FORMAT))
        .withColorTargetState(3, target(PROBE_FORMAT))
        .build();

    private VoxelLightPipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(DIRECT, PROBE_UPDATE);
    }

    private static ColorTargetState target(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }
}
