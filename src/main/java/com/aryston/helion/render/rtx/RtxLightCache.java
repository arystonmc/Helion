package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

final class RtxLightCache {
    static final int QUEUE_WIDTH = 512;
    static final int QUEUE_ROWS = 256;

    private static final String LABEL = "Helion Path Tracing Light Cache";
    private static final int USAGE = VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    private static final long ENTRIES = 1L << 18;
    private static final long ENTRY_BYTES = 64L;
    private static final long QUEUE_BYTES = (1L + (long) QUEUE_WIDTH * QUEUE_ROWS) * Integer.BYTES;
    private static final int PAIR = 2;
    private static final long WHOLE_BUFFER = 0L;
    private static final int ZERO = 0;

    private final RtxBuffer entries;
    private final RtxBuffer[] queues = new RtxBuffer[PAIR];
    private final TrackedResource tracked;
    private boolean cleared;
    private int filling;

    private RtxLightCache(RtxDevice device, GpuResources resources) {
        entries = RtxBuffer.create(device, ENTRIES * ENTRY_BYTES, USAGE, false);
        for (int slot = 0; slot < PAIR; slot++) {
            queues[slot] = RtxBuffer.create(device, QUEUE_BYTES, USAGE, false);
        }
        tracked = resources.track(LABEL, ENTRIES * ENTRY_BYTES + QUEUE_BYTES * PAIR, () -> {
            entries.destroy();
            for (RtxBuffer queue : queues) {
                queue.destroy();
            }
        });
    }

    static RtxLightCache create(RtxDevice device, GpuResources resources) {
        return new RtxLightCache(device, resources);
    }

    boolean clearIfNew(VkCommandBuffer commandBuffer) {
        if (cleared) {
            return false;
        }
        cleared = true;
        VK10.vkCmdFillBuffer(commandBuffer, entries.buffer(), WHOLE_BUFFER, VK10.VK_WHOLE_SIZE, ZERO);
        for (RtxBuffer queue : queues) {
            VK10.vkCmdFillBuffer(commandBuffer, queue.buffer(), WHOLE_BUFFER, VK10.VK_WHOLE_SIZE, ZERO);
        }
        return true;
    }

    void advance() {
        filling = 1 - filling;
    }

    RtxBinding entries() {
        return binding(entries);
    }

    RtxBinding filling() {
        return binding(queues[filling]);
    }

    RtxBinding queued() {
        return binding(queues[1 - filling]);
    }

    void release(GpuResources resources) {
        resources.release(tracked);
    }

    private static RtxBinding binding(RtxBuffer buffer) {
        return new RtxBinding.StorageBuffer(buffer.buffer(), WHOLE_BUFFER, buffer.size());
    }
}
