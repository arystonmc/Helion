package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.temporal.DynamicRenderScale;
import com.mojang.blaze3d.platform.Monitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

final class FrameRateCeiling {
    private FrameRateCeiling() {
    }

    static int current() {
        Minecraft minecraft = Minecraft.getInstance();
        int limit = minecraft.getFramerateLimitTracker().getFramerateLimit();
        int ceiling = limit < Options.UNLIMITED_FRAMERATE_CUTOFF ? limit : DynamicRenderScale.UNLIMITED_FRAME_RATE;
        if (!minecraft.options.enableVsync().get()) {
            return ceiling;
        }
        Monitor monitor = minecraft.getWindow().findBestMonitor();
        return monitor == null ? ceiling : Math.min(ceiling, Math.round(monitor.currentMode().getRefreshRate()));
    }
}
