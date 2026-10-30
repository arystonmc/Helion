package com.aryston.helion.render.water;

import java.util.List;

record GlassRainImage(int width, int height, List<byte[]> levels) {
    static final int CHANNELS = 4;

    GlassRainImage {
        levels = List.copyOf(levels);
    }

    int levelWidth(int level) {
        return Math.max(1, width >> level);
    }

    int levelHeight(int level) {
        return Math.max(1, height >> level);
    }
}
