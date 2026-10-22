package com.aryston.helion.render.lightshaft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class LightShaftViewTest {
    private static final float FIELD_OF_VIEW = (float) Math.toRadians(70.0);
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float NEAR = 0.05F;
    private static final float FAR = 100.0F;
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f().perspective(FIELD_OF_VIEW, ASPECT, FAR, NEAR, true);
    private static final Vector3f AHEAD_AND_UP = new Vector3f(0.0F, 0.4F, -1.0F).normalize();
    private static final Vector3f BEHIND_AND_UP = new Vector3f(0.0F, 0.4F, 1.0F).normalize();
    private static final Vector3f BELOW_HORIZON = new Vector3f(0.0F, -0.5F, 1.0F).normalize();
    private static final float CLEAR = 1.0F;
    private static final float HEAVY_RAIN = 0.1F;
    private static final float CENTER = 0.5F;
    private static final float TOLERANCE = 1.0e-4F;

    @Test
    void sunInFrontIsCenteredAndFullyVisible() {
        LightShaftView view = LightShaftView.of(VIEW_PROJECTION, AHEAD_AND_UP, BELOW_HORIZON, CLEAR).orElseThrow();
        assertTrue(view.sunLit());
        assertEquals(CENTER, view.screenX(), TOLERANCE);
        assertTrue(view.screenY() > CENTER, "the sun above the view axis lies in the upper half");
        assertEquals(1.0F, view.visibility(), TOLERANCE);
    }

    @Test
    void sunBehindTheCameraHasNoShafts() {
        assertFalse(LightShaftView.of(VIEW_PROJECTION, BEHIND_AND_UP, BELOW_HORIZON, CLEAR).isPresent());
    }

    @Test
    void moonTakesOverBelowTheHorizon() {
        Optional<LightShaftView> view = LightShaftView.of(VIEW_PROJECTION, BELOW_HORIZON, AHEAD_AND_UP, CLEAR);
        assertTrue(view.isPresent());
        assertFalse(view.get().sunLit());
    }

    @Test
    void heavyRainHidesTheShafts() {
        assertFalse(LightShaftView.of(VIEW_PROJECTION, AHEAD_AND_UP, BELOW_HORIZON, HEAVY_RAIN).isPresent());
    }
}
