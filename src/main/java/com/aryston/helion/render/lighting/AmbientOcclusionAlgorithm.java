package com.aryston.helion.render.lighting;

public enum AmbientOcclusionAlgorithm {
    GTAO(0),
    VISIBILITY_BITMASK(1);

    private final int shaderId;

    AmbientOcclusionAlgorithm(int shaderId) {
        this.shaderId = shaderId;
    }

    public int shaderId() {
        return shaderId;
    }
}
