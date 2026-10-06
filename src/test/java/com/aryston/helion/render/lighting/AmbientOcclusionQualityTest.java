package com.aryston.helion.render.lighting;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AmbientOcclusionQualityTest {
    @Test
    void higherQualityNeverUsesFewerSamplesOrPasses() {
        AmbientOcclusionQuality[] presets = AmbientOcclusionQuality.values();
        for (int index = 1; index < presets.length; index++) {
            AmbientOcclusionQuality lower = presets[index - 1];
            AmbientOcclusionQuality higher = presets[index];
            assertTrue(higher.slices() >= lower.slices(), higher + " slices");
            assertTrue(higher.stepsPerSlice() >= lower.stepsPerSlice(), higher + " steps per slice");
            assertTrue(higher.denoisePasses() >= lower.denoisePasses(), higher + " denoise passes");
            assertTrue(higher.slices() * higher.stepsPerSlice() > lower.slices() * lower.stepsPerSlice(), higher + " samples");
        }
    }
}
