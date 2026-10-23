package com.aryston.helion.render.rtx;

public record RayTracingSettings(boolean enabled, boolean waterReflections, boolean shadows) {
    public static final RayTracingSettings DISABLED = new RayTracingSettings(false, false, false);

    public boolean active() {
        return enabled && RayTracing.requested();
    }

    public boolean tracesShadows() {
        return active() && shadows;
    }

    public boolean reflectsWater() {
        return active() && waterReflections;
    }
}
