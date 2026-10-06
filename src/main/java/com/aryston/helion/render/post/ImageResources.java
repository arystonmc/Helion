package com.aryston.helion.render.post;

import com.aryston.helion.render.graph.FrameContext;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.aryston.helion.render.scene.SceneSnapshot;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ImageResources {
    static final int BLOOM_MIP_COUNT = 6;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LABEL = "Helion Image Settings";
    private static final float EXPOSURE_BASE = 2.0F;
    private static final float DARK_THRESHOLD_SCALE = 0.5F;
    private static final float DAYLIGHT_THRESHOLD_SCALE = 8.0F;
    private static final float THRESHOLD_KNEE_FRACTION = 0.5F;
    private static final int DITHER_ON = 1;
    private static final int DITHER_OFF = 0;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec4()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putInt()
        .putInt()
        .putFloat()
        .get();

    private final GpuResources resources;
    private @Nullable UniformRing uniforms;
    private boolean missingPipelinesReported;

    public ImageResources(GpuResources resources) {
        this.resources = resources;
    }

    Optional<ImagePrograms> programs(FrameContext frame) {
        if (frame.scene().colorFormat() != ImagePipelines.OUTPUT_FORMAT) {
            return Optional.empty();
        }
        Optional<ImagePrograms> compiled = ImagePrograms.compile();
        if (compiled.isEmpty()) {
            reportMissingPipelines();
        }
        return compiled;
    }

    GpuBuffer frameUniforms(FrameContext frame) {
        Optional<GpuBuffer> written = frame.post().imageUniforms();
        if (written.isPresent()) {
            return written.get();
        }
        GpuBuffer buffer = write(frame.settings().image(), frame.scene());
        frame.post().publishImageUniforms(buffer);
        return buffer;
    }

    void finishFrame() {
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    public void close() {
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
    }

    public static float bloomThreshold(BloomSettings bloom, float ambientLight) {
        float daylight = daylight(ambientLight);
        return bloom.threshold() * (DARK_THRESHOLD_SCALE + daylight * (DAYLIGHT_THRESHOLD_SCALE - DARK_THRESHOLD_SCALE));
    }

    private static float daylight(float ambientLight) {
        return ambientLight * ambientLight;
    }

    private GpuBuffer write(ImageSettings settings, SceneSnapshot scene) {
        if (uniforms == null) {
            uniforms = new UniformRing(LABEL, UNIFORM_SIZE, resources);
        }
        BloomSettings bloom = settings.bloom();
        SharpeningSettings sharpening = settings.sharpening();
        float threshold = bloomThreshold(bloom, scene.ambientLight());
        return uniforms.write(builder -> builder
            .putVec4(scene.fogColor())
            .putFloat((float) Math.pow(EXPOSURE_BASE, settings.exposure()))
            .putFloat(bloom.enabled() ? bloom.intensity() / BLOOM_MIP_COUNT : 0.0F)
            .putFloat(threshold)
            .putFloat(threshold * THRESHOLD_KNEE_FRACTION)
            .putFloat(daylight(scene.ambientLight()))
            .putInt(settings.toneMapper().shaderId())
            .putInt(settings.dither() ? DITHER_ON : DITHER_OFF)
            .putFloat(sharpening.enabled() ? sharpening.strength() : 0.0F));
    }

    private void reportMissingPipelines() {
        if (!missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion bloom and tone mapping are skipped because their shaders could not be compiled");
        }
    }
}
