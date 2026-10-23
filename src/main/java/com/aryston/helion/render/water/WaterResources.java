package com.aryston.helion.render.water;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.TerrainTextures;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.Optional;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class WaterResources {
    static final float WAVE_STRENGTH = 1.0F;
    static final float REFLECTION_STRENGTH = 1.0F;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SURFACE_LABEL = "Helion Water Surface Settings";
    private static final String REFLECTION_LABEL = "Helion Water Reflection Settings";
    private static final int SURFACE_SIZE = new Std140SizeCalculator()
        .putVec4()
        .putVec4()
        .putFloat()
        .get();
    private static final int REFLECTION_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putFloat()
        .putFloat()
        .putFloat()
        .get();
    private static final String SCREEN_LABEL = "Helion Screen Reflection Settings";
    private static final int SCREEN_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putMat4f()
        .putVec4()
        .putVec4()
        .putVec4()
        .get();
    private static final double NOISE_PERIOD = 1024.0;
    private static final float PRESENT = 1.0F;
    private static final float ABSENT = 0.0F;

    private final GpuResources resources;
    private final RainWetness rainWetness = new RainWetness();
    private @Nullable UniformRing surfaceUniforms;
    private @Nullable UniformRing reflectionUniforms;
    private @Nullable UniformRing screenUniforms;
    private boolean missingScreenPipelineReported;
    private boolean missingPipelinesReported;

    public WaterResources(GpuResources resources) {
        this.resources = resources;
    }

    Optional<CompiledRenderPipeline> reflectionProgram(boolean physicalSky) {
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(physicalSky ? WaterPipelines.REFLECTION_PHYSICAL_SKY : WaterPipelines.REFLECTION);
        if (compiled.isEmpty() && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion water reflections are skipped because their shaders could not be compiled");
        }
        return compiled;
    }

    GpuBuffer surfaceUniforms(TerrainTextures textures) {
        if (surfaceUniforms == null) {
            surfaceUniforms = new UniformRing(SURFACE_LABEL, SURFACE_SIZE, resources);
        }
        return surfaceUniforms.write(builder -> builder
            .putVec4(textures.waterStill())
            .putVec4(textures.waterFlow())
            .putFloat(WAVE_STRENGTH));
    }

    public RainWetness rainWetness() {
        return rainWetness;
    }

    Optional<CompiledRenderPipeline> screenReflectionProgram(boolean physicalSky) {
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(physicalSky ? WaterPipelines.SCREEN_REFLECTION_PHYSICAL_SKY : WaterPipelines.SCREEN_REFLECTION);
        if (compiled.isEmpty() && !missingScreenPipelineReported) {
            missingScreenPipelineReported = true;
            LOGGER.warn("Helion screen space reflections are skipped because their shader could not be compiled");
        }
        return compiled;
    }

    GpuBuffer reflectionUniforms(HelionCamera camera, boolean traced, boolean screen) {
        if (reflectionUniforms == null) {
            reflectionUniforms = new UniformRing(REFLECTION_LABEL, REFLECTION_SIZE, resources);
        }
        return reflectionUniforms.write(builder -> builder
            .putMat4f(camera.levelViewProjection().invert())
            .putFloat(REFLECTION_STRENGTH)
            .putFloat(traced ? PRESENT : ABSENT)
            .putFloat(screen ? PRESENT : ABSENT));
    }

    Optional<CompiledRenderPipeline> polishedProgram() {
        return HelionPipelines.compiled(WaterPipelines.POLISHED_COMPOSITE);
    }

    GpuBuffer screenUniforms(HelionCamera camera, boolean geometry, boolean water, boolean polished, Wetness wetness) {
        if (screenUniforms == null) {
            screenUniforms = new UniformRing(SCREEN_LABEL, SCREEN_SIZE, resources);
        }
        Vector3dc position = camera.position();
        return screenUniforms.write(builder -> builder
            .putMat4f(camera.levelViewProjection())
            .putMat4f(camera.levelViewProjection().invert())
            .putVec4(geometry ? PRESENT : ABSENT, water ? PRESENT : ABSENT, polished ? PRESENT : ABSENT, ABSENT)
            .putVec4(wetness.surfaces(), wetness.puddles(), wetness.rain(), wetness.seconds())
            .putVec4((float) (position.x() % NOISE_PERIOD), (float) (position.y() % NOISE_PERIOD), (float) (position.z() % NOISE_PERIOD), ABSENT));
    }

    void finishScreen() {
        if (screenUniforms != null) {
            screenUniforms.rotate();
        }
    }

    void finishSurface() {
        if (surfaceUniforms != null) {
            surfaceUniforms.rotate();
        }
    }

    void finishReflection() {
        if (reflectionUniforms != null) {
            reflectionUniforms.rotate();
        }
    }

    public void close() {
        if (surfaceUniforms != null) {
            surfaceUniforms.close();
        }
        if (reflectionUniforms != null) {
            reflectionUniforms.close();
        }
        if (screenUniforms != null) {
            screenUniforms.close();
        }
        surfaceUniforms = null;
        reflectionUniforms = null;
        screenUniforms = null;
    }
}
