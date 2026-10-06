package com.aryston.helion.render.lighting;

public record DeferredLightingSettings(boolean enabled, float blockLightIntensity, float skyLightIntensity, boolean lightOnlyView) {
    public static final float DEFAULT_BLOCK_LIGHT_INTENSITY = 1.0F;
    public static final float DEFAULT_SKY_LIGHT_INTENSITY = 1.0F;
    public static final DeferredLightingSettings DISABLED = new DeferredLightingSettings(
        false, DEFAULT_BLOCK_LIGHT_INTENSITY, DEFAULT_SKY_LIGHT_INTENSITY, false
    );
}
