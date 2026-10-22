package com.aryston.helion.render.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VoxelVolumeLayoutTest {
    private static final int FAR_SECTION = 1_874_999;
    private static final int LOW_SECTION = -4;

    @Test
    void atlasHoldsEveryBrickExactly() {
        assertEquals(2048, VoxelVolumeLayout.ATLAS_WIDTH);
        assertEquals(1024, VoxelVolumeLayout.ATLAS_HEIGHT);
        assertEquals(VoxelVolumeLayout.SECTION_COUNT * VoxelVolumeLayout.BRICK_BYTES, VoxelVolumeLayout.ATLAS_BYTES);
        Set<Long> origins = new HashSet<>();
        for (int slot = 0; slot < VoxelVolumeLayout.SECTION_COUNT; slot++) {
            int x = VoxelVolumeLayout.brickX(slot);
            int y = VoxelVolumeLayout.brickY(slot);
            assertTrue(x + VoxelVolumeLayout.BRICK_SIZE <= VoxelVolumeLayout.ATLAS_WIDTH);
            assertTrue(y + VoxelVolumeLayout.BRICK_SIZE <= VoxelVolumeLayout.ATLAS_HEIGHT);
            origins.add((long) x << 32 | y);
        }
        assertEquals(VoxelVolumeLayout.SECTION_COUNT, origins.size());
    }

    @Test
    void everyBlockOfASectionHasItsOwnTexel() {
        Set<Integer> indices = new HashSet<>();
        for (int y = 0; y < VoxelVolumeLayout.SECTION_SIZE; y++) {
            for (int z = 0; z < VoxelVolumeLayout.SECTION_SIZE; z++) {
                for (int x = 0; x < VoxelVolumeLayout.SECTION_SIZE; x++) {
                    int index = VoxelVolumeLayout.brickIndex(x, y, z);
                    assertTrue(index >= 0 && index < VoxelVolumeLayout.BRICK_BYTES);
                    indices.add(index);
                }
            }
        }
        assertEquals(VoxelVolumeLayout.BRICK_BYTES, indices.size());
        assertEquals(VoxelVolumeLayout.BRICK_SIZE * 16 + 16 + 3, VoxelVolumeLayout.brickIndex(3, 5, 0));
    }

    @Test
    void negativeBlocksFallIntoTheSectionBelow() {
        assertEquals(-1, VoxelVolumeLayout.sectionOf(-1));
        assertEquals(15, VoxelVolumeLayout.localOf(-1));
        assertEquals(-2, VoxelVolumeLayout.sectionOf(-17));
        assertEquals(0, VoxelVolumeLayout.sectionOf(15));
    }

    @Test
    void windowSlotsAreUniqueAndWrapAround() {
        int min = VoxelVolumeLayout.windowMin(LOW_SECTION);
        Set<Integer> slots = new HashSet<>();
        for (int x = min; x < min + VoxelVolumeLayout.SECTIONS_PER_AXIS; x++) {
            for (int y = min; y < min + VoxelVolumeLayout.SECTIONS_PER_AXIS; y++) {
                for (int z = min; z < min + VoxelVolumeLayout.SECTIONS_PER_AXIS; z++) {
                    slots.add(VoxelVolumeLayout.slot(x, y, z));
                }
            }
        }
        assertEquals(VoxelVolumeLayout.SECTION_COUNT, slots.size());
        assertEquals(VoxelVolumeLayout.slot(min, 0, 0), VoxelVolumeLayout.slot(min + VoxelVolumeLayout.SECTIONS_PER_AXIS, 0, 0));
        for (int axis = 0; axis < VoxelVolumeLayout.SECTIONS_PER_AXIS; axis++) {
            int section = VoxelVolumeLayout.sectionForSlotAxis(axis, min);
            assertTrue(VoxelVolumeLayout.inWindow(section, min));
            assertEquals(axis, VoxelVolumeLayout.slotAxis(section));
        }
    }

    @Test
    void sectionKeysRoundTripLikeVanillaSectionNodes() {
        long key = VoxelVolumeLayout.sectionKey(-FAR_SECTION, LOW_SECTION, FAR_SECTION);
        assertEquals(-FAR_SECTION, VoxelVolumeLayout.keyX(key));
        assertEquals(LOW_SECTION, VoxelVolumeLayout.keyY(key));
        assertEquals(FAR_SECTION, VoxelVolumeLayout.keyZ(key));
        assertEquals(1L << 42 | 2L << 20 | 3L, VoxelVolumeLayout.sectionKey(1, 3, 2));
    }

    @Test
    void movingOneSectionOnlyReloadsTheEnteringSlab() {
        VoxelVolumeWindow window = new VoxelVolumeWindow();
        window.recenter(0, 0, 0);
        fill(window);
        assertEquals(0, window.pendingCount());
        assertTrue(window.recenter(1, 0, 0));
        int slab = VoxelVolumeLayout.SECTIONS_PER_AXIS * VoxelVolumeLayout.SECTIONS_PER_AXIS;
        assertEquals(slab, window.pendingCount());
        long first = window.nextPending();
        assertEquals(VoxelVolumeLayout.windowMin(1) + VoxelVolumeLayout.SECTIONS_PER_AXIS - 1, VoxelVolumeLayout.keyX(first));
        assertFalse(window.recenter(1, 0, 0));
    }

    @Test
    void nearestSectionsComeFirstAndChangesOutsideAreIgnored() {
        VoxelVolumeWindow window = new VoxelVolumeWindow();
        window.recenter(2, -1, 5);
        assertEquals(VoxelVolumeLayout.sectionKey(2, -1, 5), window.nextPending());
        fill(window);
        window.markChanged(VoxelVolumeLayout.sectionKey(100, 0, 0));
        assertEquals(0, window.pendingCount());
        long inside = VoxelVolumeLayout.sectionKey(3, -1, 5);
        window.markChanged(inside);
        assertEquals(1, window.pendingCount());
        assertEquals(inside, window.nextPending());
        window.markReady(inside);
        assertEquals(VoxelVolumeWindow.NO_SECTION, window.nextPending());
        assertNotEquals(window.minBlockX(), window.minBlockZ());
    }

    private static void fill(VoxelVolumeWindow window) {
        for (long key = window.nextPending(); key != VoxelVolumeWindow.NO_SECTION; key = window.nextPending()) {
            window.markReady(key);
        }
    }
}
