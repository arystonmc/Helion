package com.aryston.helion.render.voxel;

import com.mojang.renderpearl.api.textures.GpuTextureView;

public record VoxelVolumeView(
    GpuTextureView texture,
    GpuTextureView coarse,
    GpuTextureView ready,
    GpuTextureView lights,
    GpuTextureView grid,
    int minBlockX,
    int minBlockY,
    int minBlockZ,
    int lightCount
) {
}
