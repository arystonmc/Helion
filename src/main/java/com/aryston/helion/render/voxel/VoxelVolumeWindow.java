package com.aryston.helion.render.voxel;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

public final class VoxelVolumeWindow {
    public static final long NO_SECTION = Long.MIN_VALUE;

    private static final int[] NEAREST_FIRST = nearestFirstOffsets();

    private final long[] held = new long[VoxelVolumeLayout.SECTION_COUNT];
    private final boolean[] ready = new boolean[VoxelVolumeLayout.SECTION_COUNT];
    private boolean centered;
    private int centerX;
    private int centerY;
    private int centerZ;
    private int pending = VoxelVolumeLayout.SECTION_COUNT;

    public VoxelVolumeWindow() {
        Arrays.fill(held, NO_SECTION);
    }

    public boolean recenter(int sectionX, int sectionY, int sectionZ) {
        if (centered && sectionX == centerX && sectionY == centerY && sectionZ == centerZ) {
            return false;
        }
        centered = true;
        centerX = sectionX;
        centerY = sectionY;
        centerZ = sectionZ;
        int minX = VoxelVolumeLayout.windowMin(sectionX);
        int minY = VoxelVolumeLayout.windowMin(sectionY);
        int minZ = VoxelVolumeLayout.windowMin(sectionZ);
        for (int slot = 0; slot < VoxelVolumeLayout.SECTION_COUNT; slot++) {
            long expected = VoxelVolumeLayout.sectionKey(
                VoxelVolumeLayout.sectionForSlotAxis(VoxelVolumeLayout.slotX(slot), minX),
                VoxelVolumeLayout.sectionForSlotAxis(VoxelVolumeLayout.slotY(slot), minY),
                VoxelVolumeLayout.sectionForSlotAxis(VoxelVolumeLayout.slotZ(slot), minZ)
            );
            if (held[slot] != expected) {
                markPending(slot);
            }
        }
        return true;
    }

    public void markChanged(long sectionKey) {
        if (contains(sectionKey)) {
            markPending(slotOf(sectionKey));
        }
    }

    public void markAllChanged() {
        for (int slot = 0; slot < VoxelVolumeLayout.SECTION_COUNT; slot++) {
            markPending(slot);
        }
    }

    public long nextPending() {
        if (!centered || pending == 0) {
            return NO_SECTION;
        }
        for (int offset : NEAREST_FIRST) {
            int sectionX = centerX + offsetX(offset);
            int sectionY = centerY + offsetY(offset);
            int sectionZ = centerZ + offsetZ(offset);
            if (!ready[VoxelVolumeLayout.slot(sectionX, sectionY, sectionZ)]) {
                return VoxelVolumeLayout.sectionKey(sectionX, sectionY, sectionZ);
            }
        }
        return NO_SECTION;
    }

    public void markReady(long sectionKey) {
        int slot = slotOf(sectionKey);
        held[slot] = sectionKey;
        if (!ready[slot]) {
            ready[slot] = true;
            pending--;
        }
    }

    public boolean contains(long sectionKey) {
        return centered
            && VoxelVolumeLayout.inWindow(VoxelVolumeLayout.keyX(sectionKey), VoxelVolumeLayout.windowMin(centerX))
            && VoxelVolumeLayout.inWindow(VoxelVolumeLayout.keyY(sectionKey), VoxelVolumeLayout.windowMin(centerY))
            && VoxelVolumeLayout.inWindow(VoxelVolumeLayout.keyZ(sectionKey), VoxelVolumeLayout.windowMin(centerZ));
    }

    public int pendingCount() {
        return pending;
    }

    public int minBlockX() {
        return VoxelVolumeLayout.windowMin(centerX) * VoxelVolumeLayout.SECTION_SIZE;
    }

    public int minBlockY() {
        return VoxelVolumeLayout.windowMin(centerY) * VoxelVolumeLayout.SECTION_SIZE;
    }

    public int minBlockZ() {
        return VoxelVolumeLayout.windowMin(centerZ) * VoxelVolumeLayout.SECTION_SIZE;
    }

    private void markPending(int slot) {
        if (ready[slot]) {
            ready[slot] = false;
            pending++;
        }
    }

    private static int slotOf(long sectionKey) {
        return VoxelVolumeLayout.slot(VoxelVolumeLayout.keyX(sectionKey), VoxelVolumeLayout.keyY(sectionKey), VoxelVolumeLayout.keyZ(sectionKey));
    }

    private static int offsetX(int offset) {
        return VoxelVolumeLayout.slotX(offset) - VoxelVolumeLayout.SECTIONS_BEFORE_CENTER;
    }

    private static int offsetY(int offset) {
        return VoxelVolumeLayout.slotY(offset) - VoxelVolumeLayout.SECTIONS_BEFORE_CENTER;
    }

    private static int offsetZ(int offset) {
        return VoxelVolumeLayout.slotZ(offset) - VoxelVolumeLayout.SECTIONS_BEFORE_CENTER;
    }

    private static int[] nearestFirstOffsets() {
        return IntStream.range(0, VoxelVolumeLayout.SECTION_COUNT)
            .boxed()
            .sorted(Comparator.comparingInt(VoxelVolumeWindow::distanceSquared))
            .mapToInt(Integer::intValue)
            .toArray();
    }

    private static int distanceSquared(int offset) {
        int x = offsetX(offset);
        int y = offsetY(offset);
        int z = offsetZ(offset);
        return x * x + y * y + z * z;
    }
}
