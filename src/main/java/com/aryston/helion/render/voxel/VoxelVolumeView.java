package com.aryston.helion.render.voxel;

import com.mojang.renderpearl.api.textures.GpuTextureView;

public record VoxelVolumeView(GpuTextureView texture, int minBlockX, int minBlockY, int minBlockZ) {
}
