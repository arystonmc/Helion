package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.LongPredicate;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;

final class RtxLightScene {
    static final int GRID_RADIUS = RtxSectionInbox.RADIUS;
    static final int GRID_SIZE = 2 * GRID_RADIUS + 1;
    static final int CELLS = GRID_SIZE * GRID_SIZE * GRID_SIZE;
    static final int LIGHTS_PER_CELL = 32;

    private static final String LABEL = "Helion Path Tracing Lights";
    private static final int FRAMES_IN_FLIGHT = 4;
    private static final int USAGE = VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    private static final int ENTRY_INTS = 4;
    private static final int RECORD_ENTRIES = RtxSectionUpdate.LIGHT_INTS / ENTRY_INTS;
    private static final long SLOT_BYTES = (long) (CELLS + CELLS * LIGHTS_PER_CELL * RECORD_ENTRIES) * ENTRY_INTS * Integer.BYTES;
    private static final int[] COORDINATES = {0, 1, 2, 4, 5, 6};
    private static final int[] AXIS = {0, 1, 2, 0, 1, 2};
    private static final int NEIGHBOR_COUNT = 27;
    private static final int[][] NEIGHBORS = neighbors();

    private final Long2ObjectOpenHashMap<int[]> sections = new Long2ObjectOpenHashMap<>();
    private final RtxBuffer[] buffers;
    private final TrackedResource tracked;
    private int[] data = new int[0];
    private @Nullable RtxSectionPos built;
    private boolean dirty = true;
    private int frame;

    private RtxLightScene(RtxBuffer[] buffers, TrackedResource tracked) {
        this.buffers = buffers;
        this.tracked = tracked;
    }

    static RtxLightScene create(RtxDevice device, GpuResources resources) {
        RtxBuffer[] buffers = new RtxBuffer[FRAMES_IN_FLIGHT];
        for (int slot = 0; slot < FRAMES_IN_FLIGHT; slot++) {
            buffers[slot] = RtxBuffer.create(device, SLOT_BYTES, USAGE, true);
        }
        TrackedResource tracked = resources.track(LABEL, SLOT_BYTES * FRAMES_IN_FLIGHT, () -> Arrays.stream(buffers).forEach(RtxBuffer::destroy));
        return new RtxLightScene(buffers, tracked);
    }

    void put(long key, int[] lights) {
        int[] previous = lights.length == 0 ? sections.remove(key) : sections.put(key, lights);
        dirty |= previous != null || lights.length > 0;
    }

    void remove(long key) {
        dirty |= sections.remove(key) != null;
    }

    void removeIf(LongPredicate far) {
        dirty |= sections.long2ObjectEntrySet().removeIf(entry -> far.test(entry.getLongKey()));
    }

    void write(RtxSectionPos anchor) {
        if (dirty || !anchor.equals(built)) {
            data = build(anchor);
            built = anchor;
            dirty = false;
        }
        frame = (frame + 1) % FRAMES_IN_FLIGHT;
        long target = buffers[frame].mapped();
        MemoryUtil.memSet(target, 0, (long) CELLS * ENTRY_INTS * Integer.BYTES);
        MemoryUtil.memIntBuffer(target, data.length).put(data);
    }

    RtxBuffer buffer() {
        return buffers[frame];
    }

    void release(GpuResources resources) {
        resources.release(tracked);
    }

    private int[] build(RtxSectionPos anchor) {
        if (sections.isEmpty()) {
            return new int[0];
        }
        int[] table = new int[(CELLS + CELLS * LIGHTS_PER_CELL * RECORD_ENTRIES) * ENTRY_INTS];
        int record = 0;
        for (int cell = 0; cell < CELLS; cell++) {
            int cellX = cell % GRID_SIZE - GRID_RADIUS;
            int cellY = cell / GRID_SIZE % GRID_SIZE - GRID_RADIUS;
            int cellZ = cell / (GRID_SIZE * GRID_SIZE) - GRID_RADIUS;
            int first = record;
            for (int[] neighbor : NEIGHBORS) {
                int sectionX = anchor.sectionX() + cellX + neighbor[0];
                int sectionY = anchor.sectionY() + cellY + neighbor[1];
                int sectionZ = anchor.sectionZ() + cellZ + neighbor[2];
                int[] lights = sections.get(RtxSectionKey.of(sectionX, sectionY, sectionZ));
                if (lights == null) {
                    continue;
                }
                int[] offset = {
                    (sectionX - anchor.sectionX()) * RtxSectionKey.SECTION_SIZE,
                    (sectionY - anchor.sectionY()) * RtxSectionKey.SECTION_SIZE,
                    (sectionZ - anchor.sectionZ()) * RtxSectionKey.SECTION_SIZE
                };
                for (int light = 0; light < lights.length && record - first < LIGHTS_PER_CELL; light += RtxSectionUpdate.LIGHT_INTS) {
                    int at = (CELLS + record * RECORD_ENTRIES) * ENTRY_INTS;
                    System.arraycopy(lights, light, table, at, RtxSectionUpdate.LIGHT_INTS);
                    for (int coordinate = 0; coordinate < COORDINATES.length; coordinate++) {
                        int slot = at + COORDINATES[coordinate];
                        table[slot] = Float.floatToIntBits(Float.intBitsToFloat(table[slot]) + offset[AXIS[coordinate]]);
                    }
                    record++;
                }
            }
            table[cell * ENTRY_INTS] = first;
            table[cell * ENTRY_INTS + 1] = record - first;
        }
        return Arrays.copyOf(table, (CELLS + record * RECORD_ENTRIES) * ENTRY_INTS);
    }

    private static int[][] neighbors() {
        int[][] offsets = new int[NEIGHBOR_COUNT][];
        int index = 0;
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    offsets[index++] = new int[] {x, y, z};
                }
            }
        }
        Arrays.sort(offsets, Comparator.comparingInt(offset -> Math.abs(offset[0]) + Math.abs(offset[1]) + Math.abs(offset[2])));
        return offsets;
    }
}
