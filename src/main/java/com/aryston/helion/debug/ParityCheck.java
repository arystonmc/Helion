package com.aryston.helion.debug;

import com.aryston.helion.render.HelionRenderCore;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class ParityCheck {
    private static final ParityCheck INSTANCE = new ParityCheck();
    private static final int WARM_UP_FRAMES = 3;

    private Phase phase = Phase.IDLE;
    private int warmUpFramesLeft;
    private @Nullable FrameCapture vanillaFrame;
    private @Nullable FrameCapture helionFrame;
    private Consumer<ParityResult> reporter = result -> { };
    private Runnable abortReporter = () -> { };

    private ParityCheck() {
    }

    public static ParityCheck get() {
        return INSTANCE;
    }

    public boolean start(Consumer<ParityResult> resultReporter, Runnable abortedReporter) {
        if (phase != Phase.IDLE) {
            return false;
        }
        reporter = resultReporter;
        abortReporter = abortedReporter;
        enter(Phase.WARM_UP_VANILLA);
        return true;
    }

    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    public boolean forcesVanillaFrame() {
        return phase == Phase.WARM_UP_VANILLA || phase == Phase.CAPTURE_VANILLA;
    }

    public boolean forcesFoundationFrame() {
        return phase == Phase.WARM_UP_HELION || phase == Phase.CAPTURE_HELION;
    }

    public void afterLevelFrame(RenderTarget output, boolean renderedByHelion) {
        if (forcesFoundationFrame() && !renderedByHelion) {
            abort();
            return;
        }
        switch (phase) {
            case WARM_UP_VANILLA -> warmUp(Phase.CAPTURE_VANILLA);
            case CAPTURE_VANILLA -> {
                vanillaFrame = FrameCapture.of(output, HelionRenderCore.get().resources());
                enter(Phase.WARM_UP_HELION);
            }
            case WARM_UP_HELION -> warmUp(Phase.CAPTURE_HELION);
            case CAPTURE_HELION -> {
                helionFrame = FrameCapture.of(output, HelionRenderCore.get().resources());
                enter(Phase.COMPARE);
            }
            case COMPARE -> compareWhenReady();
            case IDLE -> {
            }
        }
    }

    private void enter(Phase next) {
        phase = next;
        warmUpFramesLeft = WARM_UP_FRAMES;
    }

    private void warmUp(Phase next) {
        warmUpFramesLeft--;
        if (warmUpFramesLeft <= 0) {
            enter(next);
        }
    }

    private void abort() {
        vanillaFrame = null;
        helionFrame = null;
        enter(Phase.IDLE);
        abortReporter.run();
    }

    private void compareWhenReady() {
        if (vanillaFrame == null || helionFrame == null || !vanillaFrame.isComplete() || !helionFrame.isComplete()) {
            return;
        }
        ParityResult result = vanillaFrame.compareWith(helionFrame);
        vanillaFrame = null;
        helionFrame = null;
        enter(Phase.IDLE);
        reporter.accept(result);
    }

    private enum Phase {
        IDLE,
        WARM_UP_VANILLA,
        CAPTURE_VANILLA,
        WARM_UP_HELION,
        CAPTURE_HELION,
        COMPARE
    }
}
