package com.aryston.helion.render.water;

public record ReflectionSettings(boolean screenSpace, boolean rainWetness) {
    public static final ReflectionSettings DISABLED = new ReflectionSettings(false, false);
}
