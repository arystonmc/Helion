package com.aryston.helion.render.lighting;

import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class AmbientOcclusionResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LABEL = "Helion Ambient Occlusion Settings";
    private static final float FALLOFF_RANGE = 0.615F;
    private static final float SAMPLE_DISTRIBUTION_POWER = 2.0F;
    private static final float THIN_OCCLUDER_COMPENSATION = 0.3F;
    private static final float FINAL_VALUE_POWER = 1.6F;
    private static final float DENOISE_BLUR_BETA = 1.2F;
    private static final float MAX_SCREEN_RADIUS_FRACTION = 0.2F;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putInt()
        .putInt()
        .putInt()
        .get();

    private final GpuResources resources;
    private @Nullable UniformRing uniforms;
    private boolean missingPipelinesReported;

    public AmbientOcclusionResources(GpuResources resources) {
        this.resources = resources;
    }

    GpuBuffer writeUniforms(AmbientOcclusionSettings settings, int width, int height) {
        if (uniforms == null) {
            uniforms = new UniformRing(LABEL, UNIFORM_SIZE, resources);
        }
        return uniforms.write(builder -> builder
            .putVec2(1.0F / width, 1.0F / height)
            .putFloat(settings.radius())
            .putFloat(FALLOFF_RANGE)
            .putFloat(SAMPLE_DISTRIBUTION_POWER)
            .putFloat(THIN_OCCLUDER_COMPENSATION)
            .putFloat(FINAL_VALUE_POWER)
            .putFloat(DENOISE_BLUR_BETA)
            .putFloat(settings.strength())
            .putFloat(height * MAX_SCREEN_RADIUS_FRACTION)
            .putInt(settings.quality().slices())
            .putInt(settings.quality().stepsPerSlice())
            .putInt(settings.algorithm().shaderId()));
    }

    void finishFrame() {
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    void reportMissingPipelines() {
        if (!missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion ambient occlusion is skipped because its shaders could not be compiled");
        }
    }

    public void close() {
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
    }
}
