package com.aryston.helion.render.voxel;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class VoxelTexture {
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
    private static final int SINGLE_LAYER = 1;
    private static final int SINGLE_MIP = 1;
    private static final int BASE_LEVEL = 0;
    private static final int FIRST_LAYER = 0;
    private static final int ORIGIN = 0;

    private final String label;
    private final GpuFormat format;
    private final int width;
    private final int height;
    private @Nullable GpuTexture texture;
    private @Nullable GpuTextureView view;
    private @Nullable TrackedResource tracked;

    VoxelTexture(String label, GpuFormat format, int width, int height) {
        this.label = label;
        this.format = format;
        this.width = width;
        this.height = height;
    }

    boolean allocated() {
        return texture != null;
    }

    void allocate(GpuResources resources) {
        GpuTexture created = RenderSystem.getDevice().createTexture(label, USAGE, format, width, height, SINGLE_LAYER, SINGLE_MIP);
        GpuTextureView createdView = RenderSystem.getDevice().createTextureView(created);
        texture = created;
        view = createdView;
        tracked = resources.track(label, (long) width * height * format.blockSize(), () -> {
            createdView.close();
            created.close();
        });
    }

    void write(ByteBuffer data, int x, int y, int regionWidth, int regionHeight) {
        RenderSystem.getDevice().createCommandEncoder()
            .writeToTexture(Objects.requireNonNull(texture), data, BASE_LEVEL, FIRST_LAYER, x, y, regionWidth, regionHeight);
    }

    void writeAll(ByteBuffer data) {
        write(data, ORIGIN, ORIGIN, width, height);
    }

    GpuTextureView view() {
        return Objects.requireNonNull(view);
    }

    void close(GpuResources resources) {
        if (tracked != null) {
            resources.release(tracked);
        }
        tracked = null;
        texture = null;
        view = null;
    }
}
