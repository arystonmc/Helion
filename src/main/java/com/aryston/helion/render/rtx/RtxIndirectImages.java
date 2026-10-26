package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.mojang.renderpearl.api.GpuFormat;

final class RtxIndirectImages {
    private static final String RAW_LABEL = "Helion Path Traced Light";
    private static final String HISTORY_LABEL = "Helion Path Traced History #";
    private static final String GUIDE_LABEL = "Helion Path Traced Guide #";
    private static final String FILTER_LABEL = "Helion Path Traced Filter #";
    private static final GpuFormat FORMAT = GpuFormat.RGBA16_FLOAT;
    private static final int PAIR = 2;

    private final int width;
    private final int height;
    private final RtxImage raw;
    private final RtxImage[] history = new RtxImage[PAIR];
    private final RtxImage[] guide = new RtxImage[PAIR];
    private final RtxImage[] filter = new RtxImage[PAIR];
    private int current;
    private boolean warm;

    private RtxIndirectImages(GpuResources resources, int width, int height) {
        this.width = width;
        this.height = height;
        raw = RtxImage.create(resources, RAW_LABEL, width, height, FORMAT);
        for (int slot = 0; slot < PAIR; slot++) {
            history[slot] = RtxImage.create(resources, HISTORY_LABEL + slot, width, height, FORMAT);
            guide[slot] = RtxImage.create(resources, GUIDE_LABEL + slot, width, height, FORMAT);
            filter[slot] = RtxImage.create(resources, FILTER_LABEL + slot, width, height, FORMAT);
        }
    }

    static RtxIndirectImages create(GpuResources resources, int width, int height) {
        return new RtxIndirectImages(resources, width, height);
    }

    boolean fits(int requestedWidth, int requestedHeight) {
        return width == requestedWidth && height == requestedHeight;
    }

    void swap() {
        current = 1 - current;
    }

    boolean warm() {
        return warm;
    }

    void markWarm() {
        warm = true;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    RtxImage raw() {
        return raw;
    }

    RtxImage history() {
        return history[current];
    }

    RtxImage previousHistory() {
        return history[1 - current];
    }

    RtxImage guide() {
        return guide[current];
    }

    RtxImage previousGuide() {
        return guide[1 - current];
    }

    RtxImage filter(int slot) {
        return filter[slot];
    }

    void close(GpuResources resources) {
        raw.close(resources);
        for (int slot = 0; slot < PAIR; slot++) {
            history[slot].close(resources);
            guide[slot].close(resources);
            filter[slot].close(resources);
        }
    }
}
