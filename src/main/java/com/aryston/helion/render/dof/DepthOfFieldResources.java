package com.aryston.helion.render.dof;

import com.aryston.helion.render.post.DepthOfFieldSettings;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.TrackedResource;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class DepthOfFieldResources {
    static final float FOCUS_RATE = 4.0F;
    static final float MAX_BLUR_SCREEN_FRACTION = 0.015F;
    static final float APERTURE_SCALE = 0.6F;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LABEL = "Helion Depth Of Field Settings";
    private static final String FOCUS_LABEL = "Helion Depth Of Field Focus #";
    private static final int FOCUS_SIZE = 1;
    private static final int FOCUS_TARGETS = 2;
    private static final float NANOS_PER_SECOND = 1.0e9F;
    private static final float MAX_STEP_SECONDS = 0.25F;
    private static final float FULL_BLEND = 1.0F;
    private static final long NO_FRAME = -1L;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .putFloat()
        .putFloat()
        .get();

    private final GpuResources resources;
    private final List<TextureTarget> focusTargets = new ArrayList<>();
    private @Nullable TrackedResource trackedFocus;
    private @Nullable UniformRing uniforms;
    private @Nullable GpuBuffer frameUniforms;
    private int currentFocus;
    private long lastFrameNanos = NO_FRAME;
    private boolean missingPipelinesReported;

    public DepthOfFieldResources(GpuResources resources) {
        this.resources = resources;
    }

    Optional<DepthOfFieldPrograms> programs() {
        Optional<DepthOfFieldPrograms> compiled = DepthOfFieldPrograms.compile();
        if (compiled.isEmpty() && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion depth of field is skipped because its shaders could not be compiled");
        }
        return compiled;
    }

    RenderTarget previousFocus() {
        return focusTargets().get(currentFocus);
    }

    RenderTarget nextFocus() {
        return focusTargets().get((currentFocus + 1) % FOCUS_TARGETS);
    }

    GpuBuffer frameUniforms(DepthOfFieldSettings settings, int width, int height) {
        if (frameUniforms != null) {
            return frameUniforms;
        }
        if (uniforms == null) {
            uniforms = new UniformRing(LABEL, UNIFORM_SIZE, resources);
        }
        float blend = focusBlend(System.nanoTime());
        frameUniforms = uniforms.write(builder -> builder
            .putVec2(1.0F / width, 1.0F / height)
            .putFloat(height * MAX_BLUR_SCREEN_FRACTION * settings.strength())
            .putFloat(blend)
            .putFloat(APERTURE_SCALE * settings.strength()));
        return frameUniforms;
    }

    void finishFrame() {
        frameUniforms = null;
        currentFocus = (currentFocus + 1) % FOCUS_TARGETS;
        if (uniforms != null) {
            uniforms.rotate();
        }
    }

    public void reset() {
        lastFrameNanos = NO_FRAME;
    }

    public void close() {
        if (trackedFocus != null) {
            resources.release(trackedFocus);
        }
        trackedFocus = null;
        focusTargets.clear();
        if (uniforms != null) {
            uniforms.close();
        }
        uniforms = null;
        frameUniforms = null;
        reset();
    }

    static float focusBlend(float seconds) {
        return 1.0F - (float) Math.exp(-FOCUS_RATE * seconds);
    }

    private float focusBlend(long now) {
        long last = lastFrameNanos;
        lastFrameNanos = now;
        if (last == NO_FRAME) {
            return FULL_BLEND;
        }
        return focusBlend(Math.min((now - last) / NANOS_PER_SECOND, MAX_STEP_SECONDS));
    }

    private List<TextureTarget> focusTargets() {
        if (focusTargets.isEmpty()) {
            List<TextureTarget> created = new ArrayList<>();
            for (int index = 0; index < FOCUS_TARGETS; index++) {
                created.add(new TextureTarget(FOCUS_LABEL + index, FOCUS_SIZE, FOCUS_SIZE, DepthOfFieldPipelines.FOCUS_FORMAT, null));
            }
            focusTargets.addAll(created);
            trackedFocus = resources.track(
                FOCUS_LABEL, (long) FOCUS_TARGETS * DepthOfFieldPipelines.FOCUS_FORMAT.blockSize(), () -> created.forEach(TextureTarget::destroyBuffers)
            );
        }
        return focusTargets;
    }
}
