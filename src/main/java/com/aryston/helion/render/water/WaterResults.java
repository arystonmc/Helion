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

    public Optional<ResourceHandle<RenderTarget>> surface() {
        return Optional.ofNullable(surface);
    }

    public Optional<GpuTextureView> reflections() {
        return Optional.ofNullable(reflections);
    }

    public Optional<SceneReflectionPass> reflectionPass() {
        return Optional.ofNullable(reflectionPass);
    }

    public void publishReflections(GpuTextureView traced) {
        reflections = traced;
    }

    void publishSurface(ResourceHandle<RenderTarget> written) {
        surface = written;
    }

    void publishReflectionPass(SceneReflectionPass pass) {
        reflectionPass = pass;
    }
}
