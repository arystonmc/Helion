package com.aryston.helion.render.voxel;

public record VoxelLightingSettings(VoxelLightingMode mode, VoxelLightingQuality quality, boolean bounce) {
    public static final VoxelLightingSettings DISABLED = new VoxelLightingSettings(VoxelLightingMode.OFF, VoxelLightingQuality.MEDIUM, false);

    public boolean enabled() {
        return mode != VoxelLightingMode.OFF;
    }
}
