package com.aryston.helion.debug;

import com.aryston.helion.render.HelionRenderCore;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class ParityCheck {
    private static final ParityCheck INSTANCE = new ParityCheck();

    private Phase phase = Phase.IDLE;
    private @Nullable FrameCapture vanillaFrame;
    private @Nullable FrameCapture helionFrame;
    private Consumer<ParityResult> reporter = result -> { };

    private ParityCheck() {
    }

    public static ParityCheck get() {
        return INSTANCE;
    }

    public boolean start(Consumer<ParityResult> resultReporter) {
        if (phase != Phase.IDLE) {
            return false;
        }
        reporter = resultReporter;
        phase = Phase.CAPTURE_VANILLA;
        return true;
    }

    public boolean forcesVanillaFrame() {
        return phase == Phase.CAPTURE_VANILLA;
    }

    public void afterLevelFrame(RenderTarget output) {
        switch (phase) {
            case CAPTURE_VANILLA -> {
                vanillaFrame = FrameCapture.of(output, HelionRenderCore.get().resources());
                phase = Phase.CAPTURE_HELION;
            }
            case CAPTURE_HELION -> {
                helionFrame = FrameCapture.of(output, HelionRenderCore.get().resources());
                phase = Phase.COMPARE;
            }
            case COMPARE -> compareWhenReady();
            case IDLE -> {
            }
        }
    }

    private void compareWhenReady() {
        if (vanillaFrame == null || helionFrame == null || !vanillaFrame.isComplete() || !helionFrame.isComplete()) {
            return;
        }
        ParityResult result = vanillaFrame.compareWith(helionFrame);
        vanillaFrame = null;
        helionFrame = null;
        phase = Phase.IDLE;
        reporter.accept(result);
    }

    private enum Phase {
        IDLE,
        CAPTURE_VANILLA,
        CAPTURE_HELION,
        COMPARE
    }
}
