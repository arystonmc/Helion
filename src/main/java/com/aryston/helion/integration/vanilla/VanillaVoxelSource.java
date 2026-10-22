package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.voxel.VoxelClass;
import com.aryston.helion.render.voxel.VoxelSource;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import java.nio.ByteBuffer;
import java.util.function.LongConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

final class VanillaVoxelSource implements VoxelSource {
    private static final boolean NEVER_LOAD = false;

    private final VoxelBlockClasses classes = new VoxelBlockClasses();
    private @Nullable ClientLevel level;
    private int generation;

    VanillaVoxelSource use(ClientLevel current, boolean enabled) {
        VoxelSectionChanges.collect(enabled);
        if (current != level) {
            level = current;
            classes.clear();
            generation++;
        }
        if (VoxelSectionChanges.takeOverflow()) {
            generation++;
        }
        return this;
    }

    @Override
    public int levelGeneration() {
        return generation;
    }

    @Override
    public void drainChangedSections(LongConsumer changed) {
        VoxelSectionChanges.drain(changed);
    }

    @Override
    public void readSection(int sectionX, int sectionY, int sectionZ, ByteBuffer brick) {
        LevelChunkSection section = section(sectionX, sectionY, sectionZ);
        if (section == null || section.hasOnlyAir()) {
            fill(brick, VoxelClass.EMPTY);
            return;
        }
        for (int y = 0; y < VoxelVolumeLayout.SECTION_SIZE; y++) {
            for (int z = 0; z < VoxelVolumeLayout.SECTION_SIZE; z++) {
                for (int x = 0; x < VoxelVolumeLayout.SECTION_SIZE; x++) {
                    brick.put(VoxelVolumeLayout.brickIndex(x, y, z), classes.classify(section.getBlockState(x, y, z)));
                }
            }
        }
    }

    private @Nullable LevelChunkSection section(int sectionX, int sectionY, int sectionZ) {
        ClientLevel current = level;
        if (current == null) {
            return null;
        }
        int index = current.getSectionIndexFromSectionY(sectionY);
        if (index < 0 || index >= current.getSectionsCount()) {
            return null;
        }
        LevelChunk chunk = current.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, NEVER_LOAD);
        return chunk == null ? null : chunk.getSection(index);
    }

    private static void fill(ByteBuffer brick, byte value) {
        for (int index = 0; index < VoxelVolumeLayout.BRICK_BYTES; index++) {
            brick.put(index, value);
        }
    }
}
