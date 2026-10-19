package com.aryston.helion.render.lighting;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AmbientOcclusionVariantsTest {
    private static final String HORIZON_SEARCH_SHADER = "/assets/helion/shaders/ambient_occlusion/gtao.fsh";
    private static final List<String> VARIANT_DEFINES = List.of(
        "HELION_AO_SLICE_COUNT", "HELION_AO_STEPS_PER_SLICE", "#ifdef HELION_AO_VISIBILITY_BITMASK"
    );

    @Test
    void horizonSearchReadsEveryVariantDefine() throws IOException {
        String shader = readShader();

        VARIANT_DEFINES.forEach(define -> assertTrue(shader.contains(define), define + " is not used in " + HORIZON_SEARCH_SHADER));
    }

    @Test
    void everyQualityHasSlicesAndSteps() {
        for (AmbientOcclusionQuality quality : AmbientOcclusionQuality.values()) {
            assertTrue(quality.slices() > 0 && quality.stepsPerSlice() > 0, quality.name());
        }
    }

    private static String readShader() throws IOException {
        try (InputStream stream = AmbientOcclusionVariantsTest.class.getResourceAsStream(HORIZON_SEARCH_SHADER)) {
            assertNotNull(stream, HORIZON_SEARCH_SHADER);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
