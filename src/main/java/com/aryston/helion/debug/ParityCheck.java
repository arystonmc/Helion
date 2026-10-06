package com.aryston.helion.debug;

import com.aryston.helion.render.HelionRenderCore;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ParityCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ParityCheck INSTANCE = new ParityCheck();
    private static final int WARM_UP_FRAMES = 3;
    private static final int MAX_CAPTURE_ATTEMPTS = 20;

    private Phase phase = Phase.IDLE;
    private int warmUpFramesLeft;
    private int clientTicks;
    private int vanillaCaptureTick;
    private int captureAttempts;
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

    public void clientTick() {
        clientTicks++;
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
            case WARM_UP_VANILLA -> warmUp(Phase.WARM_UP_HELION);
            case WARM_UP_HELION -> warmUp(Phase.CAPTURE_VANILLA);
            case CAPTURE_VANILLA -> {
                vanillaFrame = FrameCapture.of(output, HelionRenderCore.get().resources());
                vanillaCaptureTick = clientTicks;
                phase = Phase.CAPTURE_HELION;
            }
            case CAPTURE_HELION -> captureHelion(output);
            case COMPARE -> compareWhenReady();
            case IDLE -> {
            }
        }
    }

    private void captureHelion(RenderTarget output) {
        if (clientTicks != vanillaCaptureTick && captureAttempts < MAX_CAPTURE_ATTEMPTS) {
            captureAttempts++;
            vanillaFrame = null;
            phase = Phase.CAPTURE_VANILLA;
            return;
        }
        helionFrame = FrameCapture.of(output, HelionRenderCore.get().resources());
        enter(Phase.COMPARE);
    }

    private void enter(Phase next) {
        phase = next;
        warmUpFramesLeft = WARM_UP_FRAMES;
        captureAttempts = 0;
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
        if (!result.isIdentical()) {
            writeDifferences(vanillaFrame.differencesTo(helionFrame));
        }
        vanillaFrame = null;
        helionFrame = null;
        enter(Phase.IDLE);
        reporter.accept(result);
    }

    private static void writeDifferences(ParityDifferenceImage image) {
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR);
        Util.ioPool().execute(() -> {
            try {
                LOGGER.info("Helion parity differences written to {}", image.writeTo(directory));
            } catch (IOException exception) {
                LOGGER.warn("Helion could not write the parity difference image", exception);
            }
        });
    }

    private enum Phase {
        IDLE,
        WARM_UP_VANILLA,
        WARM_UP_HELION,
        CAPTURE_VANILLA,
        CAPTURE_HELION,
        COMPARE
    }
}
