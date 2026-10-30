package com.aryston.helion.render.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DynamicRenderScaleTest {
    private static final DynamicScaleSettings TARGET_144 = new DynamicScaleSettings(true, FrameRateTarget.FPS_144);
    private static final int UNCAPPED = DynamicRenderScale.UNLIMITED_FRAME_RATE;
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    private final DynamicRenderScale scale = new DynamicRenderScale();
    private long now;

    @Test
    void disabledKeepsTheChosenScale() {
        assertEquals(RenderScale.QUALITY, scale.update(DynamicScaleSettings.DISABLED, RenderScale.QUALITY, UNCAPPED, now));
        run(TARGET_144, RenderScale.QUALITY, 10, SECOND);
        assertEquals(RenderScale.QUALITY, scale.update(DynamicScaleSettings.DISABLED, RenderScale.QUALITY, UNCAPPED, now));
    }

    @Test
    void lowersOneStepPerWindowBelowTarget() {
        assertEquals(RenderScale.NATIVE, run(TARGET_144, RenderScale.NATIVE, 100, SECOND / 2));
        assertEquals(RenderScale.ULTRA_QUALITY, run(TARGET_144, RenderScale.NATIVE, 100, SECOND * 2));
        assertEquals(RenderScale.QUALITY, run(TARGET_144, RenderScale.NATIVE, 100, SECOND * 2 + SECOND / 5));
    }

    @Test
    void neverGoesBelowPerformance() {
        assertEquals(RenderScale.PERFORMANCE, run(TARGET_144, RenderScale.NATIVE, 60, SECOND * 20));
    }

    @Test
    void raisesWhenTheTargetIsHeld() {
        run(TARGET_144, RenderScale.QUALITY, 200, SECOND);
        assertEquals(RenderScale.NATIVE, run(TARGET_144, RenderScale.QUALITY, 200, SECOND * 12));
    }

    @Test
    void holdsAtTheFrameRateCap() {
        run(TARGET_144, RenderScale.QUALITY, 60, SECOND, 60);
        assertEquals(RenderScale.NATIVE, run(TARGET_144, RenderScale.QUALITY, 60, SECOND * 12, 60));
    }

    @Test
    void stalledFramesDoNotLowerTheScale() {
        run(TARGET_144, RenderScale.NATIVE, 200, SECOND);
        now += SECOND;
        assertEquals(RenderScale.NATIVE, run(TARGET_144, RenderScale.NATIVE, 200, SECOND));
    }

    @Test
    void changedSettingsRestartFromTheChosenScale() {
        run(TARGET_144, RenderScale.NATIVE, 60, SECOND * 10);
        assertEquals(RenderScale.BALANCED, scale.update(TARGET_144, RenderScale.BALANCED, UNCAPPED, now));
    }

    private RenderScale run(DynamicScaleSettings settings, RenderScale configured, int framesPerSecond, long duration) {
        return run(settings, configured, framesPerSecond, duration, UNCAPPED);
    }

    private RenderScale run(DynamicScaleSettings settings, RenderScale configured, int framesPerSecond, long duration, int ceiling) {
        long frame = SECOND / framesPerSecond;
        long end = now + duration;
        RenderScale result = scale.update(settings, configured, ceiling, now);
        while (now < end) {
            now += frame;
            result = scale.update(settings, configured, ceiling, now);
        }
        return result;
    }
}
