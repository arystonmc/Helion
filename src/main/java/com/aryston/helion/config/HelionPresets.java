package com.aryston.helion.config;

import com.aryston.arkea.api.config.ConfigPreset;
import com.aryston.arkea.ui.render.Icon;
import com.aryston.arkea.ui.render.Icons;
import com.aryston.helion.render.clouds.CloudQuality;
import java.util.List;
import net.minecraft.network.chat.Component;

final class HelionPresets {
    private static final String KEY = "helion.config.preset.";

    private HelionPresets() {
    }

    static List<ConfigPreset> create(HelionOptions options) {
        ConfigPreset vanilla = base(options, "vanilla", Icons.CUBE, false, false);
        ConfigPreset balanced = base(options, "balanced", Icons.GAUGE, true, false);
        ConfigPreset quality = base(options, "quality", Icons.SPARKLE, true, true);
        ConfigPreset experimental = base(options, "experimental", Icons.FLASK, true, true)
            .set(options.geometryBuffer, true)
            .set(options.physicalSky, true)
            .set(options.lightShafts, true)
            .set(options.clouds, true)
            .set(options.cloudQuality, CloudQuality.MEDIUM)
            .set(options.temporal, true);
        for (ConfigPreset preset : List.of(vanilla, balanced, quality)) {
            preset.set(options.geometryBuffer, false)
                .set(options.physicalSky, false)
                .set(options.lightShafts, false)
                .set(options.clouds, false)
                .set(options.temporal, false);
        }
        vanilla.set(options.motionBlur, false)
            .set(options.depthOfField, false);
        for (ConfigPreset preset : List.of(vanilla, balanced, quality, experimental)) {
            preset.set(options.edgeSmoothing, preset == quality)
                .set(options.autoExposure, preset == experimental)
                .set(options.rayTracing, preset == experimental)
                .set(options.pathTracing, preset == experimental)
                .set(options.waterReflections, true)
                .set(options.screenReflections, preset == experimental)
                .set(options.rainWetness, preset != vanilla)
                .set(options.realisticWater, preset != vanilla);
        }
        return List.of(vanilla, balanced, quality, experimental);
    }

    private static ConfigPreset base(HelionOptions options, String id, Icon icon, boolean bloom, boolean sharpening) {
        return new ConfigPreset(Component.translatable(KEY + id), Component.translatable(KEY + id + ".description"), icon)
            .set(options.enabled, true)
            .set(options.bloom, bloom)
            .set(options.sharpening, sharpening);
    }
}
