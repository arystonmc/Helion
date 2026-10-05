package com.aryston.helion.render.lighting;

public enum AmbientOcclusionQuality {
    LOW(2, 2, 1),
    MEDIUM(3, 2, 2),
    HIGH(3, 3, 2),
    ULTRA(4, 4, 3);

    private final int slices;
    private final int stepsPerSlice;
    private final int denoisePasses;

    AmbientOcclusionQuality(int slices, int stepsPerSlice, int denoisePasses) {
        this.slices = slices;
        this.stepsPerSlice = stepsPerSlice;
        this.denoisePasses = denoisePasses;
    }

    public int slices() {
        return slices;
    }

    public int stepsPerSlice() {
        return stepsPerSlice;
    }

    public int denoisePasses() {
        return denoisePasses;
    }
}
