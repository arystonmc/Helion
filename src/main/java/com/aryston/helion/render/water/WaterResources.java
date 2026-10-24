package com.aryston.helion.render.water;

import com.aryston.helion.render.camera.HelionCamera;
import com.aryston.helion.render.geometry.TerrainTextures;
import com.aryston.helion.render.resource.GpuResources;
import com.aryston.helion.render.resource.UniformRing;
import com.aryston.helion.render.shader.HelionPipelines;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import java.util.List;
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
        .putVec4()
        .putFloat()
        .get();
    private static final int REFLECTION_SIZE = new Std140SizeCalculator()
        .putMat4f()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putVec4()
        .get();
    private static final String UNDERWATER_LABEL = "Helion Underwater Settings";
    private static final int UNDERWATER_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().get();
    private static final String HEAT_LABEL = "Helion Heat Shimmer Settings";
    private static final int HEAT_SIZE = new Std140SizeCalculator().putVec4().get();
    private static final String SCREEN_LABEL = "Helion Screen Reflection Settings";
    private static final int SCREEN_SIZE = screenSize();
    private static final double NOISE_PERIOD = 1024.0;
    private static final double NANOS_PER_SECOND = 1.0e9;
    private static final float AGE_SIGN_OFFSET = 1.0e-3F;
    private static final double SECONDS_PERIOD = 3600.0;
    private static final float PRESENT = 1.0F;
    private static final float ABSENT = 0.0F;

    private final GpuResources resources;
    private final RainWetness rainWetness = new RainWetness();
    private final Footprints footprints = new Footprints();
    private final WaterWaves waves;
    private @Nullable UniformRing surfaceUniforms;
    private @Nullable UniformRing reflectionUniforms;
    private @Nullable UniformRing screenUniforms;
    private @Nullable UniformRing underwaterUniforms;
    private @Nullable UniformRing heatUniforms;
    private boolean missingScreenPipelineReported;
    private boolean missingPipelinesReported;

    public WaterResources(GpuResources resources) {
        this.resources = resources;
        this.waves = new WaterWaves(resources);
    }

    Optional<CompiledRenderPipeline> reflectionProgram(boolean physicalSky) {
        Optional<CompiledRenderPipeline> compiled = HelionPipelines.compiled(physicalSky ? WaterPipelines.REFLECTION_PHYSICAL_SKY : WaterPipelines.REFLECTION);
        if (compiled.isEmpty() && !missingPipelinesReported) {
            missingPipelinesReported = true;
            LOGGER.warn("Helion water reflections are skipped because their shaders could not be compiled");
        }
        return compiled;
    }

    GpuBuffer surfaceUniforms(TerrainTextures textures, Wetness wetness, boolean dynamicWaves) {
        if (surfaceUniforms == null) {
            surfaceUniforms = new UniformRing(SURFACE_LABEL, SURFACE_SIZE, resources);
        }
        return surfaceUniforms.write(builder -> builder
            .putVec4(textures.waterStill())
            .putVec4(textures.waterFlow())
            .putVec4(wetness.rain(), wetness.seconds(), dynamicWaves ? PRESENT : ABSENT, ABSENT)
            .putFloat(WAVE_STRENGTH));
    }

    WaterWaves waves() {
        return waves;
    }

    Footprints footprints() {
        return footprints;
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

    GpuBuffer reflectionUniforms(HelionCamera camera, boolean traced, boolean screen, boolean realistic, float flash) {
        if (reflectionUniforms == null) {
            reflectionUniforms = new UniformRing(REFLECTION_LABEL, REFLECTION_SIZE, resources);
        }
        return reflectionUniforms.write(builder -> builder
            .putMat4f(camera.levelViewProjection().invert())
            .putFloat(REFLECTION_STRENGTH)
            .putFloat(traced ? PRESENT : ABSENT)
            .putFloat(screen ? PRESENT : ABSENT)
            .putFloat(realistic ? PRESENT : ABSENT)
            .putFloat((float) ((System.nanoTime() / NANOS_PER_SECOND) % SECONDS_PERIOD))
            .putFloat(flash)
            .putVec4((float) (camera.position().x() % NOISE_PERIOD), (float) (camera.position().y() % NOISE_PERIOD), (float) (camera.position().z() % NOISE_PERIOD), ABSENT));
    }

    Optional<CompiledRenderPipeline> polishedProgram() {
        return HelionPipelines.compiled(WaterPipelines.POLISHED_COMPOSITE);
    }

    GpuBuffer screenUniforms(HelionCamera camera, boolean geometry, boolean water, boolean polished, boolean waves, Wetness wetness, List<Footprint> prints, double seconds, boolean traced) {
        if (screenUniforms == null) {
            screenUniforms = new UniformRing(SCREEN_LABEL, SCREEN_SIZE, resources);
        }
        Vector3dc position = camera.position();
        int count = Math.min(prints.size(), Footprints.MAX_PRINTS);
        return screenUniforms.write(builder -> {
            builder
                .putMat4f(camera.levelViewProjection())
                .putMat4f(camera.levelViewProjection().invert())
                .putVec4(geometry ? PRESENT : ABSENT, water ? PRESENT : ABSENT, polished ? PRESENT : ABSENT, waves ? PRESENT : ABSENT)
                .putVec4(wetness.surfaces(), wetness.puddles(), wetness.rain(), wetness.seconds())
                .putVec4((float) (position.x() % NOISE_PERIOD), (float) (position.y() % NOISE_PERIOD), (float) (position.z() % NOISE_PERIOD), wetness.flash())
                .putVec4(count, traced ? PRESENT : ABSENT, ABSENT, ABSENT);
            for (int index = 0; index < Footprints.MAX_PRINTS; index++) {
                putFootprint(builder, index < count ? prints.get(prints.size() - count + index) : null, seconds);
            }
        });
    }

    private static void putFootprint(Std140Builder builder, @Nullable Footprint print, double seconds) {
        if (print == null) {
            builder.putVec4(ABSENT, ABSENT, ABSENT, ABSENT);
            return;
        }
        float age = (float) ((seconds - print.seconds()) / Footprints.LIFETIME_SECONDS);
        builder.putVec4(
            (float) (print.x() % NOISE_PERIOD), (float) (print.z() % NOISE_PERIOD), (float) Math.toRadians(print.yaw()), print.left() ? age : -age - AGE_SIGN_OFFSET
        );
    }

    private static int screenSize() {
        Std140SizeCalculator calculator = new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().putVec4().putVec4();
        for (int index = 0; index < Footprints.MAX_PRINTS; index++) {
            calculator.putVec4();
        }
        return calculator.get();
    }

    GpuBuffer underwaterUniforms(HelionCamera camera, float daylight, float skyLight) {
        if (underwaterUniforms == null) {
            underwaterUniforms = new UniformRing(UNDERWATER_LABEL, UNDERWATER_SIZE, resources);
        }
        Vector3dc position = camera.position();
        return underwaterUniforms.write(builder -> builder
            .putMat4f(camera.levelViewProjection().invert())
            .putVec4((float) (position.x() % NOISE_PERIOD), (float) (position.y() % NOISE_PERIOD), (float) (position.z() % NOISE_PERIOD), daylight)
            .putVec4((float) ((System.nanoTime() / NANOS_PER_SECOND) % SECONDS_PERIOD), skyLight, ABSENT, ABSENT));
    }

    GpuBuffer heatUniforms() {
        if (heatUniforms == null) {
            heatUniforms = new UniformRing(HEAT_LABEL, HEAT_SIZE, resources);
        }
        return heatUniforms.write(builder -> builder.putVec4((float) ((System.nanoTime() / NANOS_PER_SECOND) % SECONDS_PERIOD), ABSENT, ABSENT, ABSENT));
    }

    void finishHeat() {
        if (heatUniforms != null) {
            heatUniforms.rotate();
        }
    }

    void finishUnderwater() {
        if (underwaterUniforms != null) {
            underwaterUniforms.rotate();
        }
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
        waves.close();
        if (surfaceUniforms != null) {
            surfaceUniforms.close();
        }
        if (reflectionUniforms != null) {
            reflectionUniforms.close();
        }
        if (screenUniforms != null) {
            screenUniforms.close();
        }
        if (underwaterUniforms != null) {
            underwaterUniforms.close();
        }
        if (heatUniforms != null) {
            heatUniforms.close();
        }
        heatUniforms = null;
        underwaterUniforms = null;
        surfaceUniforms = null;
        reflectionUniforms = null;
        screenUniforms = null;
    }
}
