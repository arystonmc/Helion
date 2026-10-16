package com.aryston.helion.render.temporal;

public enum RenderScale {
    NATIVE(1.0F),
    ULTRA_QUALITY(1.0F / 1.3F),
    QUALITY(1.0F / 1.5F),
    BALANCED(1.0F / 1.7F),
    PERFORMANCE(1.0F / 2.0F);

    private static final int MIN_SIZE = 1;

    private final float factor;

    RenderScale(float factor) {
        this.factor = factor;
    }

    public float factor() {
        return factor;
    }

    public int internalSize(int outputSize) {
        return Math.max(MIN_SIZE, Math.round(outputSize * factor));
    }
}
