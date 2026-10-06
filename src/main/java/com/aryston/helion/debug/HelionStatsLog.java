package com.aryston.helion.debug;

import com.aryston.helion.render.HelionRenderCore;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public final class HelionStatsLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INTERVAL_TICKS = 100;

    private int ticksUntilReport = INTERVAL_TICKS;

    public void tick(Minecraft minecraft) {
        if (FMLEnvironment.isProduction() || minecraft.level == null) {
            return;
        }
        ticksUntilReport--;
        if (ticksUntilReport > 0) {
            return;
        }
        ticksUntilReport = INTERVAL_TICKS;
        HelionRenderCore core = HelionRenderCore.get();
        Map<String, Double> timings = core.timings().averageMillis();
        if (!core.isActive() || timings.isEmpty()) {
            return;
        }
        Window window = minecraft.getWindow();
        LOGGER.info(String.format(
            Locale.ROOT,
            "Helion stats: %d fps at %dx%d, Helion GPU total %.2f ms",
            minecraft.getFps(),
            window.getWidth(),
            window.getHeight(),
            HelionStatsLines.totalMillis(timings)
        ));
        LOGGER.info("Helion stages: {}", HelionStatsLines.timings(timings));
        HelionStatsLines.image(core).ifPresent(LOGGER::info);
    }
}
