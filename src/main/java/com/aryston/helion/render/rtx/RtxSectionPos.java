package com.aryston.helion.render.rtx;

public record RtxSectionPos(int sectionX, int sectionY, int sectionZ) {
    boolean within(int x, int y, int z, int radius) {
        return Math.abs(x - sectionX) <= radius && Math.abs(y - sectionY) <= radius && Math.abs(z - sectionZ) <= radius;
    }

    int distance(int x, int y, int z) {
        return Math.max(Math.abs(x - sectionX), Math.max(Math.abs(y - sectionY), Math.abs(z - sectionZ)));
    }

    int originX() {
        return sectionX * RtxSectionKey.SECTION_SIZE;
    }

    int originY() {
        return sectionY * RtxSectionKey.SECTION_SIZE;
    }

    int originZ() {
        return sectionZ * RtxSectionKey.SECTION_SIZE;
    }
}
