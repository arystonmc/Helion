package com.aryston.helion.render.shadow;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class ShadowCascades {
    public static final int COUNT = 4;
    public static final float MAX_DISTANCE = 160.0F;
    public static final float CASTER_REACH = 256.0F;
    private static final float[] SPLITS = {0.08F, 0.22F, 0.5F, 1.0F};
    private static final float RADIUS_STEP = 1.0F / 16.0F;
    private static final float PARALLEL_LIMIT = 0.99F;
    private static final Vector3fc PRIMARY_UP = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final Vector3fc SECONDARY_UP = new Vector3f(1.0F, 0.0F, 0.0F);

    private ShadowCascades() {
    }

    public static List<ShadowCascade> compute(
        Vector3dc cameraPosition,
        Matrix4fc viewRotation,
        Matrix4fc projection,
        Vector3fc lightDirection,
        float maxDistance,
        int resolution,
        boolean zeroToOneDepth
    ) {
        Matrix4f lightView = lightView(lightDirection);
        Matrix4f viewToWorld = new Matrix4f(viewRotation).invert();
        float tanX = 1.0F / projection.m00();
        float tanY = 1.0F / projection.m11();
        float diagonal = tanX * tanX + tanY * tanY;
        List<ShadowCascade> cascades = new ArrayList<>(COUNT);
        float near = 0.0F;
        for (int index = 0; index < COUNT; index++) {
            float far = splitDistance(index, maxDistance);
            cascades.add(cascade(cameraPosition, viewToWorld, lightView, near, far, diagonal, resolution, zeroToOneDepth));
            near = far;
        }
        return List.copyOf(cascades);
    }

    static float splitDistance(int index, float maxDistance) {
        return SPLITS[index] * maxDistance;
    }

    public static float distance(int renderDistanceBlocks) {
        return Math.min(MAX_DISTANCE, renderDistanceBlocks);
    }

    static float boundingRadius(float near, float far, float diagonal) {
        float centerDepth = centerDepth(near, far, diagonal);
        float radius = (float) Math.sqrt((far - centerDepth) * (far - centerDepth) + far * far * diagonal);
        return (float) Math.ceil(radius / RADIUS_STEP) * RADIUS_STEP;
    }

    static float centerDepth(float near, float far, float diagonal) {
        return Math.min((far + near) * (1.0F + diagonal) * 0.5F, far);
    }

    static Matrix4f lightView(Vector3fc lightDirection) {
        Vector3f forward = new Vector3f(lightDirection).negate().normalize();
        Vector3fc up = Math.abs(forward.dot(PRIMARY_UP)) < PARALLEL_LIMIT ? PRIMARY_UP : SECONDARY_UP;
        return new Matrix4f().setLookAt(0.0F, 0.0F, 0.0F, forward.x, forward.y, forward.z, up.x(), up.y(), up.z());
    }

    private static ShadowCascade cascade(
        Vector3dc cameraPosition,
        Matrix4fc viewToWorld,
        Matrix4f lightView,
        float near,
        float far,
        float diagonal,
        int resolution,
        boolean zeroToOneDepth
    ) {
        float radius = boundingRadius(near, far, diagonal);
        float texelSize = 2.0F * radius / resolution;
        Vector3f center = viewToWorld.transformPosition(new Vector3f(0.0F, 0.0F, -centerDepth(near, far, diagonal)));
        Vector3f lightSpaceCenter = lightView.transformPosition(new Vector3f(center));
        Vector3d absoluteCenter = new Vector3d(cameraPosition).add(center.x, center.y, center.z);
        double lightSpaceX = lightView.m00() * absoluteCenter.x + lightView.m10() * absoluteCenter.y + lightView.m20() * absoluteCenter.z;
        double lightSpaceY = lightView.m01() * absoluteCenter.x + lightView.m11() * absoluteCenter.y + lightView.m21() * absoluteCenter.z;
        lightSpaceCenter.x += (float) (snap(lightSpaceX, texelSize) - lightSpaceX);
        lightSpaceCenter.y += (float) (snap(lightSpaceY, texelSize) - lightSpaceY);
        Matrix4f projection = new Matrix4f().setOrtho(
            lightSpaceCenter.x - radius,
            lightSpaceCenter.x + radius,
            lightSpaceCenter.y - radius,
            lightSpaceCenter.y + radius,
            -(lightSpaceCenter.z + radius + CASTER_REACH),
            -(lightSpaceCenter.z - radius),
            zeroToOneDepth
        );
        return new ShadowCascade(lightView, projection, lightSpaceCenter, radius, CASTER_REACH, texelSize);
    }

    private static double snap(double value, float step) {
        return Math.floor(value / step) * step;
    }
}
