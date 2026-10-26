package com.aryston.helion.render.rtx;

public record RayTracingSettings(boolean enabled, boolean waterReflections) {
    public static final RayTracingSettings DISABLED = new RayTracingSettings(false, false);

    public boolean active() {
        return enabled && RayTracing.requested();
    }

    public boolean reflectsWater() {
        return active() && waterReflections;
    }
}
