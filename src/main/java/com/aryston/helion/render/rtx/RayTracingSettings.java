package com.aryston.helion.render.rtx;

public record RayTracingSettings(boolean enabled, boolean waterReflections, boolean pathTracing) {
    public static final RayTracingSettings DISABLED = new RayTracingSettings(false, false, false);

    public boolean active() {
        return enabled && RayTracing.requested();
    }

    public boolean reflectsWater() {
        return active() && waterReflections;
    }

    public boolean tracesLight() {
        return active() && pathTracing;
    }
}
