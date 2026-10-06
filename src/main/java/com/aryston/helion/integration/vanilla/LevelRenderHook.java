package com.aryston.helion.integration.vanilla;

import com.aryston.helion.debug.ParityCheck;
import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.graph.RenderSettings;

public final class LevelRenderHook {
    private static final VanillaFrameDriver DRIVER = new VanillaFrameDriver();

    private LevelRenderHook() {
    }

    public static void render(LevelFrameRequest request, Runnable vanillaRenderer) {
        HelionRenderCore core = HelionRenderCore.get();
        ParityCheck parity = ParityCheck.get();
        boolean renderedByHelion = false;
        if (core.isActive() && !parity.forcesVanillaFrame()) {
            RenderSettings settings = parity.forcesFoundationFrame() ? core.settings().foundation() : core.settings();
            renderedByHelion = renderWithHelion(request, settings, core);
        } else {
            core.timings().pause();
            vanillaRenderer.run();
        }
        parity.afterLevelFrame(request.level().helion$gameRenderer().mainRenderTarget(), renderedByHelion);
    }

    public static void shutdown() {
        DRIVER.close();
    }

    private static boolean renderWithHelion(LevelFrameRequest request, RenderSettings settings, HelionRenderCore core) {
        try {
            DRIVER.render(request, settings);
            return true;
        } catch (RuntimeException failure) {
            core.reportFailure(failure);
            return false;
        }
    }
}
