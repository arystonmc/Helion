package com.aryston.helion.render.voxel;

import java.nio.ByteBuffer;

public final class VoxelCoarseLevel {
    public static final int CELL_SIZE = 4;
    public static final int CELLS_PER_SECTION = VoxelVolumeLayout.SECTION_SIZE / CELL_SIZE;
    public static final int SLICES_PER_ROW = 2;
    public static final int BRICK_SIZE = CELLS_PER_SECTION * SLICES_PER_ROW;
    public static final int BRICK_BYTES = BRICK_SIZE * BRICK_SIZE;
    public static final int ATLAS_WIDTH = BRICK_SIZE * VoxelVolumeLayout.BRICKS_PER_ROW;
    public static final int ATLAS_HEIGHT = BRICK_SIZE * (VoxelVolumeLayout.SECTION_COUNT / VoxelVolumeLayout.BRICKS_PER_ROW);
    public static final byte BLOCKED = (byte) 0xFF;
    public static final byte OPEN = 0;

    private static final int OCCUPANCY_MASK = 3;

    private VoxelCoarseLevel() {
    }

    public static int brickX(int slot) {
        return slot % VoxelVolumeLayout.BRICKS_PER_ROW * BRICK_SIZE;
    }

    public static int brickY(int slot) {
        return slot / VoxelVolumeLayout.BRICKS_PER_ROW * BRICK_SIZE;
    }

    public static int cellIndex(int cellX, int cellY, int cellZ) {
        int texelX = cellY % SLICES_PER_ROW * CELLS_PER_SECTION + cellX;
        int texelY = cellY / SLICES_PER_ROW * CELLS_PER_SECTION + cellZ;
        return texelY * BRICK_SIZE + texelX;
    }

    public static void build(ByteBuffer brick, ByteBuffer coarse) {
        for (int index = 0; index < BRICK_BYTES; index++) {
            coarse.put(index, OPEN);
        }
        for (int y = 0; y < VoxelVolumeLayout.SECTION_SIZE; y++) {
            for (int z = 0; z < VoxelVolumeLayout.SECTION_SIZE; z++) {
                for (int x = 0; x < VoxelVolumeLayout.SECTION_SIZE; x++) {
                    byte voxel = brick.get(VoxelVolumeLayout.brickIndex(x, y, z));
                    if ((voxel & OCCUPANCY_MASK) == VoxelClass.OPAQUE || VoxelClass.tints(voxel)) {
                        coarse.put(cellIndex(x / CELL_SIZE, y / CELL_SIZE, z / CELL_SIZE), BLOCKED);
                    }
                }
            }
        }
    }
}
