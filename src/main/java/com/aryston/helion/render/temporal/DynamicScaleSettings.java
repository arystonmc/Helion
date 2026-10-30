package com.aryston.helion.render.temporal;

public record DynamicScaleSettings(boolean enabled, FrameRateTarget target) {
    public static final DynamicScaleSettings DISABLED = new DynamicScaleSettings(false, FrameRateTarget.FPS_144);
}
