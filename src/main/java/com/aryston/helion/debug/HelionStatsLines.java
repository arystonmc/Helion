package com.aryston.helion.debug;

import com.aryston.helion.render.HelionRenderCore;
import com.aryston.helion.render.post.BloomSettings;
import com.aryston.helion.render.post.ImageResources;
import com.aryston.helion.render.post.ImageSettings;
import com.aryston.helion.render.post.SharpeningSettings;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class HelionStatsLines {
    private HelionStatsLines() {
    }

    static String timings(Map<String, Double> averageMillis) {
        return averageMillis.entrySet().stream()
            .map(entry -> String.format(Locale.ROOT, "%s %.2f ms", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(", "));
    }

    static double totalMillis(Map<String, Double> averageMillis) {
        return averageMillis.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    static Optional<String> image(HelionRenderCore core) {
        ImageSettings image = core.settings().image();
        return core.lastScene().map(scene -> String.format(
            Locale.ROOT,
            "Helion image: %s, exposure %+.1f EV, ambient light %.2f, bloom %s, sharpening %s",
            image.toneMapper().name().toLowerCase(Locale.ROOT),
            image.exposure(),
            scene.ambientLight(),
            bloom(image.bloom(), scene.ambientLight()),
            sharpening(image.sharpening())
        ));
    }

    private static String bloom(BloomSettings bloom, float ambientLight) {
        if (!bloom.enabled()) {
            return "off";
        }
        return String.format(Locale.ROOT, "threshold %.2f", ImageResources.bloomThreshold(bloom, ambientLight));
    }

    private static String sharpening(SharpeningSettings sharpening) {
        if (!sharpening.enabled()) {
            return "off";
        }
        return String.format(Locale.ROOT, "%.2f", sharpening.strength());
    }
}
