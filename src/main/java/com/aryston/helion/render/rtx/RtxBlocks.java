package com.aryston.helion.render.rtx;

import com.aryston.helion.render.voxel.VoxelBrickSink;
import com.aryston.helion.render.voxel.VoxelClass;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class RtxBlocks implements VoxelBrickSink {
    static final int AREA_CHUNKS = 3;
    static final int AREA_WIDTH = AREA_CHUNKS * VoxelVolumeLayout.SECTION_SIZE;
    static final int AREA_HEIGHT = VoxelVolumeLayout.VOLUME_SIZE;

    private static final int SIZE = VoxelVolumeLayout.SECTION_SIZE;
    private static final int SECTION_BLOCKS = SIZE * SIZE * SIZE;
    private static final int OCCUPANCY_MASK = 3;
    private static final long NO_SECTION = Long.MIN_VALUE;
    private static final int CHUNKS_BEFORE_CENTER = AREA_CHUNKS / 2;
    private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final long[] keys = new long[VoxelVolumeLayout.SECTION_COUNT];
    private final long[] revisions = new long[VoxelVolumeLayout.SECTION_COUNT];
    private final byte[][] blocks = new byte[VoxelVolumeLayout.SECTION_COUNT][];
    private final boolean[] opaque = new boolean[AREA_WIDTH * AREA_HEIGHT * AREA_WIDTH];
    private long revision;

    RtxBlocks() {
        Arrays.fill(keys, NO_SECTION);
    }

    @Override
    public void brickBuilt(int sectionX, int sectionY, int sectionZ, ByteBuffer brick) {
        int slot = VoxelVolumeLayout.slot(sectionX, sectionY, sectionZ);
        byte[] section = blocks[slot];
        if (section == null) {
            section = new byte[SECTION_BLOCKS];
            blocks[slot] = section;
        }
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    section[index(x, y, z)] = brick.get(VoxelVolumeLayout.brickIndex(x, y, z));
                }
            }
        }
        keys[slot] = VoxelVolumeLayout.sectionKey(sectionX, sectionY, sectionZ);
        revisions[slot] = ++revision;
    }

    static RtxArea area(int cameraBlockX, int volumeMinY, int cameraBlockZ) {
        int minX = (VoxelVolumeLayout.sectionOf(cameraBlockX) - CHUNKS_BEFORE_CENTER) * SIZE;
        int minZ = (VoxelVolumeLayout.sectionOf(cameraBlockZ) - CHUNKS_BEFORE_CENTER) * SIZE;
        return new RtxArea(minX, volumeMinY, minZ);
    }

    long revision(RtxArea area) {
        long latest = 0L;
        for (int sectionY = 0; sectionY < VoxelVolumeLayout.SECTIONS_PER_AXIS; sectionY++) {
            for (int sectionZ = 0; sectionZ < AREA_CHUNKS; sectionZ++) {
                for (int sectionX = 0; sectionX < AREA_CHUNKS; sectionX++) {
                    int slot = slotOf(area, sectionX, sectionY, sectionZ);
                    latest = Math.max(latest, keys[slot] == keyOf(area, sectionX, sectionY, sectionZ) ? revisions[slot] : 0L);
                }
            }
        }
        return latest;
    }

    int collectExposed(RtxArea area, int[] exposed) {
        fillOpaque(area);
        int count = 0;
        for (int y = 0; y < AREA_HEIGHT; y++) {
            for (int z = 0; z < AREA_WIDTH; z++) {
                for (int x = 0; x < AREA_WIDTH; x++) {
                    if (opaque[areaIndex(x, y, z)] && isExposed(x, y, z)) {
                        exposed[count++] = areaIndex(x, y, z);
                    }
                }
            }
        }
        return count;
    }

    static int areaX(int areaIndex) {
        return areaIndex % AREA_WIDTH;
    }

    static int areaZ(int areaIndex) {
        return areaIndex / AREA_WIDTH % AREA_WIDTH;
    }

    static int areaY(int areaIndex) {
        return areaIndex / (AREA_WIDTH * AREA_WIDTH);
    }

    static int areaBlocks() {
        return AREA_WIDTH * AREA_HEIGHT * AREA_WIDTH;
    }

    private void fillOpaque(RtxArea area) {
        Arrays.fill(opaque, false);
        for (int sectionY = 0; sectionY < VoxelVolumeLayout.SECTIONS_PER_AXIS; sectionY++) {
            for (int sectionZ = 0; sectionZ < AREA_CHUNKS; sectionZ++) {
                for (int sectionX = 0; sectionX < AREA_CHUNKS; sectionX++) {
                    int slot = slotOf(area, sectionX, sectionY, sectionZ);
                    if (keys[slot] == keyOf(area, sectionX, sectionY, sectionZ)) {
                        copySection(blocks[slot], sectionX * SIZE, sectionY * SIZE, sectionZ * SIZE);
                    }
                }
            }
        }
    }

    private void copySection(byte[] section, int originX, int originY, int originZ) {
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    opaque[areaIndex(originX + x, originY + y, originZ + z)] = (section[index(x, y, z)] & OCCUPANCY_MASK) == VoxelClass.OPAQUE;
                }
            }
        }
    }

    private boolean isExposed(int x, int y, int z) {
        for (int[] neighbor : NEIGHBORS) {
            int nx = x + neighbor[0];
            int ny = y + neighbor[1];
            int nz = z + neighbor[2];
            boolean inside = nx >= 0 && nx < AREA_WIDTH && ny >= 0 && ny < AREA_HEIGHT && nz >= 0 && nz < AREA_WIDTH;
            if (!inside || !opaque[areaIndex(nx, ny, nz)]) {
                return true;
            }
        }
        return false;
    }

    private static int slotOf(RtxArea area, int sectionX, int sectionY, int sectionZ) {
        return VoxelVolumeLayout.slot(
            VoxelVolumeLayout.sectionOf(area.minX()) + sectionX,
            VoxelVolumeLayout.sectionOf(area.minY()) + sectionY,
            VoxelVolumeLayout.sectionOf(area.minZ()) + sectionZ
        );
    }

    private static long keyOf(RtxArea area, int sectionX, int sectionY, int sectionZ) {
        return VoxelVolumeLayout.sectionKey(
            VoxelVolumeLayout.sectionOf(area.minX()) + sectionX,
            VoxelVolumeLayout.sectionOf(area.minY()) + sectionY,
            VoxelVolumeLayout.sectionOf(area.minZ()) + sectionZ
        );
    }

    private static int index(int x, int y, int z) {
        return x + SIZE * (z + SIZE * y);
    }

    private static int areaIndex(int x, int y, int z) {
        return x + AREA_WIDTH * (z + AREA_WIDTH * y);
    }
}
