package com.aryston.helion.render.water;

enum ReflectedClouds {
    NONE(0.0F),
    SCREEN(1.0F),
    SCREEN_AND_SKY_MAP(2.0F);

    private final float level;

    ReflectedClouds(float level) {
        this.level = level;
    }

    float level() {
        return level;
    }
}
