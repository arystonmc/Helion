package com.aryston.helion.render.voxel;

public final class VoxelVolumeLayout {
    public static final int SECTION_SIZE = 16;
    public static final int SECTIONS_PER_AXIS = 8;
    public static final int VOLUME_SIZE = SECTION_SIZE * SECTIONS_PER_AXIS;
    public static final int SECTION_COUNT = SECTIONS_PER_AXIS * SECTIONS_PER_AXIS * SECTIONS_PER_AXIS;
    public static final int SECTIONS_BEFORE_CENTER = SECTIONS_PER_AXIS / 2;
    public static final int SLICES_PER_ROW = 4;
    public static final int BRICK_SIZE = SECTION_SIZE * SLICES_PER_ROW;
    public static final int BRICK_BYTES = BRICK_SIZE * BRICK_SIZE;
    public static final int BRICKS_PER_ROW = 32;
    public static final int ATLAS_WIDTH = BRICK_SIZE * BRICKS_PER_ROW;
    public static final int ATLAS_HEIGHT = BRICK_SIZE * (SECTION_COUNT / BRICKS_PER_ROW);
    public static final int ATLAS_BYTES = ATLAS_WIDTH * ATLAS_HEIGHT;
    public static final int COLOR_BYTES_PER_BLOCK = 4;
    public static final int BRICK_COLOR_BYTES = BRICK_BYTES * COLOR_BYTES_PER_BLOCK;

    private static final int SECTION_MASK = SECTION_SIZE - 1;
    private static final int SECTION_SHIFT = 4;
    private static final int AXIS_MASK = SECTIONS_PER_AXIS - 1;
    private static final int HORIZONTAL_BITS = 22;
    private static final int VERTICAL_BITS = 20;
    private static final int X_SHIFT = 64 - HORIZONTAL_BITS;
    private static final int Z_SHIFT = VERTICAL_BITS;
    private static final long HORIZONTAL_MASK = (1L << HORIZONTAL_BITS) - 1L;
    private static final long VERTICAL_MASK = (1L << VERTICAL_BITS) - 1L;

    private VoxelVolumeLayout() {
    }

    public static int sectionOf(int block) {
        return block >> SECTION_SHIFT;
    }

    public static int localOf(int block) {
        return block & SECTION_MASK;
    }

    public static int windowMin(int centerSection) {
        return centerSection - SECTIONS_BEFORE_CENTER;
    }

    public static boolean inWindow(int section, int windowMin) {
        return section >= windowMin && section < windowMin + SECTIONS_PER_AXIS;
    }

    public static int slotAxis(int section) {
        return section & AXIS_MASK;
    }

    public static int slot(int sectionX, int sectionY, int sectionZ) {
        return slotAxis(sectionX) + SECTIONS_PER_AXIS * (slotAxis(sectionY) + SECTIONS_PER_AXIS * slotAxis(sectionZ));
    }

    public static int sectionForSlotAxis(int slotAxis, int windowMin) {
        return windowMin + ((slotAxis - windowMin) & AXIS_MASK);
    }

    public static int slotX(int slot) {
        return slot % SECTIONS_PER_AXIS;
    }

    public static int slotY(int slot) {
        return slot / SECTIONS_PER_AXIS % SECTIONS_PER_AXIS;
    }

    public static int slotZ(int slot) {
        return slot / (SECTIONS_PER_AXIS * SECTIONS_PER_AXIS);
    }

    public static int brickX(int slot) {
        return slot % BRICKS_PER_ROW * BRICK_SIZE;
    }

    public static int brickY(int slot) {
        return slot / BRICKS_PER_ROW * BRICK_SIZE;
    }

    public static int brickTexelX(int localX, int localY) {
        return localY % SLICES_PER_ROW * SECTION_SIZE + localX;
    }

    public static int brickTexelY(int localY, int localZ) {
        return localY / SLICES_PER_ROW * SECTION_SIZE + localZ;
    }

    public static int brickIndex(int localX, int localY, int localZ) {
        return brickTexelY(localY, localZ) * BRICK_SIZE + brickTexelX(localX, localY);
    }

    public static long sectionKey(int sectionX, int sectionY, int sectionZ) {
        return (sectionX & HORIZONTAL_MASK) << X_SHIFT | (sectionY & VERTICAL_MASK) | (sectionZ & HORIZONTAL_MASK) << Z_SHIFT;
    }

    public static int keyX(long key) {
        return (int) (key >> X_SHIFT);
    }

    public static int keyY(long key) {
        return (int) (key << (64 - VERTICAL_BITS) >> (64 - VERTICAL_BITS));
    }

    public static int keyZ(long key) {
        return (int) (key << (64 - X_SHIFT) >> (64 - HORIZONTAL_BITS));
    }
}
