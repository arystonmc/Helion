package com.aryston.helion.render.post;

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

class ToneMapperTest {
    private static final String IMAGE_INCLUDE = "/assets/helion/shaders/include/helion_image.glsl";
    private static final Pattern SHADER_CONSTANT = Pattern.compile("const int HELION_TONE_MAPPER_(\\w+) = (\\d+);");

    @Test
    void shaderIdsAreUnique() {
        long distinct = Arrays.stream(ToneMapper.values()).mapToInt(ToneMapper::shaderId).distinct().count();

        assertEquals(ToneMapper.values().length, distinct);
    }

    @Test
    void shaderIdsMatchTheShaderConstants() throws IOException {
        Matcher constants = SHADER_CONSTANT.matcher(readImageInclude());
        boolean found = false;
        while (constants.find()) {
            found = true;
            assertEquals(Integer.parseInt(constants.group(2)), ToneMapper.valueOf(constants.group(1)).shaderId(), constants.group());
        }

        assertTrue(found, "no tone mapper constants found in " + IMAGE_INCLUDE);
    }

    private static String readImageInclude() throws IOException {
        try (InputStream stream = ToneMapperTest.class.getResourceAsStream(IMAGE_INCLUDE)) {
            assertNotNull(stream, IMAGE_INCLUDE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
