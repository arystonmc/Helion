package com.aryston.helion.render.water;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

final class WaterRainCover {
    static final int SIZE = 64;

    private static final String LABEL = "Helion Water Rain Cover";
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
    private static final GpuFormat FORMAT = GpuFormat.R32_FLOAT;
    private static final int MASK = SIZE - 1;
    private static final int HALF = SIZE / 2;
    private static final int ROWS_PER_FRAME = 16;
    private static final float COVERED = Float.MAX_VALUE;
    private static final int SINGLE = 1;
    private static final int BASE_LEVEL = 0;
    private static final int FIRST_LAYER = 0;
    private static final int ORIGIN = 0;

    private final GpuResources resources;
    private @Nullable GpuTexture texture;
    private @Nullable GpuTextureView view;
    private @Nullable ByteBuffer cells;
    private @Nullable TrackedResource tracked;
    private int nextRow;

    WaterRainCover(GpuResources resources) {
        this.resources = resources;
    }

    GpuTextureView refresh(WaveSource source, double cameraX, double cameraZ) {
        ByteBuffer data = cells();
        int cornerX = (int) Math.floor(cameraX) - HALF;
        int cornerZ = (int) Math.floor(cameraZ) - HALF;
        for (int row = 0; row < ROWS_PER_FRAME; row++) {
            int blockZ = cornerZ + nextRow;
            for (int column = 0; column < SIZE; column++) {
                int blockX = cornerX + column;
                data.putFloat(((blockZ & MASK) * SIZE + (blockX & MASK)) * Float.BYTES, source.rainFloor(blockX, blockZ));
            }
            nextRow = (nextRow + 1) % SIZE;
        }
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture(), data, BASE_LEVEL, FIRST_LAYER, ORIGIN, ORIGIN, SIZE, SIZE);
        return Objects.requireNonNull(view);
    }

    void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        if (cells != null) {
            MemoryUtil.memFree(cells);
        }
        tracked = null;
        texture = null;
        view = null;
        cells = null;
        nextRow = 0;
    }

    private ByteBuffer cells() {
        if (cells == null) {
            cells = MemoryUtil.memAlloc(SIZE * SIZE * Float.BYTES);
            for (int index = 0; index < SIZE * SIZE; index++) {
                cells.putFloat(index * Float.BYTES, COVERED);
            }
        }
        return cells;
    }

    private GpuTexture texture() {
        if (texture == null) {
            GpuTexture created = RenderSystem.getDevice().createTexture(LABEL, USAGE, FORMAT, SIZE, SIZE, SINGLE, SINGLE);
            GpuTextureView createdView = RenderSystem.getDevice().createTextureView(created);
            tracked = resources.track(LABEL, (long) SIZE * SIZE * FORMAT.blockSize(), () -> {
                createdView.close();
                created.close();
            });
            texture = created;
            view = createdView;
        }
        return texture;
    }
}
