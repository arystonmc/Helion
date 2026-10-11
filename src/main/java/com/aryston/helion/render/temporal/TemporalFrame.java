package com.aryston.helion.render.temporal;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2fc;

public record TemporalFrame(
    boolean active,
    Matrix4fc jitteredProjection,
    Matrix4fc reprojection,
    Vector2fc jitterPixels,
    boolean historyValid,
    boolean cameraMotionValid
) {
    public static final TemporalFrame INACTIVE = new TemporalFrame(false, new Matrix4f(), new Matrix4f(), new Vector2f(), false, false);

    public TemporalFrame {
        jitteredProjection = new Matrix4f(jitteredProjection);
        reprojection = new Matrix4f(reprojection);
        jitterPixels = new Vector2f(jitterPixels);
    }
}
