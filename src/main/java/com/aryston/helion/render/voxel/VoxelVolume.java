package com.aryston.helion.render.voxel;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

final class VoxelVolume {
    static final GpuFormat FORMAT = GpuFormat.R8_UNORM;

    private static final String LABEL = "Helion Voxel Volume";
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
    private static final int SINGLE_LAYER = 1;
    private static final int SINGLE_MIP = 1;
    private static final int BASE_LEVEL = 0;
    private static final int FIRST_LAYER = 0;
    private static final long FRAME_BUDGET_NANOS = 1_000_000L;
    private static final int NO_GENERATION = Integer.MIN_VALUE;

    private final GpuResources resources;
    private final VoxelVolumeWindow window = new VoxelVolumeWindow();
    private final VoxelBuildStatistics statistics = new VoxelBuildStatistics();
    private @Nullable GpuTexture texture;
    private @Nullable GpuTextureView view;
    private @Nullable TrackedResource tracked;
    private @Nullable ByteBuffer brick;
    private int generation = NO_GENERATION;

    VoxelVolume(GpuResources resources) {
        this.resources = resources;
    }

    void update(int cameraX, int cameraY, int cameraZ, VoxelSource source) {
        long start = System.nanoTime();
        ensureAllocated();
        if (source.levelGeneration() != generation) {
            generation = source.levelGeneration();
            window.markAllChanged();
        }
        source.drainChangedSections(window::markChanged);
        window.recenter(VoxelVolumeLayout.sectionOf(cameraX), VoxelVolumeLayout.sectionOf(cameraY), VoxelVolumeLayout.sectionOf(cameraZ));
        statistics.beginFrame(window.pendingCount(), start);
        int built = buildPending(source, start);
        statistics.endFrame(built, window.pendingCount(), System.nanoTime() - start);
    }

    Optional<VoxelVolumeView> view() {
        if (view == null) {
            return Optional.empty();
        }
        return Optional.of(new VoxelVolumeView(view, window.minBlockX(), window.minBlockY(), window.minBlockZ()));
    }

    VoxelBuildStatistics statistics() {
        return statistics;
    }

    int pendingSections() {
        return window.pendingCount();
    }

    void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        if (brick != null) {
            MemoryUtil.memFree(brick);
        }
        tracked = null;
        texture = null;
        view = null;
        brick = null;
        generation = NO_GENERATION;
        window.markAllChanged();
        statistics.reset();
    }

    private int buildPending(VoxelSource source, long start) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuTexture target = Objects.requireNonNull(texture);
        ByteBuffer data = Objects.requireNonNull(brick);
        int built = 0;
        for (long key = window.nextPending(); key != VoxelVolumeWindow.NO_SECTION; key = window.nextPending()) {
            data.clear();
            source.readSection(VoxelVolumeLayout.keyX(key), VoxelVolumeLayout.keyY(key), VoxelVolumeLayout.keyZ(key), data);
            int slot = VoxelVolumeLayout.slot(VoxelVolumeLayout.keyX(key), VoxelVolumeLayout.keyY(key), VoxelVolumeLayout.keyZ(key));
            encoder.writeToTexture(target, data, BASE_LEVEL, FIRST_LAYER, VoxelVolumeLayout.brickX(slot), VoxelVolumeLayout.brickY(slot),
                VoxelVolumeLayout.BRICK_SIZE, VoxelVolumeLayout.BRICK_SIZE);
            window.markReady(key);
            built++;
            if (System.nanoTime() - start > FRAME_BUDGET_NANOS) {
                break;
            }
        }
        return built;
    }

    private void ensureAllocated() {
        if (texture != null) {
            return;
        }
        GpuTexture created = RenderSystem.getDevice()
            .createTexture(LABEL, USAGE, FORMAT, VoxelVolumeLayout.ATLAS_WIDTH, VoxelVolumeLayout.ATLAS_HEIGHT, SINGLE_LAYER, SINGLE_MIP);
        GpuTextureView createdView = RenderSystem.getDevice().createTextureView(created);
        texture = created;
        view = createdView;
        brick = MemoryUtil.memAlloc(VoxelVolumeLayout.BRICK_BYTES);
        tracked = resources.track(LABEL, VoxelVolumeLayout.ATLAS_BYTES, () -> {
            createdView.close();
            created.close();
        });
        window.markAllChanged();
        LOGGER.info("Helion voxel volume allocated: {} x {} {}", VoxelVolumeLayout.ATLAS_WIDTH, VoxelVolumeLayout.ATLAS_HEIGHT, FORMAT);
    }
}
