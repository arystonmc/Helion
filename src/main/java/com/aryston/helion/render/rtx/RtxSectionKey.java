package com.aryston.helion.render.rtx;

final class RtxSectionKey {
    static final int SECTION_SIZE = 16;

    private static final int SECTION_SHIFT = 4;
    private static final int HORIZONTAL_BITS = 22;
    private static final int VERTICAL_BITS = 20;
    private static final int LONG_BITS = 64;
    private static final int X_SHIFT = LONG_BITS - HORIZONTAL_BITS;
    private static final int Z_SHIFT = VERTICAL_BITS;
    private static final long HORIZONTAL_MASK = (1L << HORIZONTAL_BITS) - 1L;
    private static final long VERTICAL_MASK = (1L << VERTICAL_BITS) - 1L;

    private RtxSectionKey() {
    }

    static int sectionOf(int block) {
        return block >> SECTION_SHIFT;
    }

    static long of(int sectionX, int sectionY, int sectionZ) {
        return (sectionX & HORIZONTAL_MASK) << X_SHIFT | (sectionY & VERTICAL_MASK) | (sectionZ & HORIZONTAL_MASK) << Z_SHIFT;
    }

    static int x(long key) {
        return (int) (key >> X_SHIFT);
    }

    static int y(long key) {
        return (int) (key << (LONG_BITS - VERTICAL_BITS) >> (LONG_BITS - VERTICAL_BITS));
    }

    static int z(long key) {
        return (int) (key << (LONG_BITS - X_SHIFT) >> (LONG_BITS - HORIZONTAL_BITS));
    }
}
