package com.aryston.helion.render.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class PreviousViewTest {
    private static final float FIELD_OF_VIEW = (float) Math.toRadians(70.0);
    private static final float ASPECT = 16.0F / 9.0F;
    private static final float NEAR = 0.05F;
    private static final float FAR = 512.0F;
    private static final float TOLERANCE = 1.0e-4F;
    private static final Vector2f JITTER = new Vector2f(0.0007F, -0.0004F);
    private static final Vector3d PREVIOUS_POSITION = new Vector3d(100.25, 64.5, -30.75);
    private static final Vector3d CURRENT_POSITION = new Vector3d(100.55, 64.6, -30.95);
    private static final Vector3d WORLD_POINT = new Vector3d(110.0, 66.0, -25.0);
    private static final float PREVIOUS_YAW = 0.3F;
    private static final float CURRENT_YAW = 0.32F;
    private static final float PITCH = 0.1F;

    @Test
    void mapsAPointToWhereThePreviousCameraSawIt() {
        Matrix4f projection = projection();
        Matrix4f previousRotation = rotation(PREVIOUS_YAW);
        Matrix4f currentRotation = rotation(CURRENT_YAW);
        Matrix4f jitteredViewProjection = new Matrix4f(projection).translateLocal(JITTER.x, JITTER.y, 0.0F).mul(currentRotation);
        PreviousView previous = new PreviousView(projection, previousRotation, PREVIOUS_POSITION);

        Vector3f current = ndc(jitteredViewProjection, relative(WORLD_POINT, CURRENT_POSITION));
        Vector3f reprojected = ndc(previous.reprojection(CURRENT_POSITION, JITTER, jitteredViewProjection), current);
        Vector3f expected = ndc(new Matrix4f(projection).mul(previousRotation), relative(WORLD_POINT, PREVIOUS_POSITION));

        assertEquals(expected.x + JITTER.x, reprojected.x, TOLERANCE);
        assertEquals(expected.y + JITTER.y, reprojected.y, TOLERANCE);
    }

    @Test
    void keepsEveryPixelInPlaceForAStillCamera() {
        Matrix4f projection = projection();
        Matrix4f rotation = rotation(PREVIOUS_YAW);
        Matrix4f jitteredViewProjection = new Matrix4f(projection).translateLocal(JITTER.x, JITTER.y, 0.0F).mul(rotation);
        PreviousView previous = new PreviousView(projection, rotation, PREVIOUS_POSITION);

        Vector3f current = ndc(jitteredViewProjection, relative(WORLD_POINT, PREVIOUS_POSITION));
        Vector3f reprojected = ndc(previous.reprojection(PREVIOUS_POSITION, JITTER, jitteredViewProjection), current);

        assertEquals(current.x, reprojected.x, TOLERANCE);
        assertEquals(current.y, reprojected.y, TOLERANCE);
    }

    private static Matrix4f projection() {
        return new Matrix4f().setPerspective(FIELD_OF_VIEW, ASPECT, NEAR, FAR, true);
    }

    private static Matrix4f rotation(float yaw) {
        return new Matrix4f().rotationXYZ(PITCH, yaw, 0.0F);
    }

    private static Vector3f relative(Vector3d point, Vector3d camera) {
        return new Vector3f((float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z));
    }

    private static Vector3f ndc(Matrix4f matrix, Vector3f position) {
        Vector4f clip = matrix.transform(new Vector4f(position, 1.0F));
        return new Vector3f(clip.x / clip.w, clip.y / clip.w, clip.z / clip.w);
    }
}
