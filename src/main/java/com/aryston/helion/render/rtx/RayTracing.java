package com.aryston.helion.render.rtx;

public final class RayTracing {
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
}
