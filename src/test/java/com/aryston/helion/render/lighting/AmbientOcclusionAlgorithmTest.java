package com.aryston.helion.render.lighting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AmbientOcclusionAlgorithmTest {
    private static final String AMBIENT_OCCLUSION_INCLUDE = "/assets/helion/shaders/include/helion_ambient_occlusion.glsl";
    private static final Pattern SHADER_CONSTANT = Pattern.compile("const int HELION_AO_ALGORITHM_(\\w+) = (\\d+);");

    @Test
    void shaderIdsAreUnique() {
        long distinct = Arrays.stream(AmbientOcclusionAlgorithm.values()).mapToInt(AmbientOcclusionAlgorithm::shaderId).distinct().count();

        assertEquals(AmbientOcclusionAlgorithm.values().length, distinct);
    }

    @Test
    void shaderIdsMatchTheShaderConstants() throws IOException {
        Matcher constants = SHADER_CONSTANT.matcher(readInclude());
        boolean found = false;
        while (constants.find()) {
            found = true;
            assertEquals(Integer.parseInt(constants.group(2)), AmbientOcclusionAlgorithm.valueOf(constants.group(1)).shaderId(), constants.group());
        }

        assertTrue(found, "no algorithm constants found in " + AMBIENT_OCCLUSION_INCLUDE);
    }

    private static String readInclude() throws IOException {
        try (InputStream stream = AmbientOcclusionAlgorithmTest.class.getResourceAsStream(AMBIENT_OCCLUSION_INCLUDE)) {
            assertNotNull(stream, AMBIENT_OCCLUSION_INCLUDE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
