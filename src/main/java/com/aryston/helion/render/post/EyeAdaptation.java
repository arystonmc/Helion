package com.aryston.helion.render.post;

public final class EyeAdaptation {
    static final float TARGET_LOG2_LUMINANCE = -4.8F;
    static final float DARKEN_FROM_LOG2_LUMINANCE = -3.0F;
    static final float MIN_EXPOSURE = -1.5F;
    static final float MAX_EXPOSURE = 0.5F;
    private static final float NO_DARKENING = 0.0F;
    static final float BRIGHTEN_RATE = 1.0F;
    static final float DARKEN_RATE = 3.0F;
    private static final float NANOS_PER_SECOND = 1.0e9F;
    private static final float MAX_STEP_SECONDS = 0.25F;
    private static final long NO_FRAME = -1L;
    private static final float NEUTRAL_EXPOSURE = 0.0F;

    private float exposure;
    private float meteredLog2Luminance;
    private long lastFrameNanos = NO_FRAME;

    public float exposure() {
        return exposure;
    }

    public float meteredLog2Luminance() {
        return meteredLog2Luminance;
    }

    public void reset() {
        exposure = NEUTRAL_EXPOSURE;
        lastFrameNanos = NO_FRAME;
    }

    void update(float averageLog2Luminance, long frameNanos) {
        meteredLog2Luminance = averageLog2Luminance;
        float target = targetExposure(averageLog2Luminance);
        if (lastFrameNanos == NO_FRAME) {
            exposure = target;
            lastFrameNanos = frameNanos;
            return;
        }
        float seconds = Math.min((frameNanos - lastFrameNanos) / NANOS_PER_SECOND, MAX_STEP_SECONDS);
        lastFrameNanos = frameNanos;
        float rate = target > exposure ? BRIGHTEN_RATE : DARKEN_RATE;
        exposure += (target - exposure) * (1.0F - (float) Math.exp(-rate * seconds));
    }

    static float targetExposure(float averageLog2Luminance) {
        if (averageLog2Luminance > DARKEN_FROM_LOG2_LUMINANCE) {
            return Math.max(DARKEN_FROM_LOG2_LUMINANCE - averageLog2Luminance, MIN_EXPOSURE);
        }
        return Math.clamp(TARGET_LOG2_LUMINANCE - averageLog2Luminance, NO_DARKENING, MAX_EXPOSURE);
    }
}
