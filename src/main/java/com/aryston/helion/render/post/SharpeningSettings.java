package com.aryston.helion.render.post;

public record SharpeningSettings(boolean enabled, float strength) {
    public static final double DEFAULT_STRENGTH = 0.5;
    public static final SharpeningSettings DISABLED = new SharpeningSettings(false, (float) DEFAULT_STRENGTH);
}
