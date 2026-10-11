package com.aryston.helion.render.post;

import java.nio.ByteBuffer;
import org.joml.Vector3f;

public final class ColorGradingLut {
    public static final int SIZE = 32;
    public static final int WIDTH = SIZE * SIZE;
    public static final int HEIGHT = SIZE;
    public static final int BYTES_PER_TEXEL = 4;
    private static final float MAX_CHANNEL = 255.0F;
    private static final int OPAQUE = 255;
    private static final float MIDDLE_GREY_LOG2 = (float) (Math.log(0.18) / Math.log(2.0));
    private static final float CONTRAST_FLOOR = 1.0e-4F;
    private static final float WARM_RED = 0.2F;
    private static final float WARM_BLUE = 0.25F;
    private static final float TINT_GREEN = 0.2F;
    private static final Vector3f LUMINANCE_WEIGHTS = new Vector3f(0.2126F, 0.7152F, 0.0722F);
    private static final float SRGB_LINEAR_LIMIT = 0.04045F;
    private static final float SRGB_LINEAR_SLOPE = 12.92F;
    private static final float SRGB_OFFSET = 0.055F;
    private static final float SRGB_SCALE = 1.055F;
    private static final float SRGB_EXPONENT = 2.4F;
    private static final float LINEAR_LIMIT = 0.0031308F;

    private ColorGradingLut() {
    }

    public static ByteBuffer build(ColorGrade grade, ByteBuffer target) {
        for (int green = 0; green < SIZE; green++) {
            for (int blue = 0; blue < SIZE; blue++) {
                for (int red = 0; red < SIZE; red++) {
                    Vector3f graded = grade(new Vector3f(coordinate(red), coordinate(green), coordinate(blue)), grade);
                    target.put(toByte(graded.x)).put(toByte(graded.y)).put(toByte(graded.z)).put((byte) OPAQUE);
                }
            }
        }
        return target.flip();
    }

    public static Vector3f grade(Vector3f display, ColorGrade grade) {
        Vector3f color = new Vector3f(toLinear(display.x), toLinear(display.y), toLinear(display.z));
        color.mul(1.0F + grade.temperature() * WARM_RED, 1.0F + grade.tint() * TINT_GREEN, 1.0F - grade.temperature() * WARM_BLUE);
        float luminance = color.dot(LUMINANCE_WEIGHTS);
        color.sub(luminance, luminance, luminance).mul(grade.saturation()).add(luminance, luminance, luminance);
        color.set(contrast(color.x, grade), contrast(color.y, grade), contrast(color.z, grade));
        color.set(liftGammaGain(color.x, grade), liftGammaGain(color.y, grade), liftGammaGain(color.z, grade));
        return new Vector3f(toDisplay(color.x), toDisplay(color.y), toDisplay(color.z));
    }

    static float coordinate(int index) {
        return index / (float) (SIZE - 1);
    }

    private static float contrast(float channel, ColorGrade grade) {
        float logValue = (float) (Math.log(Math.max(channel, CONTRAST_FLOOR)) / Math.log(2.0));
        return (float) Math.pow(2.0, MIDDLE_GREY_LOG2 + (logValue - MIDDLE_GREY_LOG2) * grade.contrast());
    }

    private static float liftGammaGain(float channel, ColorGrade grade) {
        float lifted = grade.gain() * (channel + grade.lift() * (1.0F - channel));
        return (float) Math.pow(Math.clamp(lifted, 0.0F, 1.0F), 1.0F / grade.gamma());
    }

    private static float toLinear(float display) {
        return display <= SRGB_LINEAR_LIMIT
            ? display / SRGB_LINEAR_SLOPE
            : (float) Math.pow((display + SRGB_OFFSET) / SRGB_SCALE, SRGB_EXPONENT);
    }

    private static float toDisplay(float linear) {
        float clamped = Math.clamp(linear, 0.0F, 1.0F);
        return clamped <= LINEAR_LIMIT
            ? clamped * SRGB_LINEAR_SLOPE
            : SRGB_SCALE * (float) Math.pow(clamped, 1.0 / SRGB_EXPONENT) - SRGB_OFFSET;
    }

    private static byte toByte(float channel) {
        return (byte) Math.round(Math.clamp(channel, 0.0F, 1.0F) * MAX_CHANNEL);
    }
}
