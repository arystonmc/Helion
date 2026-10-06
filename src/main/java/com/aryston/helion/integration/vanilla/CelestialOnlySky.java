package com.aryston.helion.integration.vanilla;

public final class CelestialOnlySky {
    private static boolean drawing;

    private CelestialOnlySky() {
    }

    public static boolean isDrawing() {
        return drawing;
    }

    static void draw(Runnable skyRendering) {
        drawing = true;
        try {
            skyRendering.run();
        } finally {
            drawing = false;
        }
    }
}
