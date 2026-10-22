package com.aryston.helion.render.lightshaft;

import java.util.Optional;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

record LightShaftView(float screenX, float screenY, Vector3fc direction, boolean sunLit, float visibility) {
    static final float SUN_BELOW_HORIZON = -0.05F;
    private static final float HORIZON_FADE_END = 0.03F;
    private static final float SCREEN_FADE_DISTANCE = 0.6F;
    private static final float RAIN_FADE_POWER = 4.0F;
    private static final float MIN_VISIBILITY = 1.0e-3F;
    private static final float HALF = 0.5F;

    LightShaftView {
        direction = new Vector3f(direction);
    }

    static Optional<LightShaftView> of(Matrix4fc viewProjection, Vector3fc sunDirection, Vector3fc moonDirection, float rainBrightness) {
        boolean sunLit = sunDirection.y() > SUN_BELOW_HORIZON;
        Vector3fc light = sunLit ? sunDirection : moonDirection;
        Vector4f clip = viewProjection.transform(new Vector4f(light.x(), light.y(), light.z(), 0.0F));
        if (clip.w <= 0.0F) {
            return Optional.empty();
        }
        float screenX = clip.x / clip.w * HALF + HALF;
        float screenY = clip.y / clip.w * HALF + HALF;
        float outside = Math.max(Math.max(-screenX, screenX - 1.0F), Math.max(-screenY, screenY - 1.0F));
        float onScreen = 1.0F - smoothstep(0.0F, SCREEN_FADE_DISTANCE, outside);
        float aboveHorizon = smoothstep(SUN_BELOW_HORIZON, HORIZON_FADE_END, light.y());
        float clearSky = (float) Math.pow(Math.clamp(rainBrightness, 0.0F, 1.0F), RAIN_FADE_POWER);
        float visibility = onScreen * aboveHorizon * clearSky;
        if (visibility < MIN_VISIBILITY) {
            return Optional.empty();
        }
        return Optional.of(new LightShaftView(screenX, screenY, light, sunLit, visibility));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
