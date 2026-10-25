package com.aryston.helion.render.water;

public record UnderwaterView(boolean submerged, float skyLight, float surfaceAbove, float floorBelow, int waterColor) {
    public static final int DEFAULT_WATER_COLOR = 0x3F76E4;
    public static final UnderwaterView ABOVE_WATER = new UnderwaterView(false, 1.0F, 0.0F, 0.0F, DEFAULT_WATER_COLOR);
}
