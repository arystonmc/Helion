package com.aryston.helion.render.atmosphere;

public record PhysicalSkySettings(boolean enabled, StarDensity starDensity, boolean milkyWay, boolean lightShafts) {
    public static final PhysicalSkySettings DISABLED = new PhysicalSkySettings(false, StarDensity.MEDIUM, true, false);

    public static PhysicalSkySettings enabledWithDefaults() {
        return new PhysicalSkySettings(true, StarDensity.MEDIUM, true, true);
    }
}
