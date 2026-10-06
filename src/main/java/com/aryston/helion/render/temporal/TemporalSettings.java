package com.aryston.helion.render.temporal;

public record TemporalSettings(boolean enabled) {
    public static final TemporalSettings DISABLED = new TemporalSettings(false);
}
