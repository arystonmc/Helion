package com.aryston.helion.render.lightshaft;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Optional;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class LightShaftResources {
    static final int RESOLUTION_DIVISOR = 4;
    static final int SAMPLES = 48;
    static final float DECAY = 0.975F;
    static final float STREAK_LENGTH = 0.85F;
    static final float GLOW_SHARPNESS = 40.0F;
    static final float SUN_STRENGTH = 0.14F;
    static final float MOON_STRENGTH = 1.6F;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SETTINGS_LABEL = "Helion Light Shaft Settings";
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .putVec4()
        .get();

    private final GpuResources resources;
    private @Nullable UniformRing uniforms;
    private boolean missingPipelinesReported;

    public LightShaftResources(GpuResources resources) {
        this.resources = resources;
    }

    Optional<LightShaftPrograms> programs() {
        Optional<LightShaftPrograms> compiled = LightShaftPrograms.compile();
        if (compiled.isEmpty() && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion light shafts are skipped because their shaders could not be compiled");
        }
        return compiled;
    }

    GpuBuffer writeUniforms(HelionCamera camera, LightShaftView view, int sceneWidth, int sceneHeight, int width, int height) {
        if (uniforms == null) {
            uniforms = new UniformRing(SETTINGS_LABEL, UNIFORM_SIZE, resources);
        }
        Matrix4f inverse = camera.levelViewProjection().invert();
        float strength = view.sunLit() ? SUN_STRENGTH : MOON_STRENGTH;
        return uniforms.write(builder -> builder
            .putMat4f(inverse)
            .putVec4(view.direction().x(), view.direction().y(), view.direction().z(), strength)
            .putVec4(view.screenX(), view.screenY(), view.visibility(), view.sunLit() ? 1.0F : 0.0F)
            .putVec4(width, height, 1.0F / width, 1.0F / height)
            .putVec4(SAMPLES, DECAY, STREAK_LENGTH, GLOW_SHARPNESS)
            .putVec4(sceneWidth, sceneHeight, RESOLUTION_DIVISOR, 0.0F));
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
}
