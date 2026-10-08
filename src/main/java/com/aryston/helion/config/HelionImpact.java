package com.aryston.helion.config;

import com.aryston.arkea.api.config.ConfigMeter;
import com.aryston.arkea.api.config.ConfigOption;
import com.aryston.arkea.api.config.ConfigValues;
import com.aryston.helion.render.lighting.AmbientOcclusionQuality;
import com.aryston.helion.render.shadow.ShadowQuality;
import java.util.List;
import net.minecraft.network.chat.Component;

final class HelionImpact {
    private static final String KEY = "helion.config.impact";
    private static final double QUALITY_WEIGHT = 0.5;
    private static final List<String> LEVELS = List.of("low", "medium", "high", "extreme");

    private HelionImpact() {
    }

    static ConfigMeter meter(HelionOptions options) {
        double maximum = maximum(options);
        return new ConfigMeter(Component.translatable(KEY), values -> fraction(options, values, maximum),
            values -> Component.translatable(KEY + "." + LEVELS.get(level(fraction(options, values, maximum)))),
            Component.translatable(KEY + ".note"));
    }

    private static double fraction(HelionOptions options, ConfigValues values, double maximum) {
        if (!values.value(options.enabled)) {
            return 0.0;
        }
        return Math.clamp(score(options, values) / maximum, 0.0, 1.0);
    }

    private static int level(double fraction) {
        return Math.min(LEVELS.size() - 1, (int) Math.floor(fraction * LEVELS.size()));
    }

    private static List<ConfigOption<Boolean>> features(HelionOptions options) {
        return List.of(options.ambientOcclusion, options.bloom, options.sharpening, options.temporal, options.geometryBuffer, options.lighting,
            options.physicalSky, options.shadows);
    }

    private static double score(HelionOptions options, ConfigValues values) {
        double score = 0.0;
        for (ConfigOption<Boolean> feature : features(options)) {
            if (values.value(feature)) {
                score += feature.cost();
            }
        }
        if (values.value(options.ambientOcclusion)) {
            score += QUALITY_WEIGHT * values.value(options.ambientOcclusionQuality).ordinal();
        }
        if (values.value(options.shadows)) {
            score += QUALITY_WEIGHT * values.value(options.shadowQuality).ordinal();
        }
        return score;
    }

    private static double maximum(HelionOptions options) {
        double maximum = 0.0;
        for (ConfigOption<Boolean> feature : features(options)) {
            maximum += feature.cost();
        }
        return maximum + QUALITY_WEIGHT * (AmbientOcclusionQuality.values().length - 1 + ShadowQuality.values().length - 1);
    }
}
