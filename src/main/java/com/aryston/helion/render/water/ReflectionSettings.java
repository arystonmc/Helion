package com.aryston.helion.render.water;

public record ReflectionSettings(boolean screenSpace) {
    public static final ReflectionSettings DISABLED = new ReflectionSettings(false);
}
