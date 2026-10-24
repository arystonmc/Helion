package com.aryston.helion.render.water;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class WaterResults {
    private @Nullable ResourceHandle<RenderTarget> surface;
    private @Nullable GpuTextureView reflections;
    private @Nullable SceneReflectionPass reflectionPass;
    private @Nullable ResourceHandle<RenderTarget> screenReflections;
    private @Nullable Wetness wetness;
    private @Nullable ResourceHandle<RenderTarget> tint;
    private @Nullable ResourceHandle<RenderTarget> refraction;
    private @Nullable ResourceHandle<RenderTarget> waves;
    private boolean underwater;
    private float underwaterSkyLight;
    private List<Footprint> footprints = List.of();
    private double footprintSeconds;

    public Optional<ResourceHandle<RenderTarget>> surface() {
        return Optional.ofNullable(surface);
    }

    public Optional<GpuTextureView> reflections() {
        return Optional.ofNullable(reflections);
    }

    public Optional<Wetness> wetness() {
        return Optional.ofNullable(wetness);
    }

    public Optional<SceneReflectionPass> reflectionPass() {
        return Optional.ofNullable(reflectionPass);
    }

    Optional<ResourceHandle<RenderTarget>> screenReflections() {
        return Optional.ofNullable(screenReflections);
    }

    void publishScreenReflections(ResourceHandle<RenderTarget> written) {
        screenReflections = written;
    }

    public void publishReflections(GpuTextureView traced) {
        reflections = traced;
    }

    Optional<ResourceHandle<RenderTarget>> tint() {
        return Optional.ofNullable(tint);
    }

    Optional<ResourceHandle<RenderTarget>> refraction() {
        return Optional.ofNullable(refraction);
    }

    Optional<ResourceHandle<RenderTarget>> waves() {
        return Optional.ofNullable(waves);
    }

    List<Footprint> footprints() {
        return footprints;
    }

    double footprintSeconds() {
        return footprintSeconds;
    }

    void publishFootprints(List<Footprint> visible, double seconds) {
        footprints = List.copyOf(visible);
        footprintSeconds = seconds;
    }

    boolean underwater() {
        return underwater;
    }

    float underwaterSkyLight() {
        return underwaterSkyLight;
    }

    void publishUnderwater(float skyLight) {
        underwater = true;
        underwaterSkyLight = skyLight;
    }

    void publishWaves(ResourceHandle<RenderTarget> simulated) {
        waves = simulated;
    }

    void publishTint(ResourceHandle<RenderTarget> written) {
        tint = written;
    }

    void publishRefraction(ResourceHandle<RenderTarget> copied) {
        refraction = copied;
    }

    void publishWetness(Wetness current) {
        wetness = current;
    }

    void publishSurface(ResourceHandle<RenderTarget> written) {
        surface = written;
    }

    void publishReflectionPass(SceneReflectionPass pass) {
        reflectionPass = pass;
    }
}
