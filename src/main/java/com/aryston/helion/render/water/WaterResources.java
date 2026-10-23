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
        .get();

    private final GpuResources resources;
    private @Nullable UniformRing surfaceUniforms;
    private @Nullable UniformRing reflectionUniforms;
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

    GpuBuffer reflectionUniforms(HelionCamera camera) {
        if (reflectionUniforms == null) {
            reflectionUniforms = new UniformRing(REFLECTION_LABEL, REFLECTION_SIZE, resources);
        }
        return reflectionUniforms.write(builder -> builder
            .putMat4f(camera.levelViewProjection().invert())
            .putFloat(REFLECTION_STRENGTH));
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
        surfaceUniforms = null;
        reflectionUniforms = null;
    }
}
