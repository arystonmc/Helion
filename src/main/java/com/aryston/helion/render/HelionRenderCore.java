package com.aryston.helion.render;

import com.aryston.helion.render.backend.GpuDeviceSummary;
import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.graph.GpuTimings;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.SceneTargets;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class HelionRenderCore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HelionRenderCore INSTANCE = new HelionRenderCore();

    private final GpuResources resources = new GpuResources();
    private final SceneTargets sceneTargets = new SceneTargets(resources);
    private final GpuTimings timings = new GpuTimings(resources);
    private PassiveListener passiveListener = reason -> { };
    private @Nullable GpuDeviceSummary device;
    private @Nullable PassiveReason passiveReason;
    private @Nullable RenderTarget mainTargetOverride;
    private @Nullable HelionCamera lastCamera;
    private boolean enabled = true;
    private boolean measureGpuTimings = true;

    private HelionRenderCore() {
    }

    public static HelionRenderCore get() {
        return INSTANCE;
    }

    public void setPassiveListener(PassiveListener listener) {
        passiveListener = listener;
    }

    public void applySettings(boolean enabledSetting, boolean gpuTimingsSetting) {
        enabled = enabledSetting;
        measureGpuTimings = gpuTimingsSetting;
    }

    public boolean isActive() {
        detectDevice();
        return passiveReason == null && enabled;
    }

    public boolean toggle() {
        enabled = !enabled;
        LOGGER.info("Helion render core {}", enabled ? "enabled" : "disabled");
        return enabled;
    }

    public void passivate(PassiveReason reason) {
        if (passiveReason != null) {
            return;
        }
        passiveReason = reason;
        LOGGER.warn("Helion render core is passive: {}", reason);
        passiveListener.onPassive(reason);
    }

    public void reportFailure(Throwable failure) {
        LOGGER.error("Helion render core failed and falls back to vanilla rendering", failure);
        passivate(PassiveReason.RENDER_FAILURE);
    }

    public RenderTarget beginLevelFrame(RenderTarget mainTarget) {
        RenderTarget scene = sceneTargets.matching(mainTarget);
        GpuDeviceSummary summary = detectDevice();
        if (measureGpuTimings && summary.supportsTimestamps()) {
            timings.beginFrame(summary.timestampPeriod());
        }
        mainTargetOverride = scene;
        return scene;
    }

    public void endLevelFrame() {
        mainTargetOverride = null;
    }

    public void recordCamera(HelionCamera camera) {
        lastCamera = camera;
    }

    public Optional<HelionCamera> lastCamera() {
        return Optional.ofNullable(lastCamera);
    }

    public RenderTarget resolveMainTarget(RenderTarget original) {
        return mainTargetOverride != null ? mainTargetOverride : original;
    }

    public void shutdown() {
        int released = resources.releaseAll();
        timings.close();
        sceneTargets.close();
        LOGGER.info("Helion released {} GPU resources on shutdown", released);
    }

    public Optional<PassiveReason> passiveReason() {
        return Optional.ofNullable(passiveReason);
    }

    public Optional<GpuDeviceSummary> device() {
        return Optional.ofNullable(device);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public GpuTimings timings() {
        return timings;
    }

    public GpuResources resources() {
        return resources;
    }

    private GpuDeviceSummary detectDevice() {
        if (device == null) {
            device = GpuDeviceSummary.of(RenderSystem.getDevice().getDeviceInfo());
            LOGGER.info("Helion detected {} on {} ({})", device.backend(), device.name(), device.driver());
            if (!device.isVulkan()) {
                passivate(PassiveReason.NOT_VULKAN);
            }
        }
        return device;
    }
}
