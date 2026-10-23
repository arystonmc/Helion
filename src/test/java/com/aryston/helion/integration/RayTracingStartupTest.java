package com.aryston.helion.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RayTracingStartupTest {
    @Test
    void readsEnabledInsideTheRayTracingSection() {
        assertTrue(RayTracingStartup.configured(List.of(
            "[dynamicLights]",
            "    enabled = false",
            "",
            "[rayTracing]",
            "    #Builds a ray tracing copy of the blocks",
            "    enabled = true",
            "    waterReflections = true"
        )));
    }

    @Test
    void ignoresEnabledOfOtherSections() {
        assertFalse(RayTracingStartup.configured(List.of(
            "[shadows]",
            "    enabled = true",
            "[rayTracing]",
            "    enabled = false",
            "[clouds]",
            "    enabled = true"
        )));
    }

    @Test
    void staysOffWithoutTheSection() {
        assertFalse(RayTracingStartup.configured(List.of("[general]", "    enabled = true")));
    }
}
