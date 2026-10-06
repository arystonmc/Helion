package com.aryston.helion.render.temporal;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Optional;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class TemporalResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LABEL = "Helion Temporal Settings";
    private static final float CURRENT_FRAME_WEIGHT = 0.1F;
    private static final double TELEPORT_DISTANCE = 16.0;
    private static final float HISTORY_VALID = 1.0F;
    private static final float HISTORY_INVALID = 0.0F;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putVec2()
        .putFloat()
        .putFloat()
        .get();

    private final GpuResources resources;
    private final TemporalHistory history;
    private @Nullable UniformRing uniforms;
    private @Nullable Matrix4f levelProjection;
    private @Nullable PreviousView previous;
    private @Nullable TemporalPrograms programs;
    private int frameIndex;
    private boolean historyWritten;
    private boolean missingPipelinesReported;

    public TemporalResources(GpuResources resources) {
        this.resources = resources;
        this.history = new TemporalHistory(resources);
    }

    public void recordLevelProjection(Matrix4fc projection) {
        levelProjection = new Matrix4f(projection);
    }

    public TemporalFrame begin(TemporalSettings settings, HelionCamera camera, int width, int height) {
        Matrix4f projection = levelProjection;
        if (!settings.enabled() || projection == null || !compilePrograms()) {
            invalidate();
            return TemporalFrame.INACTIVE;
        }
        boolean resized = history.ensureSize(width, height);
        Vector2f jitter = JitterSequence.ndcOffset(frameIndex++, width, height);
        Matrix4f jitteredProjection = new Matrix4f(projection).translateLocal(jitter.x, jitter.y, 0.0F);
        Matrix4f jitteredViewProjection = new Matrix4f(jitteredProjection).mul(camera.viewRotation());
        PreviousView last = previous;
        boolean valid = historyWritten
            && !resized
            && last != null
            && last.position().distance(camera.position()) < TELEPORT_DISTANCE;
        Matrix4f reprojection = valid ? last.reprojection(camera.position(), jitter, jitteredViewProjection) : new Matrix4f();
        previous = new PreviousView(projection, camera.viewRotation(), camera.position());
        historyWritten = false;
        return new TemporalFrame(true, jitteredProjection, reprojection, valid);
    }

    public void invalidate() {
        historyWritten = false;
        previous = null;
    }

    TemporalPrograms programs() {
        if (programs == null) {
            throw new IllegalStateException("Temporal programs used before a temporal frame began");
        }
        return programs;
    }

    RenderTarget previousHistory() {
        return history.previous();
    }

    RenderTarget nextHistory() {
        return history.next();
    }

    GpuBuffer writeUniforms(TemporalFrame frame, int width, int height) {
        if (uniforms == null) {
            uniforms = new UniformRing(LABEL, UNIFORM_SIZE, resources);
        }
        return uniforms.write(builder -> builder
            .putMat4f(frame.reprojection())
            .putVec2(1.0F / width, 1.0F / height)
            .putFloat(CURRENT_FRAME_WEIGHT)
            .putFloat(frame.historyValid() ? HISTORY_VALID : HISTORY_INVALID));
    }

    void finishFrame() {
        history.swap();
        historyWritten = true;
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    public void close() {
        history.close();
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
        invalidate();
    }

    private boolean compilePrograms() {
        Optional<TemporalPrograms> compiled = TemporalPrograms.compile();
        programs = compiled.orElse(null);
        if (compiled.isEmpty() && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion temporal anti-aliasing is skipped because its shaders could not be compiled");
        }
        return compiled.isPresent();
    }
}
