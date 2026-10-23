package com.aryston.helion.integration.vanilla;

import com.aryston.helion.render.voxel.VoxelClass;
import com.aryston.helion.render.voxel.VoxelLightSink;
import com.aryston.helion.render.voxel.VoxelSource;
import com.aryston.helion.render.voxel.VoxelVolumeLayout;
import java.nio.ByteBuffer;
import java.util.function.LongConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

final class VanillaVoxelSource implements VoxelSource {
    private static final boolean NEVER_LOAD = false;
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final int NO_COLOR = 0;

    private final VoxelBlockClasses classes = new VoxelBlockClasses();
    private final VoxelBiomeTints biomeTints = new VoxelBiomeTints();
    private @Nullable ClientLevel level;
    private int generation;
    private int colorVersion = VoxelLightColors.version();

    VanillaVoxelSource use(ClientLevel current, boolean enabled) {
        VoxelSectionChanges.collect(enabled);
        if (current != level) {
            level = current;
            classes.clear();
            biomeTints.clear();
            generation++;
        }
        if (VoxelLightColors.version() != colorVersion) {
            colorVersion = VoxelLightColors.version();
            classes.clear();
            biomeTints.clear();
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
    public void readSection(int sectionX, int sectionY, int sectionZ, ByteBuffer brick, ByteBuffer colors, VoxelLightSink lights) {
        ClientLevel current = level;
        LevelChunkSection section = section(sectionX, sectionY, sectionZ);
        if (current == null || section == null || section.hasOnlyAir()) {
            fill(brick, VoxelClass.EMPTY);
            fillColors(colors);
            return;
        }
        int originX = sectionX * VoxelVolumeLayout.SECTION_SIZE;
        int originY = sectionY * VoxelVolumeLayout.SECTION_SIZE;
        int originZ = sectionZ * VoxelVolumeLayout.SECTION_SIZE;
        for (int y = 0; y < VoxelVolumeLayout.SECTION_SIZE; y++) {
            for (int z = 0; z < VoxelVolumeLayout.SECTION_SIZE; z++) {
                for (int x = 0; x < VoxelVolumeLayout.SECTION_SIZE; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    byte voxel = classes.classify(state);
                    int index = VoxelVolumeLayout.brickIndex(x, y, z);
                    brick.put(index, voxel);
                    int albedo = biomeTints.tinted(classes.albedo(state), state, current, originX + x, originY + y, originZ + z);
                    colors.putInt(index * VoxelVolumeLayout.COLOR_BYTES_PER_BLOCK, albedo);
                    int emission = VoxelBlockClasses.emission(voxel);
                    if (emission > 0) {
                        float[] color = classes.color(state);
                        lights.light(originX + x, originY + y, originZ + z, emission, color[RED], color[GREEN], color[BLUE]);
                    }
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

    private static void fillColors(ByteBuffer colors) {
        for (int index = 0; index < VoxelVolumeLayout.BRICK_BYTES; index++) {
            colors.putInt(index * VoxelVolumeLayout.COLOR_BYTES_PER_BLOCK, NO_COLOR);
        }
    }

    private static void fill(ByteBuffer brick, byte value) {
        for (int index = 0; index < VoxelVolumeLayout.BRICK_BYTES; index++) {
            brick.put(index, value);
        }
    }
}
