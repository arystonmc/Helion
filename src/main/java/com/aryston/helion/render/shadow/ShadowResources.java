package com.aryston.helion.render.shadow;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ShadowResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SETTINGS_LABEL = "Helion Shadow Settings";
    private static final String PROJECTION_LABEL = "Helion Shadow Projection #";
    private static final String SHADOW_MAP_LABEL = "Helion Shadow Map #";
    private static final String ENTITY_MAP_LABEL = "Helion Entity Shadow Map #";
    private static final String ENTITY_PROJECTION_LABEL = "Helion Entity Shadow Projection #";
    private static final int SETTINGS_SIZE = settingsSize();
    private static final int PROJECTION_SIZE = new Std140SizeCalculator().putMat4f().get();

    private final GpuResources resources;
    private final List<UniformRing> projections = new ArrayList<>();
    private final List<TextureTarget> shadowMaps = new ArrayList<>();
    private final List<TrackedResource> trackedShadowMaps = new ArrayList<>();
    private final List<UniformRing> entityProjections = new ArrayList<>();
    private final List<TextureTarget> entityMaps = new ArrayList<>();
    private final List<TrackedResource> trackedEntityMaps = new ArrayList<>();
    private int entityMapResolution;
    private final ShadowCascadeCache cache = new ShadowCascadeCache();
    private @Nullable UniformRing settings;
    private int shadowMapResolution;
    private boolean missingPipelinesReported;

    public ShadowResources(GpuResources resources) {
        this.resources = resources;
    }

    List<RenderTarget> shadowMaps(int resolution) {
        if (shadowMaps.isEmpty() || shadowMapResolution != resolution) {
            releaseShadowMaps();
            for (int cascade = 0; cascade < ShadowCascades.COUNT; cascade++) {
                String label = SHADOW_MAP_LABEL + cascade;
                TextureTarget created = new TextureTarget(label, resolution, resolution, null, ShadowPipelines.SHADOW_MAP_FORMAT);
                trackedShadowMaps.add(resources.track(
                    label, (long) resolution * resolution * ShadowPipelines.SHADOW_MAP_FORMAT.blockSize(), created::destroyBuffers
                ));
                shadowMaps.add(created);
            }
            shadowMapResolution = resolution;
        }
        return List.copyOf(shadowMaps);
    }

    List<RenderTarget> entityMaps(int cascadeResolution) {
        int resolution = cascadeResolution / ShadowPipelines.ENTITY_RESOLUTION_DIVISOR;
        if (entityMaps.isEmpty() || entityMapResolution != resolution) {
            releaseEntityMaps();
            for (int cascade = 0; cascade < ShadowPipelines.ENTITY_CASCADES; cascade++) {
                String label = ENTITY_MAP_LABEL + cascade;
                TextureTarget created = new TextureTarget(
                    label, resolution, resolution, ShadowPipelines.ENTITY_COLOR_FORMAT, ShadowPipelines.SHADOW_MAP_FORMAT
                );
                long bytes = (long) resolution * resolution
                    * (ShadowPipelines.ENTITY_COLOR_FORMAT.blockSize() + ShadowPipelines.SHADOW_MAP_FORMAT.blockSize());
                trackedEntityMaps.add(resources.track(label, bytes, created::destroyBuffers));
                entityMaps.add(created);
            }
            entityMapResolution = resolution;
        }
        return List.copyOf(entityMaps);
    }

    GpuBuffer writeEntityProjection(int cascade, Matrix4fc projection) {
        while (entityProjections.size() <= cascade) {
            entityProjections.add(new UniformRing(ENTITY_PROJECTION_LABEL + entityProjections.size(), PROJECTION_SIZE, resources));
        }
        return entityProjections.get(cascade).write(builder -> builder.putMat4f(projection));
    }

    ShadowCascadeCache cache() {
        return cache;
    }

    GpuBuffer writeProjection(int cascade, Matrix4fc projection) {
        while (projections.size() <= cascade) {
            projections.add(new UniformRing(PROJECTION_LABEL + projections.size(), PROJECTION_SIZE, resources));
        }
        return projections.get(cascade).write(builder -> builder.putMat4f(projection));
    }

    GpuBuffer writeSettings(HelionCamera camera, ShadowLight light, float distance, int resolution) {
        if (settings == null) {
            settings = new UniformRing(SETTINGS_LABEL, SETTINGS_SIZE, resources);
        }
        Vector4f texelSizes = new Vector4f(cache.texelSize(0), cache.texelSize(1), cache.texelSize(2), cache.texelSize(3));
        return Objects.requireNonNull(settings).write(builder -> {
            builder.putMat4f(camera.levelViewProjection().invert());
            for (int cascade = 0; cascade < ShadowCascades.COUNT; cascade++) {
                builder.putMat4f(cache.maskMatrix(cascade, camera.position()));
            }
            builder.putVec4(texelSizes)
                .putVec3(light.direction())
                .putFloat(distance)
                .putFloat(resolution);
        });
    }

    void finishFrame() {
        projections.forEach(UniformRing::rotate);
        entityProjections.forEach(UniformRing::rotate);
        if (settings != null) {
            settings.rotate();
        }
    }

    void reportMissingPipelines() {
        if (!missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion shadows are skipped because the shadow mask shader could not be compiled");
        }
    }

    public void close() {
        projections.forEach(UniformRing::close);
        projections.clear();
        entityProjections.forEach(UniformRing::close);
        entityProjections.clear();
        releaseEntityMaps();
        if (settings != null) {
            settings.close();
        }
        settings = null;
        releaseShadowMaps();
    }

    private void releaseEntityMaps() {
        trackedEntityMaps.forEach(resources::release);
        trackedEntityMaps.clear();
        entityMaps.clear();
        entityMapResolution = 0;
    }

    private void releaseShadowMaps() {
        trackedShadowMaps.forEach(resources::release);
        trackedShadowMaps.clear();
        shadowMaps.clear();
        shadowMapResolution = 0;
        cache.clear();
    }

    private static int settingsSize() {
        Std140SizeCalculator calculator = new Std140SizeCalculator().putMat4f();
        for (int cascade = 0; cascade < ShadowCascades.COUNT; cascade++) {
            calculator.putMat4f();
        }
        return calculator.putVec4().putVec3().putFloat().putFloat().get();
    }
}
