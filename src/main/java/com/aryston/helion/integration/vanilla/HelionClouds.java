package com.aryston.helion.integration.vanilla;

public final class HelionClouds {
    private static volatile boolean replacingVanilla;

    private HelionClouds() {
    }

    public static boolean isReplacingVanilla() {
        return replacingVanilla;
    }

    static void replaceVanilla(boolean replace) {
        replacingVanilla = replace;
    }
}
