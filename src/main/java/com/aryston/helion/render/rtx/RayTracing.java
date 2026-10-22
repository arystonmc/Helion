package com.aryston.helion.render.rtx;

public final class RayTracing {
    public static final boolean ENABLED = Boolean.getBoolean("helion.rayTracing");
    public static final boolean VIEW = ENABLED && Boolean.getBoolean("helion.rayTracing.view");

    private RayTracing() {
    }

    public static boolean deviceRequested() {
        return ENABLED || RtxSpike.ENABLED;
    }
}
