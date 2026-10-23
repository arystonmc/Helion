package com.aryston.helion.render.rtx;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;

final class RtxSectionScene {
    private static final int BUILDS_PER_FRAME = 16;
    private static final int REQUESTS_PER_FRAME = 8;
    private static final int EVICTION_MARGIN = 2;
    private static final int RADIUS = RtxSectionInbox.RADIUS;
    private static final long NO_BYTES = 0L;

    private final RtxDevice device;
    private final GpuResources resources;
    private final RtxQuadIndices indices = new RtxQuadIndices();
    private final RtxSharedScratch scratch = new RtxSharedScratch();
    private final RtxTopLevel topLevel;
    private final RtxEntityScene entities;
    private final Long2ObjectOpenHashMap<RtxSectionBlas> built = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<RtxSectionUpdate> pending = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet known = new LongOpenHashSet();
    private final LongOpenHashSet requested = new LongOpenHashSet();
    private final List<RtxSectionBlas> building = new ArrayList<>();
    private final List<RtxSectionBlas> visible = new ArrayList<>();
    private @Nullable RtxSectionPos anchor;
    private long builtBytes;

    RtxSectionScene(RtxDevice device, GpuResources resources) {
        this.device = device;
        this.resources = resources;
        this.topLevel = RtxTopLevel.create(device, resources);
        this.entities = new RtxEntityScene(device, resources);
    }

    RtxScenePlan prepare(RtxSectionInbox inbox, RtxSectionPos center) {
        anchor = center;
        inbox.moveTo(center);
        drain(inbox, center);
        evict(center);
        building.clear();
        List<RtxSectionUpdate> due = new ArrayList<>(pending.values());
        due.sort(Comparator.comparingInt(update -> center.distance(update.sectionX(), update.sectionY(), update.sectionZ())));
        long scratchBytes = NO_BYTES;
        for (RtxSectionUpdate update : due.subList(0, Math.min(due.size(), BUILDS_PER_FRAME))) {
            long key = key(update.sectionX(), update.sectionY(), update.sectionZ());
            pending.remove(key);
            release(built.remove(key));
            RtxSectionBlas blas = RtxSectionBlas.create(device, resources, update, indices.address(device, resources, update.quads()));
            built.put(key, blas);
            builtBytes += blas.bytes();
            known.add(key);
            building.add(blas);
            scratchBytes += RtxAccelerationStructure.aligned(blas.scratchSize(), device.scratchAlignment());
        }
        long scratchBase = building.isEmpty() ? NO_BYTES : scratch.address(device, resources, scratchBytes);
        requestMissing(inbox, center);
        visible.clear();
        for (RtxSectionBlas blas : built.values()) {
            RtxSectionPos position = blas.position();
            if (center.within(position.sectionX(), position.sectionY(), position.sectionZ(), RADIUS)) {
                visible.add(blas);
            }
        }
        topLevel.write(visible, center);
        return new RtxScenePlan(List.copyOf(building), scratchBase, built.size(), pending.size(), visible.size(), builtBytes);
    }

    void recordBottomLevels(VkCommandBuffer commandBuffer, RtxScenePlan plan) {
        long offset = plan.scratchBase();
        for (RtxSectionBlas blas : plan.builds()) {
            blas.record(commandBuffer, resources, offset);
            offset += RtxAccelerationStructure.aligned(blas.scratchSize(), device.scratchAlignment());
        }
    }

    void recordEntities(VkCommandBuffer commandBuffer, List<RtxEntityDraws.RtxEntityDraw> draws, float translateX, float translateY, float translateZ) {
        long address = entities.record(commandBuffer, draws);
        if (address != RtxEntityScene.NO_ADDRESS) {
            topLevel.appendEntities(address, translateX, translateY, translateZ);
        }
    }

    void clearEntitySurfaces() {
        entities.clearSurfaces();
    }

    RtxEntitySurfaces entitySurfaces() {
        return entities.surfaces();
    }

    void recordTopLevel(VkCommandBuffer commandBuffer) {
        topLevel.record(commandBuffer);
    }

    RtxBuffer surfaceTable() {
        return topLevel.surfaceTable();
    }

    long topLevelHandle() {
        return topLevel.handle();
    }

    @Nullable RtxSectionPos anchor() {
        return anchor;
    }

    void close() {
        built.values().forEach(this::release);
        built.clear();
        pending.clear();
        known.clear();
        requested.clear();
        indices.close(resources);
        entities.close();
        scratch.close(resources);
        topLevel.release(resources);
        builtBytes = NO_BYTES;
    }

    private void drain(RtxSectionInbox inbox, RtxSectionPos center) {
        for (RtxSectionUpdate update = inbox.poll(); update != null; update = inbox.poll()) {
            long key = key(update.sectionX(), update.sectionY(), update.sectionZ());
            if (update.isRemoval()) {
                pending.remove(key);
                release(built.remove(key));
                known.remove(key);
                continue;
            }
            if (!center.within(update.sectionX(), update.sectionY(), update.sectionZ(), RADIUS + EVICTION_MARGIN)) {
                continue;
            }
            if (update.isEmpty()) {
                pending.remove(key);
                release(built.remove(key));
                known.add(key);
                continue;
            }
            pending.put(key, update);
        }
    }

    private void evict(RtxSectionPos center) {
        int limit = RADIUS + EVICTION_MARGIN;
        built.long2ObjectEntrySet().removeIf(entry -> {
            RtxSectionPos position = entry.getValue().position();
            boolean far = !center.within(position.sectionX(), position.sectionY(), position.sectionZ(), limit);
            if (far) {
                release(entry.getValue());
            }
            return far;
        });
        pending.long2ObjectEntrySet().removeIf(entry -> {
            RtxSectionUpdate update = entry.getValue();
            return !center.within(update.sectionX(), update.sectionY(), update.sectionZ(), limit);
        });
        known.removeIf((long key) -> !center.within(VoxelVolumeLayout.keyX(key), VoxelVolumeLayout.keyY(key), VoxelVolumeLayout.keyZ(key), limit));
        requested.removeIf((long key) -> !center.within(VoxelVolumeLayout.keyX(key), VoxelVolumeLayout.keyY(key), VoxelVolumeLayout.keyZ(key), limit));
    }

    private void requestMissing(RtxSectionInbox inbox, RtxSectionPos center) {
        int requests = 0;
        for (int distance = 0; distance <= RADIUS && requests < REQUESTS_PER_FRAME; distance++) {
            for (int y = -distance; y <= distance && requests < REQUESTS_PER_FRAME; y++) {
                for (int z = -distance; z <= distance && requests < REQUESTS_PER_FRAME; z++) {
                    for (int x = -distance; x <= distance && requests < REQUESTS_PER_FRAME; x++) {
                        if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) != distance) {
                            continue;
                        }
                        int sectionX = center.sectionX() + x;
                        int sectionY = center.sectionY() + y;
                        int sectionZ = center.sectionZ() + z;
                        long key = key(sectionX, sectionY, sectionZ);
                        if (known.contains(key) || pending.containsKey(key) || !requested.add(key)) {
                            continue;
                        }
                        inbox.requestRecompile(sectionX, sectionY, sectionZ);
                        requests++;
                    }
                }
            }
        }
    }

    private void release(@Nullable RtxSectionBlas blas) {
        if (blas != null) {
            builtBytes -= blas.bytes();
            blas.release(resources);
        }
    }

    private static long key(int sectionX, int sectionY, int sectionZ) {
        return VoxelVolumeLayout.sectionKey(sectionX, sectionY, sectionZ);
    }
}
