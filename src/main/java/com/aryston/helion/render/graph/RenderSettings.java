package com.aryston.helion.render.graph;

import com.aryston.helion.render.lighting.AmbientOcclusionSettings;

public record RenderSettings(AmbientOcclusionSettings ambientOcclusion) {
    public static RenderSettings foundation() {
        return new RenderSettings(AmbientOcclusionSettings.DISABLED);
    }
}
