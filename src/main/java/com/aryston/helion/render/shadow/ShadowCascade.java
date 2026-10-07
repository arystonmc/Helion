package com.aryston.helion.render.shadow;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record ShadowCascade(Matrix4fc lightView, Matrix4fc projection, Vector3fc lightSpaceCenter, float radius, float casterReach, float texelSize) {
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
}
