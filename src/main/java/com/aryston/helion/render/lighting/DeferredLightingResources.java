package com.aryston.helion.render.lighting;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class DeferredLightingResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LABEL = "Helion Lighting Settings";
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putVec3()
        .putVec3()
        .putVec3()
        .putVec3()
        .get();

    private final GpuResources resources;
    private @Nullable UniformRing uniforms;
    private boolean missingPipelinesReported;

    public DeferredLightingResources(GpuResources resources) {
        this.resources = resources;
    }

    GpuBuffer writeUniforms(LightEnvironment environment, DeferredLightingSettings settings) {
        if (uniforms == null) {
            uniforms = new UniformRing(LABEL, UNIFORM_SIZE, resources);
        }
        return uniforms.write(builder -> builder
            .putFloat(environment.skyFactor())
            .putFloat(environment.blockFactor())
            .putFloat(environment.nightVisionFactor())
            .putFloat(environment.darknessScale())
            .putFloat(environment.bossOverlayDarkening())
            .putFloat(environment.brightness())
            .putFloat(settings.blockLightIntensity())
            .putFloat(settings.skyLightIntensity())
            .putVec3(environment.blockLightTint())
            .putVec3(environment.skyLightColor())
            .putVec3(environment.ambientColor())
            .putVec3(environment.nightVisionColor()));
    }

    void finishFrame() {
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    void reportMissingPipelines() {
        if (!missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion deferred lighting is skipped because its shaders could not be compiled");
        }
    }

    public void close() {
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
    }
}
