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
    private @Nullable ResourceHandle<RenderTarget> glassSurface;
    private @Nullable ResourceHandle<RenderTarget> glassTint;
    private @Nullable ResourceHandle<RenderTarget> refraction;
    private @Nullable ResourceHandle<RenderTarget> waves;
    private UnderwaterView underwater = UnderwaterView.ABOVE_WATER;
    private boolean lava;
    private List<Footprint> footprints = List.of();
    private double footprintSeconds;
    private double gameSeconds;

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

    double gameSeconds() {
        return gameSeconds;
    }

    void publishGameSeconds(double seconds) {
        gameSeconds = seconds;
    }

    UnderwaterView underwater() {
        return underwater;
    }

    boolean lavaNearby() {
        return lava;
    }

    void publishLava() {
        lava = true;
    }

    void publishUnderwater(UnderwaterView view) {
        underwater = view;
    }

    void publishWaves(ResourceHandle<RenderTarget> simulated) {
        waves = simulated;
    }

    void publishTint(ResourceHandle<RenderTarget> written) {
        tint = written;
    }

    void publishGlass(ResourceHandle<RenderTarget> writtenSurface, ResourceHandle<RenderTarget> writtenTint) {
        glassSurface = writtenSurface;
        glassTint = writtenTint;
    }

    public Optional<ResourceHandle<RenderTarget>> glassSurface() {
        return Optional.ofNullable(glassSurface);
    }

    Optional<ResourceHandle<RenderTarget>> glassTint() {
        return Optional.ofNullable(glassTint);
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
