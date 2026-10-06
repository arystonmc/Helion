package com.aryston.helion.render.graph;

import com.aryston.helion.render.lighting.AmbientOcclusionSettings;
import com.aryston.helion.render.post.ImageSettings;

public record RenderSettings(AmbientOcclusionSettings ambientOcclusion, ImageSettings image) {
    public static RenderSettings foundation() {
        return new RenderSettings(AmbientOcclusionSettings.DISABLED, ImageSettings.FOUNDATION);
    }
}
