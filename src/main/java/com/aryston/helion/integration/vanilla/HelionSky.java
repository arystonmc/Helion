package com.aryston.helion.integration.vanilla;

import org.joml.Vector4f;

public final class HelionSky {
    private static final float MOON_BRIGHTNESS = 0.6F;
    private static boolean drawing;
    private static boolean celestialsOnly;

    private HelionSky() {
    }

    public static boolean isDrawingCelestialsOnly() {
        return drawing && celestialsOnly;
    }

    public static Vector4f moonColor(Vector4f vanilla) {
        if (!drawing) {
            return vanilla;
        }
        return new Vector4f(vanilla.x() * MOON_BRIGHTNESS, vanilla.y() * MOON_BRIGHTNESS, vanilla.z() * MOON_BRIGHTNESS, vanilla.w());
    }

    static void draw(Runnable skyRendering, boolean onlyCelestials) {
        drawing = true;
        celestialsOnly = onlyCelestials;
        try {
            skyRendering.run();
        } finally {
            drawing = false;
            celestialsOnly = false;
        }
    }
}
