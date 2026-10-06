package com.aryston.helion.render.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicalSkyTest {
    private static final String ATMOSPHERE_INCLUDE = "/assets/helion/shaders/include/helion_atmosphere.glsl";
    private static final float TOLERANCE = 1.0e-5F;
    private static final float QUARTER_TURN = (float) (Math.PI / 2.0);
    private static final float ONE_KILOMETER = 1000.0F;
    private static final float BELOW_SEA_LEVEL = -40.0F;

    @Test
    void celestialBodyIsOverheadAtAngleZero() {
        Vector3f direction = SkyEnvironment.celestialDirection(0.0F);

        assertEquals(0.0F, direction.x, TOLERANCE);
        assertEquals(1.0F, direction.y, TOLERANCE);
        assertEquals(0.0F, direction.z, TOLERANCE);
    }

    @Test
    void celestialBodyRisesAndSetsOnOppositeSides() {
        Vector3f rising = SkyEnvironment.celestialDirection(-QUARTER_TURN);
        Vector3f setting = SkyEnvironment.celestialDirection(QUARTER_TURN);

        assertEquals(0.0F, rising.y, TOLERANCE);
        assertEquals(0.0F, setting.y, TOLERANCE);
        assertEquals(-rising.x, setting.x, TOLERANCE);
        assertEquals(1.0F, rising.length(), TOLERANCE);
    }

    @Test
    void viewHeightFollowsAltitudeAndStaysAboveTheGround() {
        float seaLevel = PhysicalSkyResources.viewHeight(0.0F);

        assertTrue(seaLevel > PhysicalSkyResources.PLANET_RADIUS_KILOMETERS);
        assertEquals(PhysicalSkyResources.PLANET_RADIUS_KILOMETERS + 1.0F, PhysicalSkyResources.viewHeight(ONE_KILOMETER), TOLERANCE);
        assertEquals(seaLevel, PhysicalSkyResources.viewHeight(BELOW_SEA_LEVEL), TOLERANCE);
    }

    @Test
    void javaConstantsMatchTheShader() throws IOException {
        String include = readInclude();

        assertEquals(PhysicalSkyResources.PLANET_RADIUS_KILOMETERS, floatConstant(include, "HELION_BOTTOM_RADIUS"), TOLERANCE);
        assertSize(include, "HELION_TRANSMITTANCE_SIZE", PhysicalSkyPipelines.TRANSMITTANCE_WIDTH, PhysicalSkyPipelines.TRANSMITTANCE_HEIGHT);
        assertSize(
            include, "HELION_MULTIPLE_SCATTERING_SIZE", PhysicalSkyPipelines.MULTIPLE_SCATTERING_SIZE, PhysicalSkyPipelines.MULTIPLE_SCATTERING_SIZE
        );
        assertSize(include, "HELION_SKY_VIEW_SIZE", PhysicalSkyPipelines.SKY_VIEW_WIDTH, PhysicalSkyPipelines.SKY_VIEW_HEIGHT);
    }

    private static void assertSize(String include, String name, int width, int height) {
        Matcher size = Pattern.compile("const vec2 " + name + " = vec2\\(([\\d.]+), ([\\d.]+)\\);").matcher(include);
        assertTrue(size.find(), name);
        assertEquals(width, Float.parseFloat(size.group(1)), TOLERANCE, name);
        assertEquals(height, Float.parseFloat(size.group(2)), TOLERANCE, name);
    }

    private static float floatConstant(String include, String name) {
        Matcher constant = Pattern.compile("const float " + name + " = ([\\d.]+);").matcher(include);
        assertTrue(constant.find(), name);
        return Float.parseFloat(constant.group(1));
    }

    private static String readInclude() throws IOException {
        try (InputStream stream = PhysicalSkyTest.class.getResourceAsStream(ATMOSPHERE_INCLUDE)) {
            assertNotNull(stream, ATMOSPHERE_INCLUDE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
