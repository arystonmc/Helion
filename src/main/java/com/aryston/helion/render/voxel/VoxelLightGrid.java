package com.aryston.helion.render.voxel;

import java.util.Comparator;
import java.util.List;

public final class VoxelLightGrid {
    public static final int CELL_SIZE = 8;
    public static final int CELLS_PER_AXIS = VoxelVolumeLayout.VOLUME_SIZE / CELL_SIZE;
    public static final int CELL_COUNT = CELLS_PER_AXIS * CELLS_PER_AXIS * CELLS_PER_AXIS;
    public static final int LIGHTS_PER_CELL = 16;
    public static final int MAX_LIGHTS = 1024;
    public static final int NO_LIGHT = 0;

    private static final float BLOCK_CENTER = 0.5F;
    private static final float DISTANCE_FALLOFF = 1.25F;

    private final List<VoxelLight> lights;
    private final short[] cells;

    private VoxelLightGrid(List<VoxelLight> lights, short[] cells) {
        this.lights = lights;
        this.cells = cells;
    }

    public static VoxelLightGrid build(List<VoxelLight> candidates, int minX, int minY, int minZ, int cameraX, int cameraY, int cameraZ) {
        List<VoxelLight> lights = candidates.stream()
            .sorted(Comparator.comparingLong(light -> distanceSquared(light, cameraX, cameraY, cameraZ)))
            .limit(MAX_LIGHTS)
            .toList();
        short[] cells = new short[CELL_COUNT * LIGHTS_PER_CELL];
        float[] priorities = new float[CELL_COUNT * LIGHTS_PER_CELL];
        int[] counts = new int[CELL_COUNT];
        for (int index = 0; index < lights.size(); index++) {
            VoxelLight light = lights.get(index);
            addLight(cells, priorities, counts, index, light.x() - minX + BLOCK_CENTER, light.y() - minY + BLOCK_CENTER,
                light.z() - minZ + BLOCK_CENTER, light.level());
        }
        return new VoxelLightGrid(lights, cells);
    }

    public static int cellIndex(int cellX, int cellY, int cellZ) {
        return cellX + CELLS_PER_AXIS * (cellY + CELLS_PER_AXIS * cellZ);
    }

    public List<VoxelLight> lights() {
        return lights;
    }

    public int lightAt(int cell, int slot) {
        return cells[cell * LIGHTS_PER_CELL + slot];
    }

    public short[] cells() {
        return cells;
    }

    private static void addLight(short[] cells, float[] priorities, int[] counts, int index, float x, float y, float z, int level) {
        int fromX = cellOf(x - level);
        int toX = cellOf(x + level);
        int fromY = cellOf(y - level);
        int toY = cellOf(y + level);
        int fromZ = cellOf(z - level);
        int toZ = cellOf(z + level);
        for (int cellZ = fromZ; cellZ <= toZ; cellZ++) {
            for (int cellY = fromY; cellY <= toY; cellY++) {
                for (int cellX = fromX; cellX <= toX; cellX++) {
                    float alongX = axisDistance(x, cellX);
                    float alongY = axisDistance(y, cellY);
                    float alongZ = axisDistance(z, cellZ);
                    float distance = (float) Math.sqrt(alongX * alongX + alongY * alongY + alongZ * alongZ) * DISTANCE_FALLOFF;
                    if (distance < level) {
                        insert(cells, priorities, counts, cellIndex(cellX, cellY, cellZ), index, level - distance);
                    }
                }
            }
        }
    }

    private static void insert(short[] cells, float[] priorities, int[] counts, int cell, int index, float priority) {
        int base = cell * LIGHTS_PER_CELL;
        int slot = counts[cell];
        if (slot < LIGHTS_PER_CELL) {
            counts[cell]++;
        } else {
            slot = weakestSlot(priorities, base);
            if (priorities[base + slot] >= priority) {
                return;
            }
        }
        cells[base + slot] = (short) (index + 1);
        priorities[base + slot] = priority;
    }

    private static int weakestSlot(float[] priorities, int base) {
        int weakest = 0;
        for (int slot = 1; slot < LIGHTS_PER_CELL; slot++) {
            if (priorities[base + slot] < priorities[base + weakest]) {
                weakest = slot;
            }
        }
        return weakest;
    }

    private static float axisDistance(float position, int cell) {
        float low = cell * CELL_SIZE;
        float high = low + CELL_SIZE;
        return Math.max(0.0F, Math.max(low - position, position - high));
    }

    private static int cellOf(float position) {
        return Math.clamp((int) Math.floor(position / CELL_SIZE), 0, CELLS_PER_AXIS - 1);
    }

    private static long distanceSquared(VoxelLight light, int x, int y, int z) {
        long dx = light.x() - x;
        long dy = light.y() - y;
        long dz = light.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
