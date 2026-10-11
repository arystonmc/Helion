package com.aryston.helion.render.post;

public record DepthOfFieldSettings(boolean enabled, float strength) {
    public static final double DEFAULT_STRENGTH = 0.5;
    public static final DepthOfFieldSettings DISABLED = new DepthOfFieldSettings(false, (float) DEFAULT_STRENGTH);
}
