package com.aryston.helion.debug;

import com.aryston.helion.config.HelionConfig;
import com.aryston.helion.config.HelionPresetSettings;
import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.graph.RenderSettings;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public final class GpuProfile {
    private static final String PROPERTY = "helion.gpuProfile";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int START_DELAY_TICKS = 300;
    private static final int SETTLE_TICKS = 100;
    private static final int MEASURE_TICKS = 200;
    private static final String HELION_OFF = "Helion off";
    private static final boolean MEASURE_GPU = true;
    private static final String STAGE_FORMAT = "%s %.3f";

    private final List<Step> steps = new ArrayList<>();
    private int current;
    private boolean started;
    private int ticksUntilAction = START_DELAY_TICKS;
    private boolean measuring;
    private long fpsSum;
    private int fpsSamples;
    private float yaw;
    private float pitch;
    private boolean finished;
    private boolean hudWasHidden;

    private GpuProfile() {
    }

    public static Optional<GpuProfile> requested() {
        if (FMLEnvironment.isProduction() || !Boolean.getBoolean(PROPERTY)) {
            return Optional.empty();
        }
        return Optional.of(new GpuProfile());
    }

    public void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (finished || player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        minecraft.gui.setScreen(null);
        minecraft.getFramerateLimitTracker().onInputReceived();
        if (started) {
            holdCamera(player);
        }
        if (measuring) {
            fpsSum += minecraft.getFps();
            fpsSamples++;
        }
        ticksUntilAction--;
        if (ticksUntilAction > 0) {
            return;
        }
        if (!started) {
            start(minecraft, player);
        } else if (!measuring) {
            beginMeasuring();
        } else {
            finishStep(minecraft);
        }
    }

    private void start(Minecraft minecraft, LocalPlayer player) {
        started = true;
        minecraft.options.pauseOnLostFocus = false;
        hudWasHidden = minecraft.gui.hud.isHidden();
        if (!hudWasHidden) {
            minecraft.gui.hud.toggle();
        }
        yaw = player.getYRot();
        pitch = player.getXRot();
        steps.add(new Step(HELION_OFF, false, RenderSettings.OFF));
        HelionPresetSettings.capture().forEach(preset -> steps.add(new Step(preset.name(), true, preset.settings())));
        LOGGER.info("Helion GPU profile started with {} steps at {}x{}", steps.size(),
            minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        beginStep(steps.get(current));
    }

    private void finishStep(Minecraft minecraft) {
        report(steps.get(current));
        current++;
        if (current == steps.size()) {
            finish(minecraft);
            return;
        }
        beginStep(steps.get(current));
    }

    private void beginStep(Step step) {
        HelionRenderCore.get().applySettings(step.helion(), MEASURE_GPU, step.settings());
        measuring = false;
        ticksUntilAction = SETTLE_TICKS;
    }

    private void beginMeasuring() {
        measuring = true;
        fpsSum = 0;
        fpsSamples = 0;
        ticksUntilAction = MEASURE_TICKS;
    }

    private void report(Step step) {
        Map<String, Double> stages = HelionRenderCore.get().timings().averageMillis();
        double total = stages.values().stream().mapToDouble(Double::doubleValue).sum();
        String detail = stages.entrySet().stream()
            .map(entry -> String.format(Locale.ROOT, STAGE_FORMAT, entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(", "));
        double fps = fpsSamples == 0 ? 0.0 : (double) fpsSum / fpsSamples;
        LOGGER.info(String.format(Locale.ROOT, "Helion GPU profile %s: %.1f FPS, %.3f ms measured GPU time [%s]", step.name(), fps, total, detail));
    }

    private void holdCamera(LocalPlayer player) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    private void finish(Minecraft minecraft) {
        finished = true;
        HelionRenderCore.get().applySettings(
            HelionConfig.ENABLED.getAsBoolean(),
            HelionConfig.DEBUG_MODE.getAsBoolean(),
            HelionConfig.renderSettings()
        );
        if (!hudWasHidden) {
            minecraft.gui.hud.toggle();
        }
        LOGGER.info("Helion GPU profile finished");
        minecraft.stop();
    }

    private record Step(String name, boolean helion, RenderSettings settings) {
    }
}
