package com.aryston.helion.render.temporal;

import java.util.concurrent.TimeUnit;

public final class DynamicRenderScale {
    public static final int UNLIMITED_FRAME_RATE = Integer.MAX_VALUE;
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final long FIRST_RAISE_DELAY_NANOS = TimeUnit.SECONDS.toNanos(4);
    private static final long MAX_RAISE_DELAY_NANOS = TimeUnit.SECONDS.toNanos(64);
    private static final long STALLED_FRAME_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final int SETTLING_FRAMES = 10;
    private static final double LOWER_BELOW_TARGET = 0.92;
    private static final double RAISE_AT_TARGET = 0.98;
    private static final RenderScale[] STEPS = RenderScale.values();

    private DynamicScaleSettings settings = DynamicScaleSettings.DISABLED;
    private RenderScale configured = RenderScale.NATIVE;
    private RenderScale current = RenderScale.NATIVE;
    private long lastFrameNanos;
    private long windowStartNanos;
    private int windowFrames;
    private int settlingFrames;
    private long lastChangeNanos;
    private long raiseDelayNanos = FIRST_RAISE_DELAY_NANOS;
    private boolean lastChangeRaised;

    public RenderScale update(DynamicScaleSettings newSettings, RenderScale newConfigured, int frameRateCeiling, long nowNanos) {
        if (!newSettings.enabled()) {
            settings = newSettings;
            configured = newConfigured;
            current = newConfigured;
            return current;
        }
        if (!newSettings.equals(settings) || newConfigured != configured) {
            restart(newSettings, newConfigured, nowNanos);
            return current;
        }
        long frameNanos = nowNanos - lastFrameNanos;
        lastFrameNanos = nowNanos;
        if (frameNanos > STALLED_FRAME_NANOS) {
            settle(nowNanos);
            return current;
        }
        if (settlingFrames > 0) {
            settlingFrames--;
            startWindow(nowNanos);
            return current;
        }
        windowFrames++;
        long elapsed = nowNanos - windowStartNanos;
        if (elapsed >= WINDOW_NANOS) {
            adjust((double) windowFrames * NANOS_PER_SECOND / elapsed, targetFrameRate(frameRateCeiling), nowNanos);
            startWindow(nowNanos);
        }
        return current;
    }

    public RenderScale current() {
        return current;
    }

    private void adjust(double framesPerSecond, int targetFrameRate, long nowNanos) {
        int index = current.ordinal();
        if (framesPerSecond < targetFrameRate * LOWER_BELOW_TARGET && index < STEPS.length - 1) {
            if (lastChangeRaised) {
                raiseDelayNanos = Math.min(MAX_RAISE_DELAY_NANOS, raiseDelayNanos * 2);
            }
            change(STEPS[index + 1], false, nowNanos);
            return;
        }
        lastChangeRaised = false;
        if (framesPerSecond >= targetFrameRate * RAISE_AT_TARGET && index > 0 && nowNanos - lastChangeNanos >= raiseDelayNanos) {
            change(STEPS[index - 1], true, nowNanos);
        }
    }

    private void change(RenderScale scale, boolean raised, long nowNanos) {
        current = scale;
        lastChangeRaised = raised;
        lastChangeNanos = nowNanos;
        settle(nowNanos);
    }

    private int targetFrameRate(int frameRateCeiling) {
        return Math.min(settings.target().framesPerSecond(), frameRateCeiling);
    }

    private void restart(DynamicScaleSettings newSettings, RenderScale newConfigured, long nowNanos) {
        settings = newSettings;
        configured = newConfigured;
        current = newConfigured;
        lastFrameNanos = nowNanos;
        lastChangeNanos = nowNanos;
        lastChangeRaised = false;
        raiseDelayNanos = FIRST_RAISE_DELAY_NANOS;
        settle(nowNanos);
    }

    private void settle(long nowNanos) {
        settlingFrames = SETTLING_FRAMES;
        startWindow(nowNanos);
    }

    private void startWindow(long nowNanos) {
        windowStartNanos = nowNanos;
        windowFrames = 0;
    }
}
