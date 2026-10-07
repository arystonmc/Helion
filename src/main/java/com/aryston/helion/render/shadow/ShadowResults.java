package com.aryston.helion.render.shadow;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ShadowResults {
    private @Nullable Shadows shadows;

    public Optional<Shadows> shadows() {
        return Optional.ofNullable(shadows);
    }

    void publish(ResourceHandle<RenderTarget> mask, ShadowLight light) {
        shadows = new Shadows(mask, light);
    }

    public record Shadows(ResourceHandle<RenderTarget> mask, ShadowLight light) {
    }
}
