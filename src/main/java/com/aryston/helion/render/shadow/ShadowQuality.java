package com.aryston.helion.render.shadow;

public enum ShadowQuality {
    LOW(1024),
    MEDIUM(1536),
    HIGH(2048);

    private final int cascadeResolution;

    ShadowQuality(int cascadeResolution) {
        this.cascadeResolution = cascadeResolution;
    }

    public int cascadeResolution() {
        return cascadeResolution;
    }
}
