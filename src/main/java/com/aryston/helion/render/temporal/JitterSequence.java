package com.aryston.helion.render.temporal;

import org.joml.Vector2f;

public final class JitterSequence {
    public static final int BASE_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    private static final int HORIZONTAL_BASE = 2;
    private static final int VERTICAL_BASE = 3;
    private static final float CENTER = 0.5F;
    private static final float NDC_PER_PIXEL = 2.0F;

    private JitterSequence() {
    }

    public static int length(float upscaleRatio) {
        return Math.clamp(Math.round(BASE_LENGTH * upscaleRatio * upscaleRatio), BASE_LENGTH, MAX_LENGTH);
    }

    public static Vector2f pixelOffset(int frame, int length) {
        int index = Math.floorMod(frame, length) + 1;
        return new Vector2f(halton(index, HORIZONTAL_BASE) - CENTER, halton(index, VERTICAL_BASE) - CENTER);
    }

    public static Vector2f ndcOffset(Vector2f pixels, int width, int height) {
        return new Vector2f(pixels.x * NDC_PER_PIXEL / width, pixels.y * NDC_PER_PIXEL / height);
    }

    static float halton(int index, int base) {
        float result = 0.0F;
        float fraction = 1.0F;
        for (int remaining = index; remaining > 0; remaining /= base) {
            fraction /= base;
            result += fraction * (remaining % base);
        }
        return result;
    }
}
