package com.aryston.helion.render.atmosphere;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public record SkyEnvironment(Vector3fc sunDirection, Vector3fc moonDirection, float rainBrightness, float altitude, AtmosphereFog fog) {
    private static final float CELESTIAL_YAW = (float) Math.toRadians(-90.0);

    public SkyEnvironment {
        sunDirection = new Vector3f(sunDirection);
        moonDirection = new Vector3f(moonDirection);
    }

    public static Vector3f celestialDirection(float angle) {
        return new Vector3f(0.0F, 1.0F, 0.0F).rotateX(angle).rotateY(CELESTIAL_YAW);
    }
}
