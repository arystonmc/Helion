package com.aryston.helion.render.voxel;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class VoxelLightingResources {
    private static final String SLICE_LABEL = "Helion Voxel Slice";
    private static final int SLICE_UNIFORM_SIZE = new Std140SizeCalculator().putIVec4().putIVec4().putIVec4().get();
    private static final int UNUSED = 0;

    private final GpuResources resources;
    private final VoxelVolume volume;
    private @Nullable UniformRing sliceUniforms;
    private boolean active;
    private int cameraX;
    private int cameraY;
    private int cameraZ;

    public VoxelLightingResources(GpuResources resources) {
        this.resources = resources;
        this.volume = new VoxelVolume(resources);
    }

    public void update(VoxelLightingSettings settings, int blockX, int blockY, int blockZ, VoxelSource source) {
        if (!settings.enabled()) {
            if (active) {
                close();
            }
            return;
        }
        active = true;
        cameraX = blockX;
        cameraY = blockY;
        cameraZ = blockZ;
        volume.update(blockX, blockY, blockZ, source);
    }

    public Optional<VoxelVolumeView> volume() {
        return active ? volume.view() : Optional.empty();
    }

    public boolean active() {
        return active;
    }

    public int pendingSections() {
        return volume.pendingSections();
    }

    public VoxelBuildStatistics statistics() {
        return volume.statistics();
    }

    GpuBuffer writeSliceUniforms(VoxelVolumeView view, int outputWidth, int outputHeight) {
        if (sliceUniforms == null) {
            sliceUniforms = new UniformRing(SLICE_LABEL, SLICE_UNIFORM_SIZE, resources);
        }
        return sliceUniforms.write(builder -> builder
            .putIVec4(cameraX, cameraY, cameraZ, UNUSED)
            .putIVec4(view.minBlockX(), view.minBlockY(), view.minBlockZ(), VoxelVolumeLayout.VOLUME_SIZE)
            .putIVec4(outputWidth, outputHeight, UNUSED, UNUSED));
    }

    void finishFrame() {
        if (sliceUniforms != null) {
            sliceUniforms.rotate();
        }
    }

    public void close() {
        if (sliceUniforms != null) {
            sliceUniforms.close();
        }
        sliceUniforms = null;
        volume.close();
        active = false;
    }
}
