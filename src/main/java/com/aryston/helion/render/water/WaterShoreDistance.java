package com.aryston.helion.render.water;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

final class WaterShoreDistance {
    static final int LIMIT_BLOCKS = 16;

    private static final String LABEL = "Helion Water Shore Distance";
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
    private static final GpuFormat FORMAT = GpuFormat.R8_UNORM;
    private static final int SIZE = WaterShoreMask.SIZE;
    private static final int MASK = SIZE - 1;
    private static final int STRAIGHT = 3;
    private static final int DIAGONAL = 4;
    private static final int FAR = LIMIT_BLOCKS * STRAIGHT;
    private static final int BYTE_MAX = 255;
    private static final int SINGLE = 1;
    private static final int BASE_LEVEL = 0;
    private static final int FIRST_LAYER = 0;
    private static final int ORIGIN = 0;

    private final GpuResources resources;
    private final int[] distances = new int[SIZE * SIZE];
    private @Nullable ByteBuffer bytes;
    private @Nullable GpuTexture texture;
    private @Nullable GpuTextureView view;
    private @Nullable TrackedResource tracked;

    WaterShoreDistance(GpuResources resources) {
        this.resources = resources;
    }

    void update(ByteBuffer cells, byte land, int cornerX, int cornerZ) {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                distances[row * SIZE + column] = cells.get(texel(cornerX + column, cornerZ + row)) == land ? 0 : FAR;
            }
        }
        sweepForward();
        sweepBackward();
        ByteBuffer data = bytes();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                data.put(texel(cornerX + column, cornerZ + row), (byte) (Math.min(distances[row * SIZE + column], FAR) * BYTE_MAX / FAR));
            }
        }
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture(), data, BASE_LEVEL, FIRST_LAYER, ORIGIN, ORIGIN, SIZE, SIZE);
    }

    Optional<GpuTextureView> view() {
        return Optional.ofNullable(view);
    }

    void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        if (bytes != null) {
            MemoryUtil.memFree(bytes);
        }
        tracked = null;
        texture = null;
        view = null;
        bytes = null;
    }

    private void sweepForward() {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int index = row * SIZE + column;
                int best = distances[index];
                if (column > 0) {
                    best = Math.min(best, distances[index - 1] + STRAIGHT);
                }
                if (row > 0) {
                    best = Math.min(best, distances[index - SIZE] + STRAIGHT);
                    if (column > 0) {
                        best = Math.min(best, distances[index - SIZE - 1] + DIAGONAL);
                    }
                    if (column < MASK) {
                        best = Math.min(best, distances[index - SIZE + 1] + DIAGONAL);
                    }
                }
                distances[index] = best;
            }
        }
    }

    private void sweepBackward() {
        for (int row = MASK; row >= 0; row--) {
            for (int column = MASK; column >= 0; column--) {
                int index = row * SIZE + column;
                int best = distances[index];
                if (column < MASK) {
                    best = Math.min(best, distances[index + 1] + STRAIGHT);
                }
                if (row < MASK) {
                    best = Math.min(best, distances[index + SIZE] + STRAIGHT);
                    if (column < MASK) {
                        best = Math.min(best, distances[index + SIZE + 1] + DIAGONAL);
                    }
                    if (column > 0) {
                        best = Math.min(best, distances[index + SIZE - 1] + DIAGONAL);
                    }
                }
                distances[index] = best;
            }
        }
    }

    private static int texel(int blockX, int blockZ) {
        return (blockZ & MASK) * SIZE + (blockX & MASK);
    }

    private ByteBuffer bytes() {
        if (bytes == null) {
            bytes = MemoryUtil.memAlloc(SIZE * SIZE);
        }
        return bytes;
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
