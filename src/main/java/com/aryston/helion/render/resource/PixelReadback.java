package com.aryston.helion.render.resource;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class PixelReadback<T> {
    private static final int BUFFER_COUNT = 3;
    private static final int USAGE = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
    private static final int BASE_MIP_LEVEL = 0;
    private static final long BUFFER_OFFSET = 0L;

    private final GpuResources resources;
    private final String label;
    private final int bytes;
    private final Function<ByteBuffer, T> decode;
    private final List<GpuBuffer> buffers = new ArrayList<>();
    private final boolean[] inFlight = new boolean[BUFFER_COUNT];
    private @Nullable TrackedResource tracked;
    private @Nullable T latest;

    public PixelReadback(GpuResources resources, String label, int bytes, Function<ByteBuffer, T> decode) {
        this.resources = resources;
        this.label = label;
        this.bytes = bytes;
        this.decode = decode;
    }

    public Optional<T> latest() {
        return Optional.ofNullable(latest);
    }

    public void request(GpuTexture pixel) {
        createBuffers();
        for (int index = 0; index < BUFFER_COUNT; index++) {
            if (!inFlight[index]) {
                read(pixel, index);
                return;
            }
        }
    }

    public void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        tracked = null;
        buffers.clear();
        latest = null;
        for (int index = 0; index < BUFFER_COUNT; index++) {
            inFlight[index] = false;
        }
    }

    private void read(GpuTexture pixel, int index) {
        GpuBuffer buffer = buffers.get(index);
        inFlight[index] = true;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(pixel, buffer, BUFFER_OFFSET, () -> {
            if (!buffers.contains(buffer)) {
                return;
            }
            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                latest = decode.apply(view.data().order(ByteOrder.LITTLE_ENDIAN));
            }
            inFlight[index] = false;
        }, BASE_MIP_LEVEL);
    }

    private void createBuffers() {
        if (!buffers.isEmpty()) {
            return;
        }
        List<GpuBuffer> created = new ArrayList<>();
        for (int index = 0; index < BUFFER_COUNT; index++) {
            created.add(RenderSystem.getDevice().createBuffer(() -> label, USAGE, bytes));
        }
        buffers.addAll(created);
        tracked = resources.track(label, (long) bytes * BUFFER_COUNT, () -> created.forEach(GpuBuffer::close));
    }
}
