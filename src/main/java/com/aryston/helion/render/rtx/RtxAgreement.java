package com.aryston.helion.render.rtx;

import java.util.Arrays;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;

final class RtxAgreement {
    private static final int COUNTERS = 4;
    private static final int SLOTS = 8;
    private static final int TRACED = 0;
    private static final int RAY_QUERY_BLOCKED = 1;
    private static final int GRID_WALK_BLOCKED = 2;
    private static final int DISAGREE = 3;
    private static final long RANGE = (long) COUNTERS * Integer.BYTES;
    private static final double PERCENT = 100.0;
    private static final long FIRST_SLOT_OFFSET = 0L;

    private final RtxBuffer buffer;
    private final long slotStride;
    private final boolean[] written = new boolean[SLOTS];
    private final long[] totals = new long[COUNTERS];
    private int next;

    private RtxAgreement(RtxBuffer buffer, long slotStride) {
        this.buffer = buffer;
        this.slotStride = slotStride;
    }

    static RtxAgreement create(RtxDevice device) {
        long alignment = device.storageOffsetAlignment();
        long stride = (RANGE + alignment - 1) / alignment * alignment;
        RtxBuffer buffer = RtxBuffer.create(device, stride * SLOTS, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, true);
        MemoryUtil.memSet(buffer.mapped(), 0, buffer.size());
        return new RtxAgreement(buffer, stride);
    }

    long beginSlotFor(RtxTraceMode mode) {
        return mode == RtxTraceMode.COMPARE ? beginSlot() : FIRST_SLOT_OFFSET;
    }

    private long beginSlot() {
        int slot = next;
        next = (next + 1) % SLOTS;
        long address = buffer.mapped() + slot * slotStride;
        if (written[slot]) {
            for (int counter = 0; counter < COUNTERS; counter++) {
                totals[counter] += Integer.toUnsignedLong(MemoryUtil.memGetInt(address + (long) counter * Integer.BYTES));
            }
        }
        MemoryUtil.memSet(address, 0, RANGE);
        written[slot] = true;
        return slot * slotStride;
    }

    long buffer() {
        return buffer.buffer();
    }

    long range() {
        return RANGE;
    }

    long bytes() {
        return buffer.size();
    }

    String takeSummary() {
        long traced = totals[TRACED];
        String summary = traced == 0L
            ? "no traced pixels"
            : String.format(
                "%d traced pixels, ray query blocked %.2f %%, grid walk blocked %.2f %%, disagree %.3f %%",
                traced, percent(totals[RAY_QUERY_BLOCKED], traced), percent(totals[GRID_WALK_BLOCKED], traced), percent(totals[DISAGREE], traced)
            );
        Arrays.fill(totals, 0L);
        return summary;
    }

    void destroy(VkDevice vkDevice) {
        buffer.destroy(vkDevice);
    }

    private static double percent(long part, long whole) {
        return PERCENT * part / whole;
    }
}
