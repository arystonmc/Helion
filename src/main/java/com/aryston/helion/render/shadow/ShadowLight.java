package com.aryston.helion.render.shadow;

import java.util.Optional;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record ShadowLight(Vector3fc direction, boolean moon, float strength) {
    static final float FADE_START_ELEVATION = 0.02F;
    static final float FADE_END_ELEVATION = 0.12F;

    public ShadowLight {
        direction = new Vector3f(direction).normalize();
    }

    public static Optional<ShadowLight> choose(Vector3fc sunDirection, Vector3fc moonDirection) {
        if (sunDirection.y() > FADE_START_ELEVATION) {
            return Optional.of(new ShadowLight(sunDirection, false, fade(sunDirection.y())));
        }
        if (moonDirection.y() > FADE_START_ELEVATION) {
            return Optional.of(new ShadowLight(moonDirection, true, fade(moonDirection.y())));
        }
        return Optional.empty();
    }

    public float sunStrength() {
        return moon ? 0.0F : strength;
    }

    public float moonStrength() {
        return moon ? strength : 0.0F;
    }

    private static float fade(float elevation) {
        float amount = Math.clamp((elevation - FADE_START_ELEVATION) / (FADE_END_ELEVATION - FADE_START_ELEVATION), 0.0F, 1.0F);
        return amount * amount * (3.0F - 2.0F * amount);
    }
}
