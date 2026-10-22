package com.aryston.helion.render.voxel;

public enum VoxelLightingQuality {
    LOW(1, 2),
    MEDIUM(2, 2),
    HIGH(4, 1);

    private final int tracedLights;
    private final int resolutionDivisor;

    VoxelLightingQuality(int tracedLights, int resolutionDivisor) {
        this.tracedLights = tracedLights;
        this.resolutionDivisor = resolutionDivisor;
    }

    public int tracedLights() {
        return tracedLights;
    }

    public int resolutionDivisor() {
        return resolutionDivisor;
    }
}
