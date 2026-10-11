package com.aryston.helion.render.post;

public record MotionBlurSettings(boolean enabled, float strength) {
    public static final double DEFAULT_STRENGTH = 0.5;
    public static final MotionBlurSettings DISABLED = new MotionBlurSettings(false, (float) DEFAULT_STRENGTH);
}
