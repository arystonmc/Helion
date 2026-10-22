package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import org.jspecify.annotations.Nullable;

final class RtxSharedScratch {
    private static final String LABEL = "Helion Ray Tracing Build Scratch";
    private static final long INITIAL_BYTES = 1L << 20;

    private @Nullable RtxBuffer buffer;
    private @Nullable TrackedResource tracked;
    private long capacity;
    private long address;

    long address(RtxDevice device, GpuResources resources, long size) {
        if (buffer == null || size > capacity) {
            grow(device, resources, Math.max(Long.highestOneBit(Math.max(size, INITIAL_BYTES) - 1) << 1, INITIAL_BYTES));
        }
        return address;
    }

    void close(GpuResources resources) {
        if (tracked != null) {
            resources.release(tracked);
        }
        tracked = null;
        buffer = null;
        capacity = 0L;
    }

    private void grow(RtxDevice device, GpuResources resources, long size) {
        close(resources);
        RtxBuffer created = RtxAccelerationStructure.scratchBuffer(device, size);
        tracked = resources.track(LABEL, created.size(), created::destroy);
        buffer = created;
        capacity = size;
        address = RtxAccelerationStructure.aligned(created.address(), device.scratchAlignment());
    }
}
