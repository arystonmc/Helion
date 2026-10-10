package com.aryston.helion.render.shadow;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record ShadowCascade(Matrix4fc lightView, Matrix4fc projection, Vector3fc lightSpaceCenter, float radius, float casterReach, float texelSize) {
    private static final int BOX_CORNERS = 8;
    private static final int CORNER_X = 1;
    private static final int CORNER_Y = 2;
    private static final int CORNER_Z = 4;

    public ShadowCascade {
        lightView = new Matrix4f(lightView);
        projection = new Matrix4f(projection);
        lightSpaceCenter = new Vector3f(lightSpaceCenter);
    }

    public Matrix4f lightViewProjection() {
        return new Matrix4f(projection).mul(lightView);
    }

    public boolean containsLightSpace(Vector3fc lightSpacePosition, float boundingRadius) {
        float reach = radius + boundingRadius;
        return Math.abs(lightSpacePosition.x() - lightSpaceCenter.x()) <= reach
            && Math.abs(lightSpacePosition.y() - lightSpaceCenter.y()) <= reach
            && lightSpacePosition.z() <= lightSpaceCenter.z() + reach + casterReach
            && lightSpacePosition.z() >= lightSpaceCenter.z() - reach;
    }

    public void expandCameraRelativeBounds(float boundingRadius, Vector3f min, Vector3f max) {
        float reach = radius + boundingRadius;
        Matrix4f lightToWorld = new Matrix4f(lightView).invertAffine();
        Vector3f corner = new Vector3f();
        for (int index = 0; index < BOX_CORNERS; index++) {
            corner.set(
                lightSpaceCenter.x() + ((index & CORNER_X) == 0 ? -reach : reach),
                lightSpaceCenter.y() + ((index & CORNER_Y) == 0 ? -reach : reach),
                lightSpaceCenter.z() + ((index & CORNER_Z) == 0 ? -reach : reach + casterReach)
            );
            lightToWorld.transformPosition(corner);
            min.min(corner);
            max.max(corner);
        }
    }
}
