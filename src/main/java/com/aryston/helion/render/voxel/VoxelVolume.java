package com.aryston.helion.render.voxel;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.render.resource.GpuResources;
import com.mojang.renderpearl.api.GpuFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

final class VoxelVolume {
    static final GpuFormat FORMAT = GpuFormat.R8_UNORM;

    private static final long FRAME_BUDGET_NANOS = 1_000_000L;
    private static final int NO_GENERATION = Integer.MIN_VALUE;
    private static final int NO_VERSION = -1;
    private static final long NOT_CHANGED = -1L;
    private static final long GRID_REBUILD_FRAMES = 10L;
    private static final byte READY = (byte) 0xFF;
    private static final byte UNKNOWN = 0;
    private static final int READY_WIDTH = VoxelVolumeLayout.BRICKS_PER_ROW;
    private static final int READY_HEIGHT = VoxelVolumeLayout.SECTION_COUNT / VoxelVolumeLayout.BRICKS_PER_ROW;

    private final GpuResources resources;
    private final VoxelVolumeWindow window = new VoxelVolumeWindow();
    private final VoxelBuildStatistics statistics = new VoxelBuildStatistics();
    private final VoxelTexture atlas = new VoxelTexture("Helion Voxel Volume", FORMAT, VoxelVolumeLayout.ATLAS_WIDTH, VoxelVolumeLayout.ATLAS_HEIGHT);
    private final VoxelTexture coarse = new VoxelTexture("Helion Voxel Coarse Level", FORMAT, VoxelCoarseLevel.ATLAS_WIDTH, VoxelCoarseLevel.ATLAS_HEIGHT);
    private final VoxelTexture ready = new VoxelTexture("Helion Voxel Ready Slots", FORMAT, READY_WIDTH, READY_HEIGHT);
    private final VoxelLightTextures lightTextures = new VoxelLightTextures();
    private final List<List<VoxelLight>> slotLights = new ArrayList<>(Collections.nCopies(VoxelVolumeLayout.SECTION_COUNT, List.of()));
    private final List<VoxelLight> collected = new ArrayList<>();
    private @Nullable ByteBuffer brick;
    private @Nullable ByteBuffer coarseBrick;
    private @Nullable ByteBuffer readyTable;
    private int generation = NO_GENERATION;
    private int uploadedReadyVersion = NO_VERSION;
    private int cameraX;
    private int cameraY;
    private int cameraZ;
    private long frame;
    private long gridFrame;
    private boolean gridDirty;
    private final long[] changedAt = new long[VoxelVolumeLayout.SECTION_COUNT];

    VoxelVolume(GpuResources resources) {
        this.resources = resources;
        Arrays.fill(changedAt, NOT_CHANGED);
    }

    void update(int blockX, int blockY, int blockZ, VoxelSource source) {
        long start = System.nanoTime();
        ensureAllocated();
        cameraX = blockX;
        cameraY = blockY;
        cameraZ = blockZ;
        if (source.levelGeneration() != generation) {
            generation = source.levelGeneration();
            window.markAllChanged();
        }
        frame++;
        source.drainChangedSections(this::sectionChanged);
        boolean moved = window.recenter(VoxelVolumeLayout.sectionOf(blockX), VoxelVolumeLayout.sectionOf(blockY), VoxelVolumeLayout.sectionOf(blockZ));
        statistics.beginFrame(window.pendingCount(), start);
        BuildResult result = buildPending(source, start);
        uploadReadySlots();
        gridDirty |= moved || result.lightsChanged();
        if (gridDirty && (window.pendingCount() == 0 || frame - gridFrame >= GRID_REBUILD_FRAMES)) {
            rebuildLightGrid();
            gridDirty = false;
            gridFrame = frame;
        }
        statistics.endFrame(result.built(), window.pendingCount(), System.nanoTime() - start);
    }

    Optional<VoxelVolumeView> view() {
        if (!atlas.allocated()) {
            return Optional.empty();
        }
        return Optional.of(new VoxelVolumeView(
            atlas.view(), coarse.view(), ready.view(), lightTextures.lightView(), lightTextures.gridView(),
            window.minBlockX(), window.minBlockY(), window.minBlockZ(), lightTextures.lightCount()
        ));
    }

    VoxelBuildStatistics statistics() {
        return statistics;
    }

    int pendingSections() {
        return window.pendingCount();
    }

    int lightCount() {
        return lightTextures.lightCount();
    }

    void close() {
        atlas.close(resources);
        coarse.close(resources);
        ready.close(resources);
        lightTextures.close(resources);
        free(brick);
        free(coarseBrick);
        free(readyTable);
        brick = null;
        coarseBrick = null;
        readyTable = null;
        generation = NO_GENERATION;
        uploadedReadyVersion = NO_VERSION;
        gridDirty = false;
        Collections.fill(slotLights, List.of());
        Arrays.fill(changedAt, NOT_CHANGED);
        window.markAllChanged();
        statistics.reset();
    }

    private BuildResult buildPending(VoxelSource source, long start) {
        ByteBuffer data = Objects.requireNonNull(brick);
        ByteBuffer coarseData = Objects.requireNonNull(coarseBrick);
        int built = 0;
        boolean lightsChanged = false;
        for (long key = window.nextPending(); key != VoxelVolumeWindow.NO_SECTION; key = window.nextPending()) {
            int sectionX = VoxelVolumeLayout.keyX(key);
            int sectionY = VoxelVolumeLayout.keyY(key);
            int sectionZ = VoxelVolumeLayout.keyZ(key);
            int slot = VoxelVolumeLayout.slot(sectionX, sectionY, sectionZ);
            collected.clear();
            source.readSection(sectionX, sectionY, sectionZ, data, (x, y, z, level, red, green, blue) ->
                collected.add(new VoxelLight(x, y, z, level, red, green, blue)));
            VoxelCoarseLevel.build(data, coarseData);
            atlas.write(data, VoxelVolumeLayout.brickX(slot), VoxelVolumeLayout.brickY(slot), VoxelVolumeLayout.BRICK_SIZE, VoxelVolumeLayout.BRICK_SIZE);
            coarse.write(coarseData, VoxelCoarseLevel.brickX(slot), VoxelCoarseLevel.brickY(slot), VoxelCoarseLevel.BRICK_SIZE, VoxelCoarseLevel.BRICK_SIZE);
            lightsChanged |= !slotLights.get(slot).isEmpty() || !collected.isEmpty();
            slotLights.set(slot, List.copyOf(collected));
            if (changedAt[slot] != NOT_CHANGED) {
                statistics.recordChangeLatency(frame - changedAt[slot]);
                changedAt[slot] = NOT_CHANGED;
            }
            window.markReady(key);
            built++;
            if (System.nanoTime() - start > FRAME_BUDGET_NANOS) {
                break;
            }
        }
        return new BuildResult(built, lightsChanged);
    }

    private void sectionChanged(long key) {
        if (window.contains(key)) {
            int slot = VoxelVolumeLayout.slot(VoxelVolumeLayout.keyX(key), VoxelVolumeLayout.keyY(key), VoxelVolumeLayout.keyZ(key));
            if (changedAt[slot] == NOT_CHANGED) {
                changedAt[slot] = frame;
            }
        }
        window.markChanged(key);
    }

    private void uploadReadySlots() {
        if (window.version() == uploadedReadyVersion) {
            return;
        }
        ByteBuffer table = Objects.requireNonNull(readyTable);
        for (int slot = 0; slot < VoxelVolumeLayout.SECTION_COUNT; slot++) {
            table.put(slot, window.isReady(slot) ? READY : UNKNOWN);
        }
        ready.writeAll(table);
        uploadedReadyVersion = window.version();
    }

    private void rebuildLightGrid() {
        long start = System.nanoTime();
        List<VoxelLight> candidates = new ArrayList<>();
        for (int slot = 0; slot < VoxelVolumeLayout.SECTION_COUNT; slot++) {
            if (window.isReady(slot)) {
                candidates.addAll(slotLights.get(slot));
            }
        }
        VoxelLightGrid grid = VoxelLightGrid.build(candidates, window.minBlockX(), window.minBlockY(), window.minBlockZ(), cameraX, cameraY, cameraZ);
        lightTextures.upload(grid, window.minBlockX(), window.minBlockY(), window.minBlockZ());
        statistics.recordLightGrid(grid.lights().size(), System.nanoTime() - start);
    }

    private void ensureAllocated() {
        if (atlas.allocated()) {
            return;
        }
        atlas.allocate(resources);
        coarse.allocate(resources);
        ready.allocate(resources);
        lightTextures.ensureAllocated(resources);
        brick = MemoryUtil.memAlloc(VoxelVolumeLayout.BRICK_BYTES);
        coarseBrick = MemoryUtil.memAlloc(VoxelCoarseLevel.BRICK_BYTES);
        readyTable = MemoryUtil.memCalloc(VoxelVolumeLayout.SECTION_COUNT);
        window.markAllChanged();
        LOGGER.info("Helion voxel volume allocated: {} x {} {}", VoxelVolumeLayout.ATLAS_WIDTH, VoxelVolumeLayout.ATLAS_HEIGHT, FORMAT);
    }

    private static void free(@Nullable ByteBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
    }

    private record BuildResult(int built, boolean lightsChanged) {
    }
}
