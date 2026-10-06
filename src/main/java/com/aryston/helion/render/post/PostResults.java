package com.aryston.helion.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class PostResults {
    private @Nullable ResourceHandle<RenderTarget> bloom;
    private @Nullable GpuBuffer imageUniforms;

    Optional<ResourceHandle<RenderTarget>> bloom() {
        return Optional.ofNullable(bloom);
    }

    void publishBloom(ResourceHandle<RenderTarget> handle) {
        bloom = handle;
    }

    Optional<GpuBuffer> imageUniforms() {
        return Optional.ofNullable(imageUniforms);
    }

    void publishImageUniforms(GpuBuffer buffer) {
        imageUniforms = buffer;
    }
}
