package com.aryston.helion.render.atmosphere;

import org.joml.Vector4f;
import org.joml.Vector4fc;

public record AtmosphereFog(
    Vector4fc color,
    float environmentalStart,
    float environmentalEnd,
    float renderDistanceStart,
    float renderDistanceEnd,
    float skyEnd,
    float cloudEnd
) {
    public AtmosphereFog {
        color = new Vector4f(color);
    }
}
