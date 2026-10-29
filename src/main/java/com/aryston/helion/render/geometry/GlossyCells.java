package com.aryston.helion.render.geometry;

import com.mojang.renderpearl.api.textures.GpuTextureView;

public record GlossyCells(GpuTextureView atlas, int width, int height, byte[] cells) {
    public static final int CELL_SIZE = 16;
    public static final byte EMISSIVE = (byte) 0x20;
    public static final byte MUDDY = (byte) 0x40;
    public static final byte METAL = (byte) 0x60;
    public static final byte POROUS = (byte) 0x80;
    public static final byte LAVA_FLOW = (byte) 0x9F;
    public static final byte DRY = (byte) 0xBF;
    public static final byte LAVA = (byte) 0xDF;
    public static final byte PLAIN_GLASS = (byte) 0xF0;
    public static final byte GLOSSY = (byte) 0xFF;
    public static final byte ORDINARY = 0;
}