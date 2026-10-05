package com.aryston.helion.render.resource;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuFence;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class UniformRing {
    private static final int BUFFER_COUNT = 3;
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE;
    private static final long WAIT_FOREVER = -1L;

    private final GpuBuffer[] buffers = new GpuBuffer[BUFFER_COUNT];
    private final @Nullable GpuFence[] fences = new GpuFence[BUFFER_COUNT];
    private final TrackedResource tracked;
    private final GpuResources resources;
    private int current;

    public UniformRing(String label, int size, GpuResources resources) {
        this.resources = resources;
        for (int index = 0; index < BUFFER_COUNT; index++) {
            int bufferIndex = index;
            buffers[index] = RenderSystem.getDevice().createBuffer(() -> label + " #" + bufferIndex, USAGE, size);
        }
        tracked = resources.track(label, (long) size * BUFFER_COUNT, this::destroy);
    }

    public GpuBuffer write(Consumer<Std140Builder> writer) {
        GpuFence fence = fences[current];
        if (fence != null) {
            fence.awaitCompletion(WAIT_FOREVER);
            fence.close();
            fences[current] = null;
        }
        GpuBuffer buffer = buffers[current];
        try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
            writer.accept(Std140Builder.intoBuffer(view.data()));
        }
        return buffer;
    }

    public void rotate() {
        GpuFence previous = fences[current];
        if (previous != null) {
            previous.close();
        }
        fences[current] = RenderSystem.getDevice().createCommandEncoder().createFence();
        current = (current + 1) % BUFFER_COUNT;
    }

    public void close() {
        resources.release(tracked);
    }

    private void destroy() {
        for (int index = 0; index < BUFFER_COUNT; index++) {
            buffers[index].close();
            GpuFence fence = fences[index];
            if (fence != null) {
                fence.close();
            }
        }
    }
}
