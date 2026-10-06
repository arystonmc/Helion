package com.aryston.helion.render.atmosphere;

public record PhysicalSkySettings(boolean enabled) {
    public static final PhysicalSkySettings DISABLED = new PhysicalSkySettings(false);
}
