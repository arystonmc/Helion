package com.aryston.helion.render.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VoxelLightGridTest {
    private static final int MIN = -64;
    private static final int TORCH_LEVEL = 14;
    private static final float WHITE = 1.0F;

    @Test
    void lightReachesOnlyCellsWithinItsLevel() {
        VoxelLightGrid grid = VoxelLightGrid.build(List.of(light(MIN + 20, MIN + 20, MIN + 20, TORCH_LEVEL)), MIN, MIN, MIN, 0, 0, 0);
        assertEquals(1, grid.lightAt(VoxelLightGrid.cellIndex(2, 2, 2), 0));
        assertEquals(1, grid.lightAt(VoxelLightGrid.cellIndex(3, 2, 2), 0));
        assertEquals(VoxelLightGrid.NO_LIGHT, grid.lightAt(VoxelLightGrid.cellIndex(5, 2, 2), 0));
        assertEquals(VoxelLightGrid.NO_LIGHT, grid.lightAt(VoxelLightGrid.cellIndex(4, 4, 4), 0));
    }

    @Test
    void crowdedCellsKeepTheStrongestLights() {
        List<VoxelLight> lights = new ArrayList<>();
        for (int level = 1; level <= 15; level++) {
            lights.add(light(MIN + 4, MIN + 4, MIN + 4, level));
            lights.add(light(MIN + 5, MIN + 4, MIN + 4, level));
        }
        VoxelLightGrid grid = VoxelLightGrid.build(lights, MIN, MIN, MIN, MIN, MIN, MIN);
        Set<Integer> levels = new HashSet<>();
        for (int slot = 0; slot < VoxelLightGrid.LIGHTS_PER_CELL; slot++) {
            levels.add(grid.lights().get(grid.lightAt(0, slot) - 1).level());
        }
        assertTrue(levels.stream().allMatch(level -> level >= 8));
    }

    @Test
    void tooManyLightsKeepsTheNearest() {
        List<VoxelLight> lights = new ArrayList<>();
        for (int index = 0; index < VoxelLightGrid.MAX_LIGHTS + 100; index++) {
            lights.add(light(index, 0, 0, TORCH_LEVEL));
        }
        VoxelLightGrid grid = VoxelLightGrid.build(lights, MIN, MIN, MIN, 0, 0, 0);
        assertEquals(VoxelLightGrid.MAX_LIGHTS, grid.lights().size());
        assertTrue(grid.lights().stream().allMatch(light -> light.x() < VoxelLightGrid.MAX_LIGHTS));
    }

    @Test
    void coarseCellsMarkOnlyLightBlockers() {
        ByteBuffer brick = ByteBuffer.allocate(VoxelVolumeLayout.BRICK_BYTES);
        ByteBuffer coarse = ByteBuffer.allocate(VoxelCoarseLevel.BRICK_BYTES);
        brick.put(VoxelVolumeLayout.brickIndex(5, 9, 13), VoxelClass.OPAQUE);
        brick.put(VoxelVolumeLayout.brickIndex(0, 0, 0), VoxelClass.encode(VoxelClass.PARTIAL, TORCH_LEVEL));
        VoxelCoarseLevel.build(brick, coarse);
        int blocked = 0;
        for (int index = 0; index < VoxelCoarseLevel.BRICK_BYTES; index++) {
            blocked += coarse.get(index) == VoxelCoarseLevel.BLOCKED ? 1 : 0;
        }
        assertEquals(1, blocked);
        assertEquals(VoxelCoarseLevel.BLOCKED, coarse.get(VoxelCoarseLevel.cellIndex(1, 2, 3)));
    }

    private static VoxelLight light(int x, int y, int z, int level) {
        return new VoxelLight(x, y, z, level, WHITE, WHITE, WHITE);
    }
}
