package com.aryston.helion.config;

import com.aryston.arkea.api.config.ConfigPreset;
import com.aryston.arkea.ui.render.Icon;
import com.aryston.arkea.ui.render.Icons;
import com.aryston.helion.render.clouds.CloudQuality;
import com.aryston.helion.render.temporal.RenderScale;
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
        ConfigPreset performance = scaledSky(base(options, "performance", Icons.BOLT, true, true), options, CloudQuality.LOW);
        ConfigPreset experimental = scaledSky(base(options, "experimental", Icons.FLASK, true, true), options, CloudQuality.MEDIUM);
        for (ConfigPreset preset : List.of(vanilla, balanced, quality)) {
            preset.set(options.geometryBuffer, false)
                .set(options.physicalSky, false)
                .set(options.lightShafts, false)
                .set(options.clouds, false)
                .set(options.temporal, false);
        }
        for (ConfigPreset preset : List.of(vanilla, performance)) {
            preset.set(options.motionBlur, false)
                .set(options.depthOfField, false);
        }
        for (ConfigPreset preset : List.of(vanilla, balanced, quality, performance, experimental)) {
            preset.set(options.edgeSmoothing, preset == quality)
                .set(options.autoExposure, preset == experimental)
                .set(options.rayTracing, preset == experimental)
                .set(options.pathTracing, preset == experimental)
                .set(options.waterReflections, true)
                .set(options.screenReflections, preset == experimental)
                .set(options.rainWetness, preset != vanilla)
                .set(options.realisticWater, preset != vanilla);
        }
        return List.of(vanilla, balanced, quality, performance, experimental);
    }

    private static ConfigPreset base(HelionOptions options, String id, Icon icon, boolean bloom, boolean sharpening) {
        return new ConfigPreset(Component.translatable(KEY + id), Component.translatable(KEY + id + ".description"), icon)
            .set(options.enabled, true)
            .set(options.bloom, bloom)
            .set(options.sharpening, sharpening);
    }

    private static ConfigPreset scaledSky(ConfigPreset preset, HelionOptions options, CloudQuality cloudQuality) {
        return preset.set(options.geometryBuffer, true)
            .set(options.physicalSky, true)
            .set(options.lightShafts, true)
            .set(options.clouds, true)
            .set(options.cloudQuality, cloudQuality)
            .set(options.temporal, true)
            .set(options.renderScale, RenderScale.QUALITY);
    }
}
