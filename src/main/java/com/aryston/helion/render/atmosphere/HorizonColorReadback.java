package com.aryston.helion.render.atmosphere;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

final class HorizonColorReadback {
    private static final String LABEL = "Helion Horizon Color Readback";
    private static final int BUFFER_COUNT = 3;
    private static final int USAGE = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
    private static final int RGBA8_BYTES = 4;
    private static final float MAX_CHANNEL = 255.0F;
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final int BASE_MIP_LEVEL = 0;
    private static final long BUFFER_OFFSET = 0L;

    private final GpuResources resources;
    private final List<GpuBuffer> buffers = new ArrayList<>();
    private final boolean[] inFlight = new boolean[BUFFER_COUNT];
    private @Nullable TrackedResource tracked;
    private @Nullable Vector3f latest;

    HorizonColorReadback(GpuResources resources) {
        this.resources = resources;
    }

    Optional<Vector3fc> latest() {
        return Optional.ofNullable(latest);
    }

    void request(GpuTexture horizon) {
        createBuffers();
        for (int index = 0; index < BUFFER_COUNT; index++) {
            if (!inFlight[index]) {
                read(horizon, index);
                return;
            }
        }
    }

    void close() {
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

    private void read(GpuTexture horizon, int index) {
        GpuBuffer buffer = buffers.get(index);
        inFlight[index] = true;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(horizon, buffer, BUFFER_OFFSET, () -> {
            if (!buffers.contains(buffer)) {
                return;
            }
            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                ByteBuffer data = view.data();
                latest = new Vector3f(channel(data, RED), channel(data, GREEN), channel(data, BLUE));
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
            created.add(RenderSystem.getDevice().createBuffer(() -> LABEL, USAGE, RGBA8_BYTES));
        }
        buffers.addAll(created);
        tracked = resources.track(LABEL, (long) RGBA8_BYTES * BUFFER_COUNT, () -> created.forEach(GpuBuffer::close));
    }

    private static float channel(ByteBuffer data, int index) {
        return Byte.toUnsignedInt(data.get(index)) / MAX_CHANNEL;
    }
}
