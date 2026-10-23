package com.aryston.helion.render.water;

public record Wetness(float surfaces, float puddles, float rain, float seconds) {
    public static final Wetness DRY = new Wetness(0.0F, 0.0F, 0.0F, 0.0F);
    private static final float VISIBLE = 0.002F;

    public boolean visible() {
        return surfaces > VISIBLE || puddles > VISIBLE;
    }
}
