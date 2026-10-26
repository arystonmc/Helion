package com.aryston.helion.render.geometry;

public enum GeometryBufferView {
    NONE(0),
    NORMALS(1),
    BLOCK_LIGHT(2),
    SKY_LIGHT(3),
    ALBEDO(4);

    private final int shaderId;

    GeometryBufferView(int shaderId) {
        this.shaderId = shaderId;
    }

    public int shaderId() {
        return shaderId;
    }
}
