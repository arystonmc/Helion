package com.aryston.helion.render.water;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class WaterResults {
    private @Nullable ResourceHandle<RenderTarget> surface;
    private @Nullable GpuTextureView reflections;
    private @Nullable SceneReflectionPass reflectionPass;
    private @Nullable ResourceHandle<RenderTarget> screenReflections;
    private @Nullable Wetness wetness;

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
