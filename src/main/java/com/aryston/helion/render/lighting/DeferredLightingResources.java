package com.aryston.helion.render.lighting;

import com.aryston.helion.render.atmosphere.SkyLight;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.shadow.ShadowLight;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Optional;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class DeferredLightingResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LABEL = "Helion Lighting Settings";
    private static final Vector3fc NO_DIRECTION = new Vector3f(0.0F, 0.0F, 0.0F);
    private static final float NO_SHADOW = 0.0F;
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
        .putVec3()
        .putVec3()
        .putFloat()
        .putFloat()
        .get();

    private final GpuResources resources;
    private @Nullable UniformRing uniforms;
    private boolean missingPipelinesReported;

    public DeferredLightingResources(GpuResources resources) {
        this.resources = resources;
    }

    GpuBuffer writeUniforms(
        LightEnvironment environment,
        DeferredLightingSettings settings,
        Optional<SkyLight> skyLight,
        Optional<ShadowLight> shadowLight
    ) {
        float sunShadowStrength = shadowLight.map(ShadowLight::sunStrength).orElse(NO_SHADOW);
        float moonShadowStrength = shadowLight.map(ShadowLight::moonStrength).orElse(NO_SHADOW);
        Vector3fc sunDirection = skyLight.map(SkyLight::sunDirection).orElse(NO_DIRECTION);
        Vector3fc moonDirection = skyLight.map(SkyLight::moonDirection).orElse(NO_DIRECTION);
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
            .putVec3(environment.nightVisionColor())
            .putVec3(sunDirection)
            .putVec3(moonDirection)
            .putFloat(sunShadowStrength)
            .putFloat(moonShadowStrength));
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
