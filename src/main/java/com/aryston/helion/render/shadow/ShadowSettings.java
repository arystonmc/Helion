package com.aryston.helion.render.shadow;

public record ShadowSettings(boolean enabled, ShadowQuality quality) {
    public static final ShadowSettings DISABLED = new ShadowSettings(false, ShadowQuality.MEDIUM);
}
