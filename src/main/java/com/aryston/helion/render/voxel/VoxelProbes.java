package com.aryston.helion.render.voxel;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class VoxelProbes {
    static final float SPACING = 4.0F;
    static final float HYSTERESIS = 0.9F;
    static final float MAX_DISTANCE = 24.0F;
    static final float MAX_BOUNCED_LIGHT = 1.0F;

    private static final String LABEL = "Helion Voxel Probes #";
    private static final String UNIFORM_LABEL = "Helion Voxel Probe Settings";
    private static final int CHANNELS = 4;
    private static final int SETS = 2;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putMat4f().putIVec4().putVec4().get();
    private static final int NO_HISTORY = 0;
    private static final int HISTORY = 1;
    private static final float TAU = (float) (2.0 * Math.PI);

    private final GpuResources resources;
    private final List<TextureTarget> targets = new ArrayList<>();
    private final Random random = new Random();
    private @Nullable TrackedResource tracked;
    private @Nullable UniformRing uniforms;
    private int current;
    private boolean hasHistory;
    private int previousMinX;
    private int previousMinY;
    private int previousMinZ;

    VoxelProbes(GpuResources resources) {
        this.resources = resources;
    }

    List<TextureTarget> currentSet() {
        return set(current);
    }

    List<TextureTarget> previousSet() {
        return set((current + 1) % SETS);
    }

    GpuBuffer writeUniforms(VoxelVolumeView view, int rays) {
        if (uniforms == null) {
            uniforms = new UniformRing(UNIFORM_LABEL, UNIFORM_SIZE, resources);
        }
        Vector3f axis = new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F);
        Matrix4f rotation = axis.lengthSquared() > 0.0F ? new Matrix4f().rotate(random.nextFloat() * TAU, axis.normalize()) : new Matrix4f();
        float spacing = SPACING;
        int shiftX = Math.round((view.minBlockX() - previousMinX) / spacing);
        int shiftY = Math.round((view.minBlockY() - previousMinY) / spacing);
        int shiftZ = Math.round((view.minBlockZ() - previousMinZ) / spacing);
        int history = hasHistory ? HISTORY : NO_HISTORY;
        return uniforms.write(builder -> builder
            .putMat4f(rotation)
            .putIVec4(shiftX, shiftY, shiftZ, history)
            .putVec4(HYSTERESIS, rays, MAX_DISTANCE, MAX_BOUNCED_LIGHT));
    }

    void finishFrame(VoxelVolumeView view) {
        previousMinX = view.minBlockX();
        previousMinY = view.minBlockY();
        previousMinZ = view.minBlockZ();
        hasHistory = true;
        current = (current + 1) % SETS;
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    void close() {
        if (tracked != null) {
            resources.release(tracked);
        }
        if (uniforms != null) {
            uniforms.close();
        }
        tracked = null;
        uniforms = null;
        targets.clear();
        hasHistory = false;
    }

    private List<TextureTarget> set(int index) {
        if (targets.isEmpty()) {
            List<TextureTarget> created = new ArrayList<>();
            for (int target = 0; target < CHANNELS * SETS; target++) {
                created.add(new TextureTarget(LABEL + target, VoxelLightPipelines.PROBE_WIDTH, VoxelLightPipelines.PROBE_HEIGHT, VoxelLightPipelines.PROBE_FORMAT, null));
            }
            targets.addAll(created);
            long bytes = (long) CHANNELS * SETS * VoxelLightPipelines.PROBE_WIDTH * VoxelLightPipelines.PROBE_HEIGHT * VoxelLightPipelines.PROBE_FORMAT.blockSize();
            tracked = resources.track(LABEL, bytes, () -> created.forEach(TextureTarget::destroyBuffers));
            hasHistory = false;
        }
        return targets.subList(index * CHANNELS, index * CHANNELS + CHANNELS);
    }
}
