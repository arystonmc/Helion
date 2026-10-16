package com.aryston.helion.render.shadow;

import java.util.Optional;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record ShadowLight(Vector3fc direction, boolean moon, float strength) {
    static final float FADE_START_ELEVATION = 0.02F;
    static final float FADE_END_ELEVATION = 0.12F;
    static final float DIRECTION_STEP = (float) Math.toRadians(0.1);

    public ShadowLight {
        direction = new Vector3f(direction).normalize();
    }

    public static Optional<ShadowLight> choose(Vector3fc sunDirection, Vector3fc moonDirection) {
        if (sunDirection.y() > FADE_START_ELEVATION) {
            return Optional.of(new ShadowLight(stepped(sunDirection), false, fade(sunDirection.y())));
        }
        if (moonDirection.y() > FADE_START_ELEVATION) {
            return Optional.of(new ShadowLight(stepped(moonDirection), true, fade(moonDirection.y())));
        }
        return Optional.empty();
    }

    public float sunStrength() {
        return moon ? 0.0F : strength;
    }

    public float moonStrength() {
        return moon ? strength : 0.0F;
    }

    static Vector3f stepped(Vector3fc direction) {
        Vector3f unit = new Vector3f(direction).normalize();
        float elevation = toStep((float) Math.asin(Math.clamp(unit.y, -1.0F, 1.0F)));
        float azimuth = toStep((float) Math.atan2(unit.z, unit.x));
        float horizontal = (float) Math.cos(elevation);
        return new Vector3f(horizontal * (float) Math.cos(azimuth), (float) Math.sin(elevation), horizontal * (float) Math.sin(azimuth));
    }

    private static float toStep(float angle) {
        return Math.round(angle / DIRECTION_STEP) * DIRECTION_STEP;
    }

    private static float fade(float elevation) {
        float amount = Math.clamp((elevation - FADE_START_ELEVATION) / (FADE_END_ELEVATION - FADE_START_ELEVATION), 0.0F, 1.0F);
        return amount * amount * (3.0F - 2.0F * amount);
    }
}
