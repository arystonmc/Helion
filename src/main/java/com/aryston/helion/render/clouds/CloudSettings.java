package com.aryston.helion.render.clouds;

public record CloudSettings(boolean enabled, CloudQuality quality) {
    public static final CloudSettings DISABLED = new CloudSettings(false, CloudQuality.MEDIUM);
}
