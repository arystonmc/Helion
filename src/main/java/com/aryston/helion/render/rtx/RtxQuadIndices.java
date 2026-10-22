package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import java.nio.IntBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRAccelerationStructure;

final class RtxQuadIndices {
    private static final String LABEL = "Helion Ray Tracing Quad Indices";
    private static final int INITIAL_QUADS = 4096;
    private static final int INDICES_PER_QUAD = 6;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int[] QUAD_CORNERS = {0, 1, 2, 2, 3, 0};
    private static final int USAGE = KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;

    private @Nullable RtxBuffer buffer;
    private @Nullable TrackedResource tracked;
    private int capacity;

    long address(RtxDevice device, GpuResources resources, int quads) {
        if (buffer == null || quads > capacity) {
            grow(device, resources, Math.max(Integer.highestOneBit(Math.max(quads, INITIAL_QUADS) - 1) << 1, INITIAL_QUADS));
        }
        return buffer.address();
    }

    void close(GpuResources resources) {
        if (tracked != null) {
            resources.release(tracked);
        }
        tracked = null;
        buffer = null;
        capacity = 0;
    }

    private void grow(RtxDevice device, GpuResources resources, int quads) {
        close(resources);
        RtxBuffer created = RtxBuffer.create(device, (long) quads * INDICES_PER_QUAD * Integer.BYTES, USAGE, true);
        IntBuffer indices = MemoryUtil.memIntBuffer(created.mapped(), quads * INDICES_PER_QUAD);
        for (int quad = 0; quad < quads; quad++) {
            for (int corner : QUAD_CORNERS) {
                indices.put(quad * VERTICES_PER_QUAD + corner);
            }
        }
        tracked = resources.track(LABEL, created.size(), created::destroy);
        buffer = created;
        capacity = quads;
    }
}
