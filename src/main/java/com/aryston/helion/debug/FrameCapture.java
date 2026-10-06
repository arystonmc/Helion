package com.aryston.helion.debug;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

final class FrameCapture {
    private static final String LABEL = "Helion Parity Capture";
    private static final int READBACK_USAGE = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
    private static final int BASE_MIP_LEVEL = 0;
    private static final long BUFFER_OFFSET = 0L;

    private final int width;
    private final int height;
    private final int colorBlockSize;
    private final int depthBlockSize;
    private byte @Nullable [] color;
    private byte @Nullable [] depth;

    private FrameCapture(int width, int height, int colorBlockSize, int depthBlockSize) {
        this.width = width;
        this.height = height;
        this.colorBlockSize = colorBlockSize;
        this.depthBlockSize = depthBlockSize;
    }

    static FrameCapture of(RenderTarget target, GpuResources resources) {
        GpuTexture colorTexture = Objects.requireNonNull(target.getColorTexture());
        GpuTexture depthTexture = Objects.requireNonNull(target.getDepthTexture());
        FrameCapture capture = new FrameCapture(
            colorTexture.getWidth(BASE_MIP_LEVEL),
            colorTexture.getHeight(BASE_MIP_LEVEL),
            colorTexture.getFormat().blockSize(),
            depthTexture.getFormat().blockSize()
        );
        read(colorTexture, resources, bytes -> capture.color = bytes);
        read(depthTexture, resources, bytes -> capture.depth = bytes);
        return capture;
    }

    boolean isComplete() {
        return color != null && depth != null;
    }

    ParityResult compareWith(FrameCapture other) {
        return ParityResult.compare(
            Objects.requireNonNull(color), Objects.requireNonNull(other.color), colorBlockSize,
            Objects.requireNonNull(depth), Objects.requireNonNull(other.depth), depthBlockSize
        );
    }

    ParityDifferenceImage differencesTo(FrameCapture other) {
        return new ParityDifferenceImage(
            width, height,
            Objects.requireNonNull(color), Objects.requireNonNull(other.color), colorBlockSize,
            Objects.requireNonNull(depth), Objects.requireNonNull(other.depth), depthBlockSize
        );
    }

    private static void read(GpuTexture texture, GpuResources resources, Consumer<byte[]> sink) {
        int size = texture.getWidth(BASE_MIP_LEVEL) * texture.getHeight(BASE_MIP_LEVEL) * texture.getFormat().blockSize();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> LABEL, READBACK_USAGE, size);
        TrackedResource tracked = resources.track(LABEL, size, buffer::close);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, BUFFER_OFFSET, () -> {
            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                byte[] bytes = new byte[size];
                view.data().get(bytes);
                sink.accept(bytes);
            }
            resources.forget(tracked);
            buffer.close();
        }, BASE_MIP_LEVEL);
    }
}
