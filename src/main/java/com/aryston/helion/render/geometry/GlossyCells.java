package com.aryston.helion.render.geometry;

import com.mojang.renderpearl.api.textures.GpuTextureView;

public record GlossyCells(GpuTextureView atlas, int width, int height, byte[] cells) {
    public static final int CELL_SIZE = 16;
    public static final byte GLOSSY = (byte) 0xFF;
}