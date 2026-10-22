package com.aryston.helion.render.atmosphere;

public enum StarDensity {
    LOW(0.35F),
    MEDIUM(0.65F),
    HIGH(1.0F);

    private final float fraction;

    StarDensity(float fraction) {
        this.fraction = fraction;
    }

    public float fraction() {
        return fraction;
    }
}
