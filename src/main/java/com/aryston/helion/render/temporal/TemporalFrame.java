package com.aryston.helion.render.temporal;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public record TemporalFrame(boolean active, Matrix4fc jitteredProjection, Matrix4fc reprojection, boolean historyValid) {
    public static final TemporalFrame INACTIVE = new TemporalFrame(false, new Matrix4f(), new Matrix4f(), false);

    public TemporalFrame {
        jitteredProjection = new Matrix4f(jitteredProjection);
        reprojection = new Matrix4f(reprojection);
    }
}
