package com.aryston.helion.render.lighting;

public record AmbientOcclusionSettings(
    boolean enabled,
    AmbientOcclusionAlgorithm algorithm,
    AmbientOcclusionQuality quality,
    float strength,
    float radius,
    boolean debugView
) {
    public static final float DEFAULT_STRENGTH = 1.0F;
    public static final float DEFAULT_RADIUS = 1.5F;
    public static final AmbientOcclusionSettings DISABLED = new AmbientOcclusionSettings(
        false, AmbientOcclusionAlgorithm.GTAO, AmbientOcclusionQuality.MEDIUM, DEFAULT_STRENGTH, DEFAULT_RADIUS, false
    );
}
