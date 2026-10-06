package com.aryston.helion.render.post;

public record BloomSettings(boolean enabled, float intensity, float threshold, boolean debugView) {
    public static final double DEFAULT_INTENSITY = 0.5;
    public static final double DEFAULT_THRESHOLD = 1.0;
    public static final BloomSettings DISABLED = new BloomSettings(false, (float) DEFAULT_INTENSITY, (float) DEFAULT_THRESHOLD, false);
}
