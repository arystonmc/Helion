package com.aryston.helion.render.camera;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public record HelionCamera(
    Vector3dc position,
    Matrix4fc viewRotation,
    Matrix4fc projection,
    Matrix4fc levelProjection,
    boolean zeroToOneDepth
) {
    public HelionCamera {
        position = new Vector3d(position);
        viewRotation = new Matrix4f(viewRotation);
        projection = new Matrix4f(projection);
        levelProjection = new Matrix4f(levelProjection);
    }

    public Matrix4f viewProjection() {
        return new Matrix4f(projection).mul(viewRotation);
    }

    public Matrix4f levelViewProjection() {
        return new Matrix4f(levelProjection).mul(viewRotation);
    }

    public HelionCamera withLevelProjection(Matrix4fc drawnWith) {
        return new HelionCamera(position, viewRotation, projection, drawnWith, zeroToOneDepth);
    }

    public HelionFrustum frustum() {
        return HelionFrustum.of(this);
    }
}
