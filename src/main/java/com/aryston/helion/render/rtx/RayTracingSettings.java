package com.aryston.helion.render.rtx;

public record RayTracingSettings(boolean enabled, boolean waterReflections, boolean pathTracing, PathTracingQuality pathTracingQuality) {
    public static final RayTracingSettings DISABLED = new RayTracingSettings(false, false, false, PathTracingQuality.HIGH);

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
