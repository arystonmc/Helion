package com.aryston.helion.render.lighting;

public record DynamicLightSettings(boolean enabled) {
    public static final DynamicLightSettings DISABLED = new DynamicLightSettings(false);
}
