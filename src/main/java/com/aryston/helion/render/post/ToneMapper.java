package com.aryston.helion.render.post;

public enum ToneMapper {
    NEUTRAL(0),
    FILMIC(1),
    NONE(2);

    private final int shaderId;

    ToneMapper(int shaderId) {
        this.shaderId = shaderId;
    }

    public int shaderId() {
        return shaderId;
    }
}
