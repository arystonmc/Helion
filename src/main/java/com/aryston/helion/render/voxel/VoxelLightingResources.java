package com.aryston.helion.render.voxel;

import static com.aryston.helion.Helion.LOGGER;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Optional;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

public final class VoxelLightingResources {
    private static final String SLICE_LABEL = "Helion Voxel Slice";
    private static final int SLICE_UNIFORM_SIZE = new Std140SizeCalculator().putIVec4().putIVec4().putIVec4().get();
    private static final String LIGHT_LABEL = "Helion Voxel Light Settings";
    private static final int LIGHT_UNIFORM_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putIVec4().putIVec4().get();
    private static final String DYNAMIC_LABEL = "Helion Voxel Dynamic Lights";
    private static final int DYNAMIC_UNIFORM_SIZE = dynamicUniformSize();
    private static final float NO_VALUE = 0.0F;
    private static final int VECTORS_PER_ENTRY = 2;
    private static final int UNUSED = 0;

    private final GpuResources resources;
    private final VoxelVolume volume;
    private @Nullable UniformRing sliceUniforms;
    private @Nullable UniformRing lightUniforms;
    private @Nullable UniformRing dynamicUniforms;
    private VoxelDynamicScene dynamicScene = VoxelDynamicScene.EMPTY;
    private boolean active;
    private boolean missingPipelinesReported;
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

    public int lightCount() {
        return volume.lightCount();
    }

    public void observeBricks(VoxelBrickSink sink) {
        volume.observeBricks(sink);
    }

    public VoxelBuildStatistics statistics() {
        return volume.statistics();
    }

    void reportMissingPipelines() {
        if (!missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion voxel lighting is skipped because its shaders could not be compiled");
        }
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

    GpuBuffer writeLightUniforms(HelionCamera camera, VoxelVolumeView view, VoxelLightingQuality quality) {
        if (lightUniforms == null) {
            lightUniforms = new UniformRing(LIGHT_LABEL, LIGHT_UNIFORM_SIZE, resources);
        }
        Matrix4f inverse = camera.levelViewProjection().invert();
        Vector3dc position = camera.position();
        float relativeX = (float) (position.x() - view.minBlockX());
        float relativeY = (float) (position.y() - view.minBlockY());
        float relativeZ = (float) (position.z() - view.minBlockZ());
        return lightUniforms.write(builder -> builder
            .putMat4f(inverse)
            .putVec4(relativeX, relativeY, relativeZ, VoxelVolumeLayout.VOLUME_SIZE)
            .putIVec4(view.minBlockX(), view.minBlockY(), view.minBlockZ(), view.lightCount())
            .putIVec4(quality.tracedLights(), quality.resolutionDivisor(), UNUSED, UNUSED));
    }

    public void useDynamicScene(VoxelDynamicScene scene) {
        dynamicScene = scene;
    }

    GpuBuffer writeDynamicUniforms(VoxelVolumeView view) {
        if (dynamicUniforms == null) {
            dynamicUniforms = new UniformRing(DYNAMIC_LABEL, DYNAMIC_UNIFORM_SIZE, resources);
        }
        VoxelDynamicScene scene = dynamicScene;
        return dynamicUniforms.write(builder -> writeDynamic(builder, scene, view));
    }

    private static void writeDynamic(Std140Builder builder, VoxelDynamicScene scene, VoxelVolumeView view) {
        builder.putIVec4(scene.lights().size(), scene.occluders().size(), UNUSED, UNUSED);
        for (int index = 0; index < VoxelDynamicScene.MAX_LIGHTS; index++) {
            if (index < scene.lights().size()) {
                VoxelDynamicLight light = scene.lights().get(index);
                builder.putVec4(relative(light.x(), view.minBlockX()), relative(light.y(), view.minBlockY()), relative(light.z(), view.minBlockZ()), light.level());
                builder.putVec4(light.red(), light.green(), light.blue(), NO_VALUE);
            } else {
                builder.putVec4(NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
                builder.putVec4(NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
            }
        }
        for (int index = 0; index < VoxelDynamicScene.MAX_OCCLUDERS; index++) {
            if (index < scene.occluders().size()) {
                VoxelOccluder box = scene.occluders().get(index);
                builder.putVec4(relative(box.minX(), view.minBlockX()), relative(box.minY(), view.minBlockY()), relative(box.minZ(), view.minBlockZ()), NO_VALUE);
                builder.putVec4(relative(box.maxX(), view.minBlockX()), relative(box.maxY(), view.minBlockY()), relative(box.maxZ(), view.minBlockZ()), NO_VALUE);
            } else {
                builder.putVec4(NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
                builder.putVec4(NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE);
            }
        }
    }

    private static float relative(double coordinate, int volumeMin) {
        return (float) (coordinate - volumeMin);
    }

    private static int dynamicUniformSize() {
        Std140SizeCalculator calculator = new Std140SizeCalculator().putIVec4();
        int vectors = VECTORS_PER_ENTRY * (VoxelDynamicScene.MAX_LIGHTS + VoxelDynamicScene.MAX_OCCLUDERS);
        for (int index = 0; index < vectors; index++) {
            calculator.putVec4();
        }
        return calculator.get();
    }

    void finishLightFrame() {
        if (lightUniforms != null) {
            lightUniforms.rotate();
        }
        if (dynamicUniforms != null) {
            dynamicUniforms.rotate();
        }
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
        if (lightUniforms != null) {
            lightUniforms.close();
        }
        if (dynamicUniforms != null) {
            dynamicUniforms.close();
        }
        sliceUniforms = null;
        lightUniforms = null;
        dynamicUniforms = null;
        dynamicScene = VoxelDynamicScene.EMPTY;
        volume.close();
        active = false;
    }
}
