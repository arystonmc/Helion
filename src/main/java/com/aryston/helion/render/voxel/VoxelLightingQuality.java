package com.aryston.helion.render.voxel;

public enum VoxelLightingQuality {
    LOW(1, 2, 4),
    MEDIUM(2, 2, 8),
    HIGH(4, 1, 16);

    private final int tracedLights;
    private final int resolutionDivisor;
    private final int probeRays;

    VoxelLightingQuality(int tracedLights, int resolutionDivisor, int probeRays) {
        this.tracedLights = tracedLights;
        this.resolutionDivisor = resolutionDivisor;
        this.probeRays = probeRays;
    }

    public int tracedLights() {
        return tracedLights;
    }

    public int resolutionDivisor() {
        return resolutionDivisor;
    }

    public int probeRays() {
        return probeRays;
    }
}
