package com.aryston.helion.render.rtx;

public final class RayTracing {
    public static final boolean VIEW = Boolean.getBoolean("helion.rayTracing.view");

    private static final boolean FORCED = Boolean.getBoolean("helion.rayTracing");
    private static volatile boolean requested = FORCED;

    private RayTracing() {
    }

    public static void requestAtStartup(boolean configured) {
        requested = FORCED || configured;
    }

    public static boolean forced() {
        return FORCED;
    }

    public static boolean requested() {
        return requested;
    }

    public static boolean deviceRequested() {
        return requested || RtxSpike.ENABLED;
    }
}
