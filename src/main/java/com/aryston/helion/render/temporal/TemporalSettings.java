package com.aryston.helion.render.temporal;

public record TemporalSettings(boolean enabled, RenderScale renderScale, DynamicScaleSettings dynamicScale) {
    public static final TemporalSettings DISABLED = new TemporalSettings(false, RenderScale.NATIVE, DynamicScaleSettings.DISABLED);

    public RenderScale effectiveScale() {
        return enabled ? renderScale : RenderScale.NATIVE;
    }
}
