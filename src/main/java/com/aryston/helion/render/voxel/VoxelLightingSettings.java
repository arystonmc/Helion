package com.aryston.helion.render.voxel;

public record VoxelLightingSettings(VoxelLightingMode mode) {
    public static final VoxelLightingSettings DISABLED = new VoxelLightingSettings(VoxelLightingMode.OFF);

    public boolean enabled() {
        return mode != VoxelLightingMode.OFF;
    }
}
