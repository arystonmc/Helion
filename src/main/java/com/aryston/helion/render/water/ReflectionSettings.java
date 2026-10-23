package com.aryston.helion.render.water;

public record ReflectionSettings(boolean screenSpace, boolean rainWetness, boolean realisticWater) {
    public static final ReflectionSettings DISABLED = new ReflectionSettings(false, false, false);
}
