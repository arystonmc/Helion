package com.aryston.helion.render.temporal;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

record PreviousView(Matrix4fc projection, Matrix4fc viewRotation, Vector3dc position) {
    PreviousView {
        projection = new Matrix4f(projection);
        viewRotation = new Matrix4f(viewRotation);
        position = new Vector3d(position);
    }

    Matrix4f reprojection(Vector3dc currentPosition, Vector2fc jitter, Matrix4fc jitteredViewProjection) {
        Vector3d moved = new Vector3d(currentPosition).sub(position);
        return new Matrix4f()
            .translation(jitter.x(), jitter.y(), 0.0F)
            .mul(projection)
            .mul(viewRotation)
            .translate((float) moved.x, (float) moved.y, (float) moved.z)
            .mul(new Matrix4f(jitteredViewProjection).invert());
    }
}
