package com.aryston.helion.render.voxel;

public record VoxelLightingSettings(VoxelLightingMode mode, VoxelLightingQuality quality) {
    public static final VoxelLightingSettings DISABLED = new VoxelLightingSettings(VoxelLightingMode.OFF, VoxelLightingQuality.MEDIUM);

    public boolean enabled() {
        return mode != VoxelLightingMode.OFF;
    }
}
