package com.aryston.helion.render.camera;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class HelionFrustumTest {
    private static final float FIELD_OF_VIEW = (float) Math.toRadians(70.0);
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float NEAR = 0.05F;
    private static final float FAR = 100.0F;
    private static final Vector3d ORIGIN = new Vector3d(1000.0, 64.0, -500.0);
    private static final double HALF_BOX = 1.0;
    private static final double IN_FRONT = -10.0;
    private static final double BEHIND = 10.0;
    private static final double FAR_TO_THE_SIDE = 100.0;
    private static final double BEYOND_FAR = -150.0;

    @Test
    void openGlDepthRange() {
        assertCulling(frustum(new Matrix4f().perspective(FIELD_OF_VIEW, ASPECT, NEAR, FAR, false), false));
    }

    @Test
    void zeroToOneDepthRange() {
        assertCulling(frustum(new Matrix4f().perspective(FIELD_OF_VIEW, ASPECT, NEAR, FAR, true), true));
    }

    @Test
    void reversedZeroToOneDepthRange() {
        assertCulling(frustum(new Matrix4f().perspective(FIELD_OF_VIEW, ASPECT, FAR, NEAR, true), true));
    }

    private static void assertCulling(HelionFrustum frustum) {
        assertTrue(intersectsBoxAt(frustum, 0.0, IN_FRONT), "box in front of the camera");
        assertFalse(intersectsBoxAt(frustum, 0.0, BEHIND), "box behind the camera");
        assertFalse(intersectsBoxAt(frustum, FAR_TO_THE_SIDE, IN_FRONT), "box outside the field of view");
        assertFalse(intersectsBoxAt(frustum, 0.0, BEYOND_FAR), "box beyond the far plane");
    }

    private static HelionFrustum frustum(Matrix4f projection, boolean zeroToOneDepth) {
        return new HelionCamera(ORIGIN, new Matrix4f(), projection, zeroToOneDepth).frustum();
    }

    private static boolean intersectsBoxAt(HelionFrustum frustum, double x, double z) {
        return frustum.intersects(
            ORIGIN.x + x - HALF_BOX, ORIGIN.y - HALF_BOX, ORIGIN.z + z - HALF_BOX,
            ORIGIN.x + x + HALF_BOX, ORIGIN.y + HALF_BOX, ORIGIN.z + z + HALF_BOX
        );
    }
}
