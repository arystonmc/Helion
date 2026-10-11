package com.aryston.helion.render.motion;

import com.aryston.helion.render.post.MotionBlurSettings;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Optional;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class MotionBlurResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LABEL = "Helion Motion Blur Settings";
    private static final float MAX_BLUR_PIXELS = 48.0F;
    static final float REFERENCE_FRAME_SECONDS = 1.0F / 30.0F;
    static final float MIN_FRAME_SCALE = 1.0F;
    static final float MAX_FRAME_SCALE = 8.0F;
    private static final float MAX_FRAME_SECONDS = 0.25F;
    private static final float NANOS_PER_SECOND = 1.0e9F;
    private static final float FRAME_TIME_SMOOTHING = 0.1F;
    private static final long NO_FRAME = -1L;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putVec2()
        .putFloat()
        .putFloat()
        .get();

    private final GpuResources resources;
    private @Nullable UniformRing uniforms;
    private @Nullable GpuBuffer frameUniforms;
    private long lastFrameNanos = NO_FRAME;
    private float frameSeconds = REFERENCE_FRAME_SECONDS;
    private boolean missingPipelinesReported;

    public MotionBlurResources(GpuResources resources) {
        this.resources = resources;
    }

    Optional<MotionBlurPrograms> programs() {
        Optional<MotionBlurPrograms> compiled = MotionBlurPrograms.compile();
        if (compiled.isEmpty() && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion motion blur is skipped because its shaders could not be compiled");
        }
        return compiled;
    }

    GpuBuffer frameUniforms(Matrix4fc reprojection, MotionBlurSettings settings, int width, int height) {
        if (frameUniforms != null) {
            return frameUniforms;
        }
        if (uniforms == null) {
            uniforms = new UniformRing(LABEL, UNIFORM_SIZE, resources);
        }
        measureFrame(System.nanoTime());
        float scale = blurScale(settings.strength(), frameSeconds);
        frameUniforms = uniforms.write(builder -> builder
            .putMat4f(reprojection)
            .putVec2(1.0F / width, 1.0F / height)
            .putFloat(scale)
            .putFloat(MAX_BLUR_PIXELS));
        return frameUniforms;
    }

    static float blurScale(float strength, float frameSeconds) {
        return strength * Math.clamp(REFERENCE_FRAME_SECONDS / frameSeconds, MIN_FRAME_SCALE, MAX_FRAME_SCALE);
    }

    private void measureFrame(long now) {
        if (lastFrameNanos != NO_FRAME) {
            float seconds = Math.min((now - lastFrameNanos) / NANOS_PER_SECOND, MAX_FRAME_SECONDS);
            frameSeconds += (seconds - frameSeconds) * FRAME_TIME_SMOOTHING;
        }
        lastFrameNanos = now;
    }

    void finishFrame() {
        frameUniforms = null;
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    public void close() {
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
        frameUniforms = null;
    }
}
