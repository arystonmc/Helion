package com.aryston.helion.config;

import com.aryston.arkea.api.config.ConfigPreset;
import com.aryston.arkea.ui.render.Icon;
import com.aryston.arkea.ui.render.Icons;
import com.aryston.helion.render.lighting.AmbientOcclusionQuality;
import com.aryston.helion.render.shadow.ShadowQuality;
import java.util.List;
import net.minecraft.network.chat.Component;

final class HelionPresets {
    private static final String KEY = "helion.config.preset.";

    private HelionPresets() {
    }

    static List<ConfigPreset> create(HelionOptions options) {
        ConfigPreset vanilla = base(options, "vanilla", Icons.CUBE, false, false, false, AmbientOcclusionQuality.LOW);
        ConfigPreset balanced = base(options, "balanced", Icons.GAUGE, true, true, false, AmbientOcclusionQuality.MEDIUM);
        ConfigPreset quality = base(options, "quality", Icons.SPARKLE, true, true, true, AmbientOcclusionQuality.HIGH);
        ConfigPreset experimental = base(options, "experimental", Icons.FLASK, true, true, true, AmbientOcclusionQuality.HIGH)
            .set(options.geometryBuffer, true)
            .set(options.lighting, true)
            .set(options.physicalSky, true)
            .set(options.shadows, true)
            .set(options.shadowQuality, ShadowQuality.MEDIUM)
            .set(options.temporal, true);
        for (ConfigPreset preset : List.of(vanilla, balanced, quality)) {
            preset.set(options.geometryBuffer, false)
                .set(options.lighting, false)
                .set(options.physicalSky, false)
                .set(options.shadows, false)
                .set(options.temporal, false);
        }
        return List.of(vanilla, balanced, quality, experimental);
    }

    private static ConfigPreset base(HelionOptions options, String id, Icon icon, boolean ambientOcclusion, boolean bloom, boolean sharpening,
        AmbientOcclusionQuality occlusionQuality) {
        return new ConfigPreset(Component.translatable(KEY + id), Component.translatable(KEY + id + ".description"), icon)
            .set(options.enabled, true)
            .set(options.ambientOcclusion, ambientOcclusion)
            .set(options.ambientOcclusionQuality, occlusionQuality)
            .set(options.bloom, bloom)
            .set(options.sharpening, sharpening);
    }
}
