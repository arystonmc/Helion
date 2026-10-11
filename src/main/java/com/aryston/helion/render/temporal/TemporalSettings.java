package com.aryston.helion.render.temporal;

public record TemporalSettings(boolean enabled, RenderScale renderScale) {
    public static final TemporalSettings DISABLED = new TemporalSettings(false, RenderScale.NATIVE);

    public RenderScale effectiveScale() {
        return enabled ? renderScale : RenderScale.NATIVE;
    }
}
