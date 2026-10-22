package com.aryston.helion.render.voxel;

import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.List;
import java.util.Optional;

public final class VoxelSlicePipelines {
    public static final GpuFormat OUTPUT_FORMAT = GpuFormat.RGBA8_UNORM;
    public static final String SETTINGS = "HelionVoxelSlice";
    public static final String VOXEL_SAMPLER = "VoxelSampler";

    public static final RenderPipeline SLICE = HelionPipelines.fullscreen("voxel/slice")
        .withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(SETTINGS, UniformType.UNIFORM_BUFFER)
            .withUniform(VOXEL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build())
        .withColorTargetState(new ColorTargetState(Optional.empty(), OUTPUT_FORMAT, ColorTargetState.WRITE_ALL))
        .build();

    private VoxelSlicePipelines() {
    }

    public static List<RenderPipeline> all() {
        return List.of(SLICE);
    }
}
